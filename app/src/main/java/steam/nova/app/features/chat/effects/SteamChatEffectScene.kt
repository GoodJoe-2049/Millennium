package steam.nova.app.features.chat.effects

import android.graphics.*
import androidx.core.graphics.PathParser
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

private fun JSONObject.entries(): Map<String, Any> = keys().asSequence().associateWith { get(it) }.filterValues { it != JSONObject.NULL }
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

internal class EffectGeometry(var time: Float, var viewportWidth: Float, var viewportHeight: Float)

private class EffectDatum(val raw: Any) {
    val number by lazy { EffectValues.number(raw) }
    val transform by lazy { EffectTransform.parse(raw) }
    val components by lazy { EffectValues.split(text,' ').map {EffectValues.number(it)} }
    val color by lazy { EffectValues.color(text) }
    val text get() = raw.toString()
}

private class EffectStyle(node: JSONObject, frames: JSONObject) {
    private data class Point(val offset:Float,val value:EffectDatum?,val ease:((Float)->Float)?=null)
    private class Animation(json:JSONObject,frameList:JSONArray) {
        val duration=json.getDouble("duration").toFloat()
        val delay=json.optDouble("delay",0.0).toFloat()
        val iterations=json.optDouble("iterations",1.0).toFloat()
        val fill:String=json.optString("fill","none")
        val direction:String=json.optString("direction","normal")
        val ease=EffectValues.easing(json.optString("timing","ease"))
        val tracks:Map<String,List<Point>>
        var progress=0f;var active=false
        init {
            require(duration.isFinite()&&duration>0&&iterations!=0f)
            val frameObjects=frameList.objects()
            val properties=frameObjects.flatMap { it.getJSONObject("style").keys().asSequence().toList() }.toSet()-"animation-timing-function"
            tracks=properties.associateWith { key ->
                val points=frameObjects.filter { it.getJSONObject("style").has(key) }.map { f ->
                    val style=f.getJSONObject("style")
                    Point(f.getDouble("offset").toFloat(),EffectDatum(style.get(key)),style.optString("animation-timing-function").takeIf(String::isNotBlank)?.let(EffectValues::easing))
                }.sortedBy {it.offset}.toMutableList()
                if(points.first().offset>0)points.add(0,Point(0f,null))
                if(points.last().offset<1)points.add(Point(1f,null))
                points
            }
        }
        fun update(time:Float) {
            val elapsed=time-delay;active=true
            val iteration:Float
            if(elapsed<0){if(fill!="backwards"&&fill!="both"){active=false;return};progress=0f;iteration=0f}
            else if(iterations>=0&&elapsed>=duration*iterations){if(fill!="forwards"&&fill!="both"){active=false;return};val fraction=iterations%1;progress=if(fraction==0f)1f else fraction;iteration=ceil(iterations)-1}
            else{val position=elapsed/duration;iteration=floor(position);progress=position-iteration}
            if(direction=="reverse"||direction=="alternate"&&iteration.toInt()%2==1||direction=="alternate-reverse"&&iteration.toInt()%2==0)progress=1-progress
        }
    }
    private val base=node.getJSONObject("style").entries().mapValues { EffectDatum(it.value) }
    private val animations=node.getJSONArray("animations").objects().map { Animation(it,frames.getJSONArray(it.getString("name"))) }
    private var lastTime=Float.NaN
    private var first:EffectDatum?=null;private var second:EffectDatum?=null;private var ratio=0f
    fun has(key:String)=base.containsKey(key)||animations.any { it.tracks.containsKey(key) }
    fun animated(key:String)=animations.any {it.tracks.containsKey(key)}
    private fun select(key:String,time:Float) {
        if(lastTime!=time){animations.forEach {it.update(time)};lastTime=time}
        first=base[key];second=first;ratio=0f
        animations.forEach { a ->
            val points=a.tracks[key]
            if(a.active&&points!=null){
                var index=0;while(index<points.lastIndex&&points[index+1].offset<=a.progress)index++
                val left=points[index];val right=points.getOrElse(index+1){left}
                first=left.value?:base[key];second=right.value?:base[key]
                val interval=right.offset-left.offset
                ratio=if(interval<=0)0f else (left.ease?:a.ease)(((a.progress-left.offset)/interval).coerceIn(0f,1f))
            }
        }
    }
    fun number(key:String,g:EffectGeometry,basis:Float,default:Float=0f):Float {
        select(key,g.time)
        fun value(d:EffectDatum?)=if(d==null||d.text=="auto"||d.text=="none")default else d.number.at(g.time,basis,g.viewportWidth,g.viewportHeight)
        val a=value(first);val result=a+(value(second)-a)*ratio
        require(result.isFinite()) {"Nonfinite effect geometry"};return result
    }
    fun component(key:String,index:Int,g:EffectGeometry,basis:Float,default:Float):Float {
        select(key,g.time)
        fun value(d:EffectDatum?):Float {
            val parts=d?.components?:return default
            val item=if(key=="margin")parts.getOrElse(index){parts[if(index==3&&parts.size>=2)1 else 0]}else parts.getOrNull(index)?:return default
            return item.at(g.time,basis,g.viewportWidth,g.viewportHeight)
        }
        val a=value(first);return a+(value(second)-a)*ratio
    }
    fun color(key:String,time:Float,default:Int):Int {
        select(key,time)
        val d=if(ratio<.5f)first else second
        return if(d==null||d.text=="inherit"||d.text=="currentColor")default else d.color
    }
    fun text(key:String,time:Float,default:String=""):String {
        select(key,time)
        val a=first?.text?:default;val b=second?.text?:default
        if(key=="visibility"&&(a=="visible"||b=="visible")&&ratio>0&&ratio<1)return "visible"
        return if(ratio<.5f)a else b
    }
    fun transform(g:EffectGeometry,w:Float,h:Float,matrix:FloatArray,other:FloatArray):EffectTransform {
        select("transform",g.time);val a=first?.transform?:EffectTransform.identity;val b=second?.transform?:EffectTransform.identity
        if(ratio==0f||a===b)a.matrix(g.time,w,h,g.viewportWidth,g.viewportHeight,matrix)
        else a.blend(b,ratio,g.time,w,h,g.viewportWidth,g.viewportHeight,matrix,other)
        return a
    }
    fun validate() {
        require(listOf("position","display","filter","fill","background-color").none(::animated)){"Unsupported discrete effect animation"}
        val values=base.toMutableMap().mapValues { listOf(it.value) }.toMutableMap()
        animations.forEach { a -> a.tracks.forEach { (k,v)->values[k]=values[k].orEmpty()+v.mapNotNull {it.value} } }
        values.forEach { (k,v)->v.forEach { d -> when(k) {
            "transform"->{val matrix=FloatArray(16);for(time in listOf(0f,120000f)){d.transform.matrix(time,430f,700f,430f,700f,matrix);require(matrix.all(Float::isFinite)){"Nonfinite effect transform"}}}
            "filter"->EffectValues.filter(d.text)
            "margin","transform-origin"->{val parts=d.components;require(parts.size in 1..(if(k=="margin")4 else 2));parts.forEach{require(it.at(0f,430f,430f,700f).isFinite())}}
            "background-color","fill","border-color"->if(d.text!="inherit"&&d.text!="currentColor")EffectValues.color(d.text)
            "opacity","left","right","top","bottom","width","height","min-width","max-width","min-height","max-height","margin-left","margin-right","margin-top","margin-bottom","z-index","background-position-x","background-position-y","border-width"->if(d.text!="auto"&&d.text!="none"){val n=d.number;require(n.at(0f,430f,430f,700f).isFinite()&&n.at(120000f,700f,430f,700f).isFinite())}
        } } }
    }
}

