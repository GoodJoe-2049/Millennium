package steam.nova.app.features.chat.effects

import android.os.SystemClock
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Confined to the effects worker. Rhino runs our parser, never downloaded source as a script. */
internal class SteamChatEffectCompiler(private val files:JSONObject,val mainUrl:String,css:String,cached:JSONObject?=null) {
    class MissingModule(val url:String):RuntimeException("Effect dependency required")
    private val deadline=ThreadLocal<Long>()
    private val factory=object:ContextFactory(){
        override fun makeContext():Context=super.makeContext().apply {
            languageVersion=Context.VERSION_ES6
            isInterpretedMode=true
            instructionObserverThreshold=10000
            maximumInterpreterStackDepth=512
            setClassShutter { false }
        }
        override fun observeInstructionCount(context:Context,count:Int) {
            check(!Thread.currentThread().isInterrupted&&SystemClock.elapsedRealtime()<(deadline.get()?:0L)){"Effect compiler time limit"}
        }
    }
    private lateinit var scope:Scriptable
    val catalog:JSONObject
    val limits:JSONObject
    init {
        runCatching {
            val moduleLoader=SteamChatEffectCompiler::class.java.classLoader
            val threadLoader=Thread.currentThread().contextClassLoader
            val service="META-INF/services/org.mozilla.javascript.RegExpLoader"
            SteamChatDiagnostics.effect("compiler.environment",
                "rhino=1.9.1 moduleLoader=${moduleLoader?.javaClass?.name} threadLoader=${threadLoader?.javaClass?.name} sameLoader=${moduleLoader===threadLoader} moduleRegExpService=${moduleLoader?.getResource(service)!=null} threadRegExpService=${threadLoader?.getResource(service)!=null}")
        }.onFailure {SteamChatDiagnostics.effectFailure("compiler.environment",it)}
        val result=run(45000) { context ->
            scope=SteamChatDiagnostics.effectTrace("compiler.scope"){context.initSafeStandardObjects()}
            for(name in listOf("Packages","java","javax","org","com","getClass","JavaAdapter","JavaImporter"))ScriptableObject.deleteProperty(scope,name)
            SteamChatDiagnostics.effectTrace("compiler.regexp") {
                check(Context.toBoolean(context.evaluateString(scope,"typeof RegExp === 'function' && /effect/.test('effect')","effect-regexp-check",1,null))){"Rhino RegExp service unavailable"}
            }
            SteamChatDiagnostics.effectTrace("compiler.acorn"){context.evaluateString(scope,SteamChatEffectAcorn.source,"acorn-8.15.0",1,null)}
            SteamChatDiagnostics.effectTrace("compiler.program"){context.evaluateString(scope,SteamChatEffectProgram.source(),"steam-effect-ast",1,null)}
            SteamChatDiagnostics.effectTrace("compiler.css"){context.evaluateString(scope,SteamChatEffectCss.source(),"steam-effect-css",1,null)}
            context.evaluateString(scope,"var __program=null,__sheet=null;function __result(fn){try{return JSON.stringify({value:fn()});}catch(e){if(e&&e.need)return JSON.stringify({need:e.need});throw e;}}","effect-adapter",1,null)
            val parsed=cached?.let { "JSON.parse(${JSONObject.quote(it.toString())})" }?:"null"
            SteamChatDiagnostics.effectTrace(if(cached==null)"compiler.catalog" else "compiler.restore","modules=${files.length()} cssChars=${css.length}") {
                read(context,"__result(function(){var cached=$parsed;__program=MillenniumEffectProgram.create(JSON.parse(${JSONObject.quote(files.toString())}),${JSONObject.quote(mainUrl)},cached&&cached.program);__sheet=cached?MillenniumEffectCss.restore(cached.sheet):MillenniumEffectCss.parse(${JSONObject.quote(css)});return {catalog:__program.catalog,limits:__program.limits};})")
            }
        }
        catalog=result.getJSONObject("catalog");limits=result.getJSONObject("limits")
        require(catalog.length() in 1..64)
        for(name in catalog.keys()) {
            require(name.matches(Regex("[A-Za-z0-9_-]{1,80}")))
            val effect=catalog.getJSONObject(name)
            require(effect.getLong("duration") in 1..120000)
            require(limits.optInt(effect.getString("queue")) in 1..32)
        }
        SteamChatDiagnostics.effect("compiler.ready","effects=${catalog.length()} queues=${limits.length()}")
    }
    fun scene(name:String,width:Float,height:Float,seed:Long):JSONObject {
        require(width.isFinite()&&height.isFinite()&&width in 1f..4096f&&height in 1f..8192f)
        return run(20000){context->read(context,"__result(function(){return __sheet.convert(__program.scene(${JSONObject.quote(name)},$width,$height,${seed and 0xffffffffL}));})")}
    }
    fun labels(url:String):JSONObject=run(20000){context->read(context,"__result(function(){return __program.labels(${JSONObject.quote(url)});})")}
    fun snapshot():JSONObject=run(20000){context->
        read(context,"__result(function(){return {program:__program.snapshot(),sheet:__sheet.snapshot()};})",48*1024*1024)
    }
    private fun read(context:Context,script:String,maxChars:Int=6*1024*1024):JSONObject {
        val text=Context.toString(context.evaluateString(scope,script,"effect-command",1,null))
        require(text.length<=maxChars){"Effect compiler output too large"}
        val result=JSONObject(text)
        if(result.has("need"))throw MissingModule(result.getString("need"))
        return result.getJSONObject("value")
    }
    private fun <T> run(timeout:Long,action:(Context)->T):T {
        deadline.set(SystemClock.elapsedRealtime()+timeout)
        return try{factory.call(action)}finally{deadline.remove()}
    }
}
