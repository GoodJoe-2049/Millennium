package steam.nova.app.features.chat.effects

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import steam.nova.app.features.chat.network.SteamChatAccount
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Binds a native renderer to a non-interactive RN slot, never to a window overlay. */
internal object SteamChatRoomEffects {
    private const val PREFIX="millennium.chat.effects."
    private val main=Handler(Looper.getMainLooper())
    // Rhino 1.9.1 discovers RegExp services through the context loader at first initialization.
    private val worker=ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,ArrayBlockingQueue(40)){task->
        Thread(task,"MillenniumChatEffects").apply{isDaemon=true;contextClassLoader=SteamChatEffectCompiler::class.java.classLoader}
    }
    private val layers=WeakHashMap<View,Layer>()
    private val owners=HashMap<String,Layer>()
    private var resources:SteamChatEffectResources?=null
    @Volatile private var available=false
    private var lifecycle=false
    private data class Playing(val prepared:SteamChatEffectResources.Prepared,val started:Long)
    private data class Waiting(val prepared:SteamChatEffectResources.Prepared,val requested:Long)
    private class Layer(val owner:String,val account:String,val key:String,val epoch:Long,view:View):View.OnAttachStateChangeListener {
        val view=WeakReference(view)
        val playing=ArrayList<Playing>()
        val waiting=ArrayDeque<Waiting>()
        var closed=false
        var loading=0
        override fun onViewAttachedToWindow(view:View)=Unit
        override fun onViewDetachedFromWindow(view:View){close(this)}
        fun update(now:Long){
            playing.removeAll {now-it.started>=it.prepared.scene.duration}
            val count=waiting.size
            repeat(count){val next=waiting.removeFirst();if(now-next.requested>15000)return@repeat
                if(playing.count{it.prepared.queue==next.prepared.queue}<next.prepared.limit)playing+=Playing(next.prepared,now) else waiting.addLast(next)
            }
        }
    }
    @Synchronized fun install(module:XposedModule,loader:ClassLoader,context:Context){
        if(available)return
        val type=Class.forName("com.facebook.react.views.view.ReactViewGroup",false,loader)
        val draw=type.getDeclaredMethod("dispatchDraw",Canvas::class.java)
        module.hook(draw).setId("millennium.chat.effects.draw").intercept {chain->
            val result=chain.proceed()
            val view=chain.thisObject as? View?:return@intercept result
            val layer=layers[view]?:return@intercept result
            val canvas=chain.args.firstOrNull() as? Canvas?:return@intercept result
            if(layer.closed||!view.isShown||view.width<=0||view.height<=0)return@intercept result
            val now=SystemClock.uptimeMillis();layer.update(now)
            if(layer.playing.isNotEmpty()){
                val saved=canvas.save()
                try{
                    canvas.clipRect(0,0,view.width,view.height)
                    val density=view.resources.displayMetrics.density;canvas.scale(density,density)
                    layer.playing.forEach {it.prepared.scene.draw(canvas,now-it.started,view.width/density,view.height/density)}
                }catch(error:Throwable){layer.playing.clear();layer.waiting.clear();SteamChatDiagnostics.effectFailure("draw",error,publicResource=true)}
                finally{canvas.restoreToCount(saved)}
                if(layer.playing.isNotEmpty())view.postInvalidateOnAnimation()
            }
            result
        }
        val application=context.applicationContext as? Application
        if(!lifecycle&&application!=null){
            lifecycle=true
            application.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks {
                override fun onActivityPaused(activity:Activity){clearActivity(activity)}
                override fun onActivityDestroyed(activity:Activity){clearActivity(activity)}
                override fun onActivityResumed(activity:Activity)=Unit
                override fun onActivityCreated(activity:Activity,savedInstanceState:Bundle?)=Unit
                override fun onActivityStarted(activity:Activity)=Unit
                override fun onActivityStopped(activity:Activity)=Unit
                override fun onActivitySaveInstanceState(activity:Activity,outState:Bundle)=Unit
            })
        }
        available=true
        SteamChatDiagnostics.effect("install.ready","lifecycle=$lifecycle hostLoader=${loader.javaClass.name}")
    }
    private fun clearActivity(activity:Activity){owners.values.toList().filter{it.view.get()?.rootView===activity.window.decorView.rootView}.forEach(::close)}
    private fun close(layer:Layer){
        layer.closed=true;layer.playing.clear();layer.waiting.clear()
        layer.view.get()?.let{view->if(layers[view]===layer)layers.remove(view);view.removeOnAttachStateChangeListener(layer);view.invalidate()}
        if(owners[layer.owner]===layer)owners.remove(layer.owner)
    }
    fun clear(){main.post{owners.values.toList().forEach(::close)}}
    fun clearOtherAccounts(account:String){main.post{owners.values.toList().filter{it.account!=account}.forEach(::close)}}
    fun handle(context:Context,activity:Activity?,args:JSONObject,isCurrent:()->Boolean,complete:(Result<JSONObject>)->Unit){
        val operation=args.getString("operation");val account=args.getString("account");val epoch=args.getLong("epoch")
        val started=SystemClock.elapsedRealtime()
        val diagnostic="request=${System.nanoTime().toString(36)} operation=${SteamChatDiagnostics.operation(operation)} epoch=$epoch"
        SteamChatDiagnostics.effect("request.begin","$diagnostic available=$available activity=${activity!=null} force=${args.optBoolean("force")} queue=${worker.queue.size}")
        fun finish(result:Result<JSONObject>){
            val detail="$diagnostic elapsedMs=${SystemClock.elapsedRealtime()-started}"
            result.fold({value->SteamChatDiagnostics.effect("request.end","$detail effects=${value.optJSONObject("catalog")?.length()?:0} accepted=${value.optBoolean("accepted")}")},
                {error->SteamChatDiagnostics.effectFailure("request",error,detail)})
            complete(result)
        }
        if(!available){finish(Result.failure(IllegalStateException("Native effect renderer unavailable")));return}
        if(operation=="roomEffectsCatalog"){
            try{worker.execute{
                SteamChatDiagnostics.effect("request.dequeue","$diagnostic waitMs=${SystemClock.elapsedRealtime()-started}")
                val result=runCatching{
                    SteamChatAccount.current(account)
                    check(isCurrent()){"Stale effect request"}
                    val repository=resources?:SteamChatDiagnostics.effectTrace("resources.create"){SteamChatEffectResources(context.applicationContext)}.also{resources=it}
                    repository.ensure(args.optBoolean("force"));SteamChatAccount.current(account);check(isCurrent()){"Stale effect request"}
                    repository.metadata(args.optString("language","english"))
                };finish(result)
            }}catch(error:Exception){finish(Result.failure(error))};return
        }
        val owner=args.getString("owner")
        require(owner.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        main.post dispatch@{
            SteamChatDiagnostics.effect("request.main","$diagnostic waitMs=${SystemClock.elapsedRealtime()-started}")
            runCatching{
                SteamChatAccount.current(account)
                check(isCurrent()){"Stale effect request"}
                if(operation=="roomEffectsDetach"){
                    owners[owner]?.takeIf{it.account==account&&it.epoch<=epoch}?.let(::close)
                    finish(Result.success(JSONObject()));return@dispatch
                }
                if(operation=="roomEffectsAttach"){
                    val host=requireNotNull(activity)
                    val candidate:View?=host.window.decorView.findViewWithTag(PREFIX+owner)
                    SteamChatDiagnostics.effect("attach.view","$diagnostic found=${candidate!=null} attached=${candidate?.isAttachedToWindow} shown=${candidate?.isShown} focus=${candidate?.hasWindowFocus()} width=${candidate?.width} height=${candidate?.height} finishing=${host.isFinishing} destroyed=${host.isDestroyed}")
                    val view=requireNotNull(candidate){"Effect layer not mounted"}
                    require(view.isAttachedToWindow&&!host.isFinishing&&!host.isDestroyed)
                    val key=args.getString("key");require(key.matches(Regex("p:7656119\\d{10}|g:[0-9]+:[0-9]+")))
                    owners[owner]?.let(::close)
                    val layer=Layer(owner,account,key,epoch,view);owners[owner]=layer;layers[view]=layer;view.addOnAttachStateChangeListener(layer)
                    finish(Result.success(JSONObject()));return@dispatch
                }
                require(operation=="roomEffectsPlay")
                val layer=requireNotNull(owners[owner]);val view=requireNotNull(layer.view.get())
                require(layer.account==account&&layer.epoch<=epoch&&layer.key==args.getString("key")&&!layer.closed&&view.isShown&&view.hasWindowFocus())
                require(layer.loading+layer.waiting.size+layer.playing.size<48){"Effect queue full"}
                val density=view.resources.displayMetrics.density;val width=view.width/density;val height=view.height/density
                val name=args.getString("name");require(name.matches(Regex("[A-Za-z0-9_-]{1,80}")))
                val requested=SystemClock.uptimeMillis();layer.loading++
                try{worker.execute{
                    SteamChatDiagnostics.effect("request.dequeue","$diagnostic waitMs=${SystemClock.elapsedRealtime()-started}")
                    val result=runCatching{SteamChatAccount.current(account);check(isCurrent());requireNotNull(resources).prepare(name,width,height)}
                    main.post deliver@{
                        layer.loading--
                        val current=isCurrent();val age=SystemClock.uptimeMillis()-requested;val focused=layer.view.get()?.hasWindowFocus()==true
                        if(!current||layer.closed||owners[owner]!==layer||!focused||age>15000){
                            SteamChatDiagnostics.effect("play.discard","$diagnostic current=$current closed=${layer.closed} ownerCurrent=${owners[owner]===layer} focus=$focused ageMs=$age")
                            finish(Result.success(JSONObject().put("accepted",false)));return@deliver
                        }
                        result.fold({prepared->
                            if(args.optBoolean("replay"))layer.playing+=Playing(prepared,SystemClock.uptimeMillis())
                            else{layer.waiting.addLast(Waiting(prepared,requested));layer.update(SystemClock.uptimeMillis())}
                            layer.view.get()?.postInvalidateOnAnimation();finish(Result.success(JSONObject().put("accepted",true)))
                        },{finish(Result.failure(it))})
                    }
                }}catch(error:Exception){layer.loading--;throw error}
            }.onFailure{finish(Result.failure(it))}
        }
    }
}