/** A retained Canvas scene. No JS, network requests, or bitmap decoding runs from draw(). */
internal class SteamChatEffectScene(json:JSONObject,private val images:Map<String,Bitmap>,validationOnly:Boolean=false) {
    val duration=json.getLong("duration")
    private val nodes=json.getJSONArray("nodes").objects().mapIndexed { index,node->Node(node,json.getJSONObject("frames"),images,validationOnly,index) }
    private val drawOrder=nodes.sortedWith(Node.order).toMutableList()
    private val animatedZ=nodes.any{it.animatedZ}
    private val geometry=EffectGeometry(0f,0f,0f)
    init {require(duration in 1..120000)}
    fun draw(canvas:Canvas,elapsed:Long,width:Float,height:Float){
        if(elapsed !in 0 until duration)return
        geometry.time=elapsed.toFloat();geometry.viewportWidth=width;geometry.viewportHeight=height
        nodes.forEach {it.measure(width,height,geometry)}
        if(animatedZ)drawOrder.sortWith(Node.order)
        drawOrder.forEach {it.draw(canvas,width,height,geometry,0f,0f,Color.BLACK,null)}
    }
    private class Node(json:JSONObject,frames:JSONObject,images:Map<String,Bitmap>,validationOnly:Boolean,val index:Int) {
        val tag:String=json.getString("tag");val props:JSONObject=json.getJSONObject("props")
        private val delay=json.optLong("delay").toFloat()
        private val style=EffectStyle(json,frames)
        private val children=json.getJSONArray("children").objects().mapIndexed {index,node->Node(node,frames,images,validationOnly,index)}
        private val drawChildren=children.sortedWith(order).toMutableList()
        private val childrenAnimatedZ=children.any{it.animatedZ}
        private val image=props.optString("src",json.optString("image")).takeIf(String::isNotEmpty)?.let {val bitmap=images[it];require(validationOnly||bitmap!=null){"Missing effect image"};bitmap}
        private val background=json.has("image")
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val source=Rect();private val destination=RectF();private val matrix=Matrix()
        private val transform=FloatArray(16);private val other=FloatArray(16)
        private val vector=path()
        private val svgTransform=props.optString("transform").takeIf(String::isNotEmpty)?.let(::svgMatrix)
        private val viewBox=props.optString("viewBox").takeIf(String::isNotEmpty)?.trim()?.split(Regex("[ ,]+"))?.map(String::toFloat)
        private val ownFilter=EffectValues.filter(style.text("filter",0f))
        private var parentFilter:ColorMatrix?=null;private var combinedFilter:ColorMatrix?=ownFilter
        private var colorFilter:ColorMatrixColorFilter?=ownFilter?.let(::ColorMatrixColorFilter)
        private val localGeometry=EffectGeometry(0f,0f,0f)
        var width=0f;private var height=0f;private var marginLeft=0f;private var marginTop=0f;private var marginRight=0f;private var marginBottom=0f
        private val absolute=style.text("position",0f)=="absolute"
        private val inline=tag=="img"||tag=="svg"||style.text("display",0f)=="inline-block"
        private val flow=children.filterNot {it.absolute}
        private var flowX=0f;private var flowY=0f
        var z=style.number("z-index",localGeometry,1f)
        val animatedZ=style.animated("z-index")
        init {style.validate();require(tag in setOf("div","img","svg","path","rect","ellipse","circle","g","polygon"));viewBox?.let{require(it.size==4&&it[2]>0&&it[3]>0)}}
        private fun local(g:EffectGeometry):EffectGeometry {localGeometry.time=g.time-delay;localGeometry.viewportWidth=g.viewportWidth;localGeometry.viewportHeight=g.viewportHeight;return localGeometry}
        private fun box(g:EffectGeometry,pw:Float){
            marginTop=style.number("margin-top",g,pw,style.component("margin",0,g,pw,0f));marginRight=style.number("margin-right",g,pw,style.component("margin",1,g,pw,0f))
            marginBottom=style.number("margin-bottom",g,pw,style.component("margin",2,g,pw,0f));marginLeft=style.number("margin-left",g,pw,style.component("margin",3,g,pw,0f))
        }
        fun measure(parentWidth:Float,parentHeight:Float,geometry:EffectGeometry){
            val g=local(geometry);box(g,parentWidth)
            if(animatedZ)z=style.number("z-index",g,1f).roundToInt().toFloat()
            val inset=style.text("inset",g.time).takeIf(String::isNotEmpty)
            require(inset==null||inset=="0")
            val fillWidth=inset!=null||style.has("left")&&style.has("right")
            val fillHeight=inset!=null||style.has("top")&&style.has("bottom")
            val intrinsicWidth=image?.width?.toFloat()?:if(tag=="svg")props.optDouble("width",300.0).toFloat() else 0f
            val intrinsicHeight=image?.height?.toFloat()?:if(tag=="svg")props.optDouble("height",150.0).toFloat() else 0f
            var autoWidth=when {fillWidth->parentWidth-style.number("left",g,parentWidth)-style.number("right",g,parentWidth);image!=null||tag=="svg"->intrinsicWidth;!absolute&&!inline->parentWidth-marginLeft-marginRight;else->parentWidth}
            width=style.number("width",g,parentWidth,autoWidth).coerceAtLeast(0f)
            if(style.has("max-width"))width=min(width,style.number("max-width",g,parentWidth,width))
            if(style.has("min-width"))width=max(width,style.number("min-width",g,parentWidth))
            val provisionalHeight=style.number("height",g,parentHeight,if(fillHeight)parentHeight-style.number("top",g,parentHeight)-style.number("bottom",g,parentHeight)else 0f)
            if(tag!="svg")children.forEach {it.measure(width,provisionalHeight,geometry)}
            if(childrenAnimatedZ)drawChildren.sortWith(order)
            if(!style.has("width")&&!fillWidth&&(absolute||inline)&&image==null&&tag!="svg") {
                val natural=flow.sumOf {(it.width+it.marginLeft+it.marginRight).toDouble()}.toFloat()
                val minimum=flow.maxOfOrNull {it.width+it.marginLeft+it.marginRight}?:0f
                val available=parentWidth-style.number("left",g,parentWidth)-style.number("right",g,parentWidth)-marginLeft-marginRight
                autoWidth=min(natural,max(minimum,available));width=autoWidth
                if(flow.isEmpty())width=if(absolute)0f else parentWidth
            }
            var cursorX=0f;var cursorY=0f;var lineHeight=0f
            children.forEach {child->
                if(!child.absolute&&cursorX>0&&(!child.inline||cursorX+child.width+child.marginLeft+child.marginRight>width)){cursorY+=lineHeight;cursorX=0f;lineHeight=0f}
                child.flowX=cursorX;child.flowY=cursorY
                if(!child.absolute){if(child.inline){cursorX+=child.width+child.marginLeft+child.marginRight;lineHeight=max(lineHeight,child.height+child.marginTop+child.marginBottom)}else cursorY+=child.height+child.marginTop+child.marginBottom}
            }
            val autoHeight=when {fillHeight->parentHeight-style.number("top",g,parentHeight)-style.number("bottom",g,parentHeight);intrinsicWidth>0->intrinsicHeight*width/intrinsicWidth;else->cursorY+lineHeight}
            height=style.number("height",g,parentHeight,autoHeight).coerceAtLeast(0f)
            if(style.has("max-height"))height=min(height,style.number("max-height",g,parentHeight,height))
            if(style.has("min-height"))height=max(height,style.number("min-height",g,parentHeight))
        }
        fun draw(canvas:Canvas,parentWidth:Float,parentHeight:Float,geometry:EffectGeometry,flowX:Float,flowY:Float,inheritedFill:Int,inheritedFilter:ColorMatrix?){
            val g=local(geometry)
            if(g.time<0||style.text("display",g.time)=="none"||style.text("visibility",g.time,"visible")!="visible")return
            val alpha=style.number("opacity",g,1f,1f).coerceIn(0f,1f);if(alpha<=0)return
            val left=when {style.has("left")->style.number("left",g,parentWidth);style.has("right")->parentWidth-width-style.number("right",g,parentWidth);absolute->0f;else->flowX}+marginLeft
            val top=when {style.has("top")->style.number("top",g,parentHeight);style.has("bottom")->parentHeight-height-style.number("bottom",g,parentHeight);absolute->0f;else->flowY}+marginTop
            val saved=canvas.save()
            try {
                canvas.translate(left,top)
                val ox=style.component("transform-origin",0,g,width,width/2);val oy=style.component("transform-origin",1,g,height,height/2)
                val engine=style.transform(g,width,height,transform,other);engine.apply(transform,matrix)
                canvas.translate(ox,oy);canvas.concat(matrix);canvas.translate(-ox,-oy)
                if(style.text("overflow",g.time)=="hidden")canvas.clipRect(0f,0f,width,height)
                if(parentFilter!==inheritedFilter){parentFilter=inheritedFilter;combinedFilter=if(ownFilter!=null&&inheritedFilter!=null)ColorMatrix(ownFilter).apply{postConcat(inheritedFilter)}else ownFilter?:inheritedFilter;colorFilter=combinedFilter?.let(::ColorMatrixColorFilter)}
                val combined=combinedFilter
                paint.colorFilter=colorFilter;paint.alpha=255
                val layer=if(alpha<1f)canvas.saveLayerAlpha(null,(alpha*255).roundToInt()) else -1
                val fill=style.color("fill",g.time,vectorFill?:inheritedFill)
                if(style.has("background-color")) {paint.color=style.color("background-color",g.time,Color.TRANSPARENT);canvas.drawRect(0f,0f,width,height,paint)}
                if(image!=null){
                    paint.color=Color.WHITE
                    if(background){
                        val x=-style.number("background-position-x",g,image.width.toFloat());val y=-style.number("background-position-y",g,image.height.toFloat())
                        source.set(x.roundToInt(),y.roundToInt(),(x+width).roundToInt(),(y+height).roundToInt())
                        destination.set(0f,0f,width,height)
                        if(source.left>=0&&source.top>=0&&source.right<=image.width&&source.bottom<=image.height)canvas.drawBitmap(image,source,destination,paint)
                    }else{destination.set(0f,0f,width,height);canvas.drawBitmap(image,null,destination,paint)}
                }
                if(tag=="svg"){
                    val vb=viewBox?:listOf(0f,0f,width,height);val scale=min(width/vb[2],height/vb[3]);canvas.translate((width-vb[2]*scale)/2,(height-vb[3]*scale)/2);canvas.scale(scale,scale);canvas.translate(-vb[0],-vb[1])
                    children.forEach {it.drawVector(canvas,fill,combined)}
                }else{
                    drawChildren.forEach {child->child.draw(canvas,width,height,geometry,child.flowX,child.flowY,fill,combined)}
                }
                if(layer>=0)canvas.restoreToCount(layer)
            }finally{canvas.restoreToCount(saved)}
        }
        private val vectorFill=props.optString("fill").takeIf {it.isNotBlank()&&it!="inherit"&&it!="currentColor"}?.let(EffectValues::color)
        private fun drawVector(canvas:Canvas,inheritedFill:Int,filter:ColorMatrix?){
            val saved=canvas.save();try{svgTransform?.let(canvas::concat);val fill=style.color("fill",0f,vectorFill?:inheritedFill)
                if(parentFilter!==filter){parentFilter=filter;colorFilter=filter?.let(::ColorMatrixColorFilter)}
                paint.color=fill;paint.colorFilter=colorFilter;vector?.let{canvas.drawPath(it,paint)};children.forEach{it.drawVector(canvas,fill,filter)}
            }finally{canvas.restoreToCount(saved)}
        }
        private fun path():Path? {
            fun n(key:String,default:Float=0f)=props.optString(key).takeIf(String::isNotEmpty)?.removeSuffix("px")?.toFloat()?:default
            val result=when(tag){
                "path"->requireNotNull(PathParser.createPathFromPathData(props.getString("d")))
                "rect"->Path().apply{addRoundRect(n("x"),n("y"),n("x")+n("width"),n("y")+n("height"),n("rx"),n("ry",n("rx")),Path.Direction.CW)}
                "ellipse","circle"->Path().apply{val rx=if(tag=="circle")n("r")else n("rx");val ry=if(tag=="circle")rx else n("ry");addOval(n("cx")-rx,n("cy")-ry,n("cx")+rx,n("cy")+ry,Path.Direction.CW)}
                "polygon"->Path().apply{val points=props.getString("points").trim().split(Regex("[ ,]+" )).map(String::toFloat);require(points.size>=6&&points.size%2==0);moveTo(points[0],points[1]);for(i in 2 until points.size step 2)lineTo(points[i],points[i+1]);close()}
                else->null
            };if(props.optString("fillRule")=="evenodd"||style.text("fill-rule",0f)=="evenodd")result?.fillType=Path.FillType.EVEN_ODD;return result
        }
        private fun svgMatrix(text:String):Matrix {
            val result=Matrix();var end=0
            Regex("([a-zA-Z]+)\\(([^)]*)\\)").findAll(text).forEach{m->require(text.substring(end,m.range.first).trim().trim(',').isEmpty());end=m.range.last+1;val p=m.groupValues[2].trim().split(Regex("[ ,]+" )).map(String::toFloat)
                when(m.groupValues[1]){"rotate"->if(p.size==3)result.preRotate(p[0],p[1],p[2])else{require(p.size==1);result.preRotate(p[0])};"translate"->result.preTranslate(p[0],p.getOrElse(1){0f});"scale"->result.preScale(p[0],p.getOrElse(1){p[0]});"matrix"->{require(p.size==6);val x=Matrix();x.setValues(floatArrayOf(p[0],p[2],p[4],p[1],p[3],p[5],0f,0f,1f));result.preConcat(x)};else->error("Unsupported SVG transform")}
            };require(text.substring(end).isBlank());return result
        }
        companion object {val order=Comparator<Node>{a,b->val z=a.z.compareTo(b.z);if(z!=0)z else a.index.compareTo(b.index)}}
    }
}
