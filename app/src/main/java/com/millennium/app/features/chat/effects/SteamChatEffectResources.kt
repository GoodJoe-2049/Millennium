package com.millennium.app.features.chat.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.LruCache
import androidx.core.net.toUri
import com.millennium.app.features.chat.diagnostics.SteamChatDiagnostics
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Public resources only. A complete verified bundle replaces the previous snapshot atomically. */
internal class SteamChatEffectResources(context:Context) {
    private val directory=File(context.filesDir,"millennium-chat-effects").apply{mkdirs()}
    private val legacyDirectory=File(context.cacheDir,"millennium-chat-effects-v1")
    private val snapshot=AtomicFile(File(directory,"verified.json.gz"))
    private val parsedCache=SteamChatEffectCache(directory)
    private val http=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
        .callTimeout(30,TimeUnit.SECONDS).followRedirects(false).cache(Cache(File(context.cacheDir,"millennium-chat-effects-http"),32L*1024*1024)).build()
    private val bitmaps=object:LruCache<String,Bitmap>(48*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    @Volatile private var active:Bundle?=null
    @Volatile private var checkedAt=0L
    private val updating=AtomicBoolean(false)
    // Background validation must see the module's Rhino service descriptors as well.
    private val updates=Executors.newSingleThreadExecutor { task->
        Thread(task,"MillenniumEffectUpdates").apply{isDaemon=true;contextClassLoader=SteamChatEffectCompiler::class.java.classLoader}
    }
    private data class Bundle(val data:JSONObject,val compiler:SteamChatEffectCompiler,val assets:List<String>) {
        val revision:String get()=data.getString("revision")
        val assetRevision:String get()=data.optString("assetRevision",revision)
    }
    data class Prepared(val scene:SteamChatEffectScene,val queue:String,val limit:Int)
    private fun allowed(url:String,script:Boolean):Boolean {
        val uri=url.toUri()
        if(uri.scheme!="https"||uri.userInfo!=null||uri.port !in listOf(-1,443)||uri.fragment!=null)return false
        return if(script)uri.host=="community.akamai.steamstatic.com"&&uri.path.orEmpty().matches(Regex("/public/(javascript/webui|css/webui)/[A-Za-z0-9_.-]+\\.(js|css)"))
        else uri.host in setOf("cdn.akamai.steamstatic.com","community.akamai.steamstatic.com")&&uri.path.orEmpty().startsWith("/steamcommunity/public/assets/")&&uri.path.orEmpty().contains("/roomeffects/")&&!uri.path.orEmpty().contains("..")
    }
    private fun readLimited(input:InputStream,max:Int):ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val count=input.read(buffer);if(count<0)break;require(out.size()+count<=max){"Effect resource too large"};out.write(buffer,0,count)}
        return out.toByteArray()
    }
    private fun resourceName(url:String)=url.substringBefore('?').substringAfterLast('/').takeIf{it.matches(Regex("[A-Za-z0-9_.-]{1,120}"))}?:"unknown"
    private fun get(url:String,script:Boolean,max:Int):ByteArray=SteamChatDiagnostics.effectTrace("resource.fetch","file=${resourceName(url)} limit=$max") {
        require(allowed(url,script)){"Unrecognized effect resource URL"}
        val request=Request.Builder().url(url).cacheControl(CacheControl.Builder().maxAge(0,TimeUnit.SECONDS).build()).build()
        http.newCall(request).execute().use {response->
            val body=response.body
            SteamChatDiagnostics.effect("resource.response","file=${resourceName(url)} status=${response.code} declaredBytes=${body.contentLength()} fromCache=${response.cacheResponse!=null}")
            check(response.isSuccessful){"Steam effect resource HTTP ${response.code}"}
            require(body.contentLength()<=max){"Effect resource Content-Length exceeds limit"}
            body.byteStream().use {readLimited(it,max)}.also {SteamChatDiagnostics.effect("resource.bytes","file=${resourceName(url)} bytes=${it.size}")}
        }
    }
    private fun script(url:String)=get(url,true,5*1024*1024).toString(Charsets.UTF_8)
    private fun hash(text:String)=SteamChatEffectCache.hash(text)
    private fun restore(data:JSONObject,parsed:JSONObject):Bundle {
        val modules=parsed.getJSONObject("program").getJSONObject("modules")
        require(modules.length() in 1..12)
        for(url in modules.keys())require(data.getJSONObject("files").has(url)){"Cached effect module missing"}
        val compiler=SteamChatDiagnostics.effectTrace("parsed.restore") {
            SteamChatEffectCompiler(data.getJSONObject("files"),data.getString("main"),data.getString("css"),parsed)
        }
        val labels=parsed.getJSONObject("labels")
        for(language in listOf("english","schinese")) {
            val strings=labels.getJSONObject(language)
            for(name in compiler.catalog.keys()) {
                val entry=strings.getJSONObject(name)
                entry.getString("title");entry.getString("message")
                val icon=compiler.catalog.getJSONObject(name).optString("icon")
                require(icon.isEmpty()||allowed(icon,false))
            }
        }
        val list=parsed.getJSONArray("assets");require(list.length()<=4096)
        val assets=(0 until list.length()).map {list.getString(it).also {url->require(allowed(url,false))}}
        data.put("labels",labels)
        SteamChatDiagnostics.effect("parsed.hit","revision=${data.getString("revision").take(12)} effects=${compiler.catalog.length()}")
        return Bundle(data,compiler,assets)
    }
    private fun compile(data:JSONObject,network:Boolean):Bundle {
        val files=data.getJSONObject("files");val main=data.getString("main");val css=data.getString("css")
        for(language in listOf("english","schinese")) {
            val url=BASE+"javascript/webui/friendsui_$language.js"
            if(!files.has(url)){check(network){"Cached localization missing"};files.put(url,script(url))}
        }
        data.put("revision",SteamChatEffectCache.revision(data))
        parsedCache.read(data.getString("revision"))?.let {parsed->
            runCatching {restore(data,parsed)}
                .onFailure {SteamChatDiagnostics.effectFailure("parsed.restore",it,publicResource=true)}
                .getOrNull()?.let {return it}
            SteamChatDiagnostics.effect("parsed.rebuild","reason=restore")
        }
        repeat(12){attempt->
            try {
                // Dependencies discovered in a previous attempt are part of the content fingerprint.
                data.put("revision",SteamChatEffectCache.revision(data))
                val compiler=SteamChatDiagnostics.effectTrace("compile.adapter","attempt=${attempt+1} modules=${files.length()} network=$network") {
                    SteamChatEffectCompiler(files,main,css)
                }
                val labels=JSONObject()
                for(language in listOf("english","schinese")) {
                    val url=BASE+"javascript/webui/friendsui_$language.js"
                    labels.put(language,SteamChatDiagnostics.effectTrace("compile.labels","language=$language"){compiler.labels(url)})
                }
                data.put("labels",labels)
                val assets=linkedSetOf<String>()
                for(name in compiler.catalog.keys()) {
                    for(sample in 0..2) {
                        val detail="name=${SteamChatDiagnostics.operation(name)} sample=$sample"
                        val scene=SteamChatDiagnostics.effectTrace("compile.scene",detail){compiler.scene(name,if(sample==1)800f else 430f,if(sample==1)430f else 700f,0x6d43L+sample*7919L)}
                        SteamChatDiagnostics.effectTrace("compile.validate",detail){SteamChatEffectScene(scene,emptyMap(),true)}
                        val list=scene.getJSONArray("assets")
                        for(i in 0 until list.length()) {
                            val url=list.getString(i);require(allowed(url,false)){"Unrecognized effect scene asset"}
                            assets.add(url)
                        }
                    }
                    val icon=compiler.catalog.getJSONObject(name).optString("icon")
                    require(icon.isEmpty()||allowed(icon,false)){"Unrecognized effect icon"}
                    if(icon.isNotEmpty())assets.add(icon)
                }
                val bundle=Bundle(data,compiler,assets.toList())
                // Cache pure parse data after validating the generated scenes, never the scenes themselves.
                runCatching {
                    val parsed=SteamChatDiagnostics.effectTrace("parsed.export"){compiler.snapshot()}
                        .put("labels",labels).put("assets",JSONArray(bundle.assets))
                    parsedCache.write(bundle.revision,parsed)
                }.onFailure {SteamChatDiagnostics.effectFailure("parsed.save",it,publicResource=true)}
                SteamChatDiagnostics.effect("compile.ready","effects=${compiler.catalog.length()} modules=${files.length()}")
                return bundle
            }catch(missing:SteamChatEffectCompiler.MissingModule){
                SteamChatDiagnostics.effect("compile.dependency","file=${resourceName(missing.url)} cached=${files.has(missing.url)} network=$network")
                check(network&&!files.has(missing.url)){"Unsupported or missing cached effect dependency"}
                require(files.length()<12)
                files.put(missing.url,script(missing.url))
                require(files.toString().length<=20*1024*1024)
            }
        }
        error("Steam effect dependency limit")
    }
    private fun fromDisk():Bundle?=runCatching {
        SteamChatDiagnostics.effect("cache.read.begin","persistent=true")
        var legacy=false
        val data=try {
            SteamChatEffectCache.readJson(snapshot,24*1024*1024)
        }catch(_:FileNotFoundException) {
            legacy=true
            SteamChatEffectCache.readJson(AtomicFile(File(legacyDirectory,"verified.json.gz")),24*1024*1024)
        }
        val schema=data.getInt("schema");require(schema in 2..3){"Effect cache schema mismatch"}
        require(allowed(data.getString("main"),true))
        val files=data.getJSONObject("files");require(files.length() in 1..12)
        for(url in files.keys())require(allowed(url,true))
        val revision=SteamChatEffectCache.revision(data)
        if(schema==2) {
            // Old PNG filenames used the main JS/CSS hash rather than the complete dependency hash.
            data.put("assetRevision",data.getString("revision"))
        }else require(data.getString("revision")==revision){"Effect source checksum mismatch"}
        require(data.optString("assetRevision",revision).matches(Regex("[a-f0-9]{64}")))
        data.put("revision",revision)
        compile(data,false).also {bundle->
            bundle.assets.forEach {localAsset(it,bundle.assetRevision)}
            if(legacy||schema==2) {
                save(bundle)
                SteamChatDiagnostics.effect("cache.migrated","assets=${bundle.assets.size} persistent=true")
            }
            checkedAt=data.optLong("checkedAt").coerceIn(0L,System.currentTimeMillis())
            SteamChatDiagnostics.effect("cache.read.end","revision=${bundle.revision.take(12)} persistent=true")
        }
    }.onFailure {
        if(it is FileNotFoundException)SteamChatDiagnostics.effect("cache.miss")
        else SteamChatDiagnostics.effectFailure("cache.read",it,publicResource=true)
    }.getOrNull()
    private fun save(bundle:Bundle) {
        bundle.data.put("schema",3)
        SteamChatEffectCache.writeJson(snapshot,bundle.data,24*1024*1024)
        // Keep the active revision until its validated replacement has been committed.
        runCatching {parsedCache.prune(setOfNotNull(active?.revision,bundle.revision))}
            .onFailure {SteamChatDiagnostics.effectFailure("parsed.prune",it,publicResource=true)}
    }
    fun ensure(force:Boolean=false):JSONObject=SteamChatDiagnostics.effectTrace("ensure","force=$force memory=${active!=null}") {
        if(active==null)active=fromDisk()
        val now=System.currentTimeMillis()
        if(active==null)update()
        else if(force||now-checkedAt>24*60*60*1000L) {
            // A previously verified bundle can play while a separate worker validates its replacement.
            if(updating.compareAndSet(false,true)) {
                SteamChatDiagnostics.effect("update.queued","force=$force")
                updates.execute {try{update()}finally{updating.set(false)}}
            }else SteamChatDiagnostics.effect("update.pending")
        }
        metadata("english")
    }
    private fun update() {
        val now=System.currentTimeMillis()
        checkedAt=now
        runCatching {
            val previous=active
            SteamChatDiagnostics.effect("update.begin","hasFallback=${previous!=null}")
            val entry=script(BASE+"javascript/webui/friends.js")
            val ref=Regex("\\./friendsui_desktopapp\\.js(?:\\?[^\"'\\s]+)?").find(entry)?.value?:error("Steam chat entry changed")
            val main=BASE+"javascript/webui/"+ref.removePrefix("./")
            val js=script(main);val css=script(BASE+"css/webui/friendsui_desktopapp.css")
            val files=JSONObject().put(main,js)
            val oldFiles=previous?.data?.getJSONObject("files")
            // Revalidate all known dependencies when the entry module is unchanged.
            // A changed entry discovers its own dependencies instead of requesting obsolete URLs.
            if(previous?.data?.getString("main")==main&&oldFiles?.optString(main)==js) {
                for(url in oldFiles.keys())if(url!=main)files.put(url,script(url))
            }
            for(language in listOf("english","schinese")) {
                val url=BASE+"javascript/webui/friendsui_$language.js"
                if(!files.has(url))files.put(url,script(url))
            }
            val data=JSONObject().put("schema",3).put("main",main).put("files",files).put("css",css).put("checkedAt",now)
            val revision=SteamChatEffectCache.revision(data);data.put("revision",revision)
            SteamChatDiagnostics.effect("update.revision","revision=${revision.take(12)} changed=${previous?.revision!=revision}")
            val candidate=if(previous?.revision==revision) {
                Bundle(JSONObject(previous.data.toString()).put("checkedAt",now),previous.compiler,previous.assets)
            }else compile(data,true)
            // Cache hits still need to ensure textures are present before publishing a new bundle.
            candidate.assets.forEach {assetFile(it,candidate.assetRevision)}
            SteamChatDiagnostics.effectTrace("cache.save"){save(candidate)}
            active=candidate
            trimAssets()
            SteamChatDiagnostics.effect("update.end")
        }.getOrElse {error->
            SteamChatDiagnostics.effectFailure("update",error,"hasFallback=${active!=null}",publicResource=true)
            if(active==null)throw error
            checkedAt=now-23*60*60*1000L
            SteamChatDiagnostics.effect("update.fallback")
        }
    }
    fun metadata(language:String):JSONObject {
        val bundle=requireNotNull(active);val locale=bundle.data.getJSONObject("labels").getJSONObject(if(language=="schinese")language else "english")
        val catalog=JSONObject()
        for(name in bundle.compiler.catalog.keys()){
            val item=JSONObject(bundle.compiler.catalog.getJSONObject(name).toString());val strings=locale.optJSONObject(name)
            item.put("title",strings?.optString("title").orEmpty().ifBlank{name});item.put("message",strings?.optString("message").orEmpty());catalog.put(name,item)
        }
        return JSONObject().put("catalog",catalog).put("revision",bundle.revision)
    }
    fun prepare(name:String,width:Float,height:Float):Prepared=SteamChatDiagnostics.effectTrace("prepare","name=${SteamChatDiagnostics.operation(name)} width=$width height=$height") {
        if(active==null)ensure()
        val bundle=requireNotNull(active)
        val descriptor=bundle.compiler.catalog.optJSONObject(name)?:error("Unsupported effect")
        val json=bundle.compiler.scene(name,width,height,System.nanoTime())
        val assets=json.getJSONArray("assets");val images=HashMap<String,Bitmap>()
        for(i in 0 until assets.length()){
            val url=assets.getString(i);val key=hash(bundle.assetRevision+"\n"+url)
            images[url]=bitmaps.get(key)?:run {
                val file=assetFile(url,bundle.assetRevision)
                fun decode()=BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply{inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888})
                val bitmap=decode()?:run {file.delete();assetFile(url,bundle.assetRevision);requireNotNull(decode()){"Invalid effect bitmap"}}
                bitmaps.put(key,bitmap);bitmap
            }
        }
        Prepared(SteamChatEffectScene(json,images),descriptor.getString("queue"),bundle.compiler.limits.getInt(descriptor.getString("queue")))
    }
    private fun validAsset(file:File):Boolean {
        if(!file.isFile||file.length() !in 1..12L*1024*1024)return false
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true;inScaled=false}
        BitmapFactory.decodeFile(file.absolutePath,bounds)
        return validBounds(bounds)
    }
    private fun localAsset(url:String,revision:String):File? {
        require(allowed(url,false))
        val name=hash(revision+"\n"+url)+".png"
        val file=File(directory,name)
        if(validAsset(file)){file.setLastModified(System.currentTimeMillis());return file}
        // Copy only verified legacy textures, without moving/deleting the old cache.
        val legacy=File(legacyDirectory,name)
        if(!validAsset(legacy))return null
        val atomic=AtomicFile(file);val output=atomic.startWrite()
        try {
            legacy.inputStream().use {input->output.write(readLimited(input,12*1024*1024))}
            atomic.finishWrite(output)
        }catch(error:Throwable){atomic.failWrite(output);throw error}
        require(validAsset(file)){"Invalid migrated effect image"}
        SteamChatDiagnostics.effect("asset.migrated","file=${resourceName(url)}")
        return file
    }
    private fun assetFile(url:String,revision:String):File {
        require(allowed(url,false))
        localAsset(url,revision)?.let {return it}
        val file=File(directory,hash(revision+"\n"+url)+".png")
        file.delete()
        val bytes=get(url,false,12*1024*1024)
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true;inScaled=false}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        SteamChatDiagnostics.effect("asset.decode","file=${resourceName(url)} width=${options.outWidth} height=${options.outHeight} png=${options.outMimeType=="image/png"}")
        require(validBounds(options)){"Invalid effect image dimensions"}
        requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=16;inScaled=false})){"Damaged effect image"}.recycle()
        val atomic=AtomicFile(file);val stream=atomic.startWrite()
        try{stream.write(bytes);atomic.finishWrite(stream)}catch(error:Throwable){atomic.failWrite(stream);throw error}
        return file
    }
    private fun validBounds(options:BitmapFactory.Options)=options.outMimeType=="image/png"&&options.outWidth in 1..8192&&options.outHeight in 1..8192&&options.outWidth.toLong()*options.outHeight<=24L*1024*1024
    @Synchronized private fun trimAssets() {
        val files=directory.listFiles {file->file.name.matches(Regex("[a-f0-9]{64}\\.png"))}?.sortedByDescending{it.lastModified()}.orEmpty()
        var total=0L
        for(file in files){total+=file.length();if(total>192L*1024*1024&&System.currentTimeMillis()-file.lastModified()>60*60*1000L)file.delete()}
    }
    companion object {private const val BASE="https://community.akamai.steamstatic.com/public/"}
}
