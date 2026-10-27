package steam.nova.app.features.chat.effects

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.Matrix
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import org.json.JSONObject
import kotlin.math.*

internal fun interface EffectNumber {
    fun at(time: Float, basis: Float, viewportWidth: Float, viewportHeight: Float): Float
}

/** CSS values and the arithmetic expressions emitted by the restricted JS interpreter. */
internal object EffectValues {
    val zero = constant(0f)
    val one = constant(1f)
    fun constant(n: Float) = EffectNumber { _, _, _, _ -> n }
    fun split(text: String, delimiter: Char = ','): List<String> {
        val result = ArrayList<String>(); var depth = 0; var start = 0; var quote = '\u0000'
        for (i in text.indices) {
            val c = text[i]
            if (quote != '\u0000') { if (c == quote && (i == 0 || text[i - 1] != '\\')) quote = '\u0000'; continue }
            if (c == '\'' || c == '"') quote = c
            else if (c == '(' || c == '[') depth++
            else if (c == ')' || c == ']') depth--
            else if (depth == 0 && (c == delimiter || delimiter == ' ' && c.isWhitespace())) {
                text.substring(start, i).trim().takeIf(String::isNotEmpty)?.let(result::add); start = i + 1
            }
        }
        text.substring(start).trim().takeIf(String::isNotEmpty)?.let(result::add)
        return result
    }

    fun expression(value: Any?): EffectNumber {
        if (value is Number) return constant(value.toFloat())
        require(value is JSONObject && value.has("expression")) { "Unsupported effect expression" }
        val op = value.getString("expression"); val raw = value.getJSONArray("args")
        val args = (0 until raw.length()).map { expression(raw.get(it)) }
        require(op in setOf("+", "-", "*", "/", "%", "**", "lerp", "progress"))
        require(args.size == if (op == "lerp") 3 else if (op == "progress") 1 else 2)
        return EffectNumber { t, b, w, h ->
            val a = args[0].at(t, b, w, h)
            if (op == "progress") (t / a.coerceAtLeast(1f)).coerceIn(0f, 1f)
            else {
                val c = args[1].at(t, b, w, h)
                when (op) {
                    "+" -> a + c; "-" -> a - c; "*" -> a * c; "/" -> a / c; "%" -> a % c; "**" -> a.pow(c)
                    else -> a + (c - a) * args[2].at(t, b, w, h)
                }
            }
        }
    }

    data class Template(val text: String, val variables: Map<String, EffectNumber>)
    fun template(value: Any?): Template {
        if (value !is JSONObject || !value.has("template")) return Template(value?.toString().orEmpty(), emptyMap())
        val variables = LinkedHashMap<String, EffectNumber>(); val parts = value.getJSONArray("template")
        val text = buildString {
            for (i in 0 until parts.length()) {
                val p = parts.get(i)
                if (p is JSONObject) { val key = "__m${variables.size}"; variables[key] = expression(p); append(key) }
                else append(p.toString())
            }
        }
        return Template(text, variables)
    }

    fun number(value: Any?, variables: Map<String, EffectNumber> = emptyMap()): EffectNumber {
        if (value is JSONObject) return expression(value)
        if (value is Number) return constant(value.toFloat())
        val source = value?.toString()?.trim().orEmpty()
        require(source.isNotEmpty()) { "Empty CSS number" }
        class Parser {
            var at = 0
            fun space() { while (at < source.length && source[at].isWhitespace()) at++ }
            fun term(): EffectNumber {
                space()
                if (source.startsWith("calc(", at)) { at += 5; val n = sum(); space(); require(source.getOrNull(at++) == ')'); return n }
                if (source.getOrNull(at) == '(') { at++; val n = sum(); space(); require(source.getOrNull(at++) == ')'); return n }
                if (source.getOrNull(at) == '+' || source.getOrNull(at) == '-') { val sign = if (source[at++] == '-') -1f else 1f; val n = term(); return EffectNumber { t,b,w,h -> sign*n.at(t,b,w,h) } }
                val start = at
                val n: EffectNumber
                if (source.startsWith("__m", at)) {
                    at += 3; while (source.getOrNull(at)?.isDigit() == true) at++
                    n = requireNotNull(variables[source.substring(start, at)])
                } else {
                    while (source.getOrNull(at)?.let { it.isDigit() || it == '.' } == true) at++
                    if (source.getOrNull(at) == 'e' || source.getOrNull(at) == 'E') { at++; if (source.getOrNull(at) in listOf('+','-')) at++; while (source.getOrNull(at)?.isDigit() == true) at++ }
                    require(at > start) { "Unsupported CSS length: $source" }; n = constant(source.substring(start, at).toFloat())
                }
                val unitStart = at; while (source.getOrNull(at)?.let { it.isLetter() || it == '%' } == true) at++
                val unit = source.substring(unitStart, at)
                require(unit in setOf("", "px", "%", "vw", "vh", "deg", "rad", "turn")) { "Unsupported CSS unit: $unit" }
                return EffectNumber { t,b,w,h -> n.at(t,b,w,h) * when(unit) { "%" -> b/100; "vw" -> w/100; "vh" -> h/100; "rad" -> 180f/PI.toFloat(); "turn" -> 360f; else -> 1f } }
            }
            fun product(): EffectNumber { var n = term(); while (true) { space(); val op = source.getOrNull(at); if(op!='*'&&op!='/')return n; at++; val a=n; val b=term(); n=EffectNumber { t,p,w,h -> if(op=='*')a.at(t,p,w,h)*b.at(t,p,w,h) else a.at(t,p,w,h)/b.at(t,p,w,h) } } }
            fun sum(): EffectNumber { var n = product(); while(true) { space(); val op=source.getOrNull(at); if(op!='+'&&op!='-')return n; at++; val a=n; val b=product(); n=EffectNumber { t,p,w,h -> if(op=='+')a.at(t,p,w,h)+b.at(t,p,w,h) else a.at(t,p,w,h)-b.at(t,p,w,h) } } }
        }
        return Parser().run { val result=sum(); space(); require(at==source.length) { "Unsupported CSS number: $source" }; result }
    }

    fun color(value: String): Int {
        val text=value.trim()
        if(text=="none"||text=="transparent")return Color.TRANSPARENT
        if(text.startsWith("hsl")) {
            val p=split(text.substringAfter('(').substringBeforeLast(')')); require(p.size>=3)
            val rgb=ColorUtils.HSLToColor(floatArrayOf(((p[0].toFloat()%360)+360)%360,p[1].removeSuffix("%").toFloat()/100,p[2].removeSuffix("%").toFloat()/100))
            return if(p.size==4)ColorUtils.setAlphaComponent(rgb,(p[3].toFloat().coerceIn(0f,1f)*255).roundToInt()) else rgb
        }
        if(text.startsWith("rgb")) {
            val p=split(text.substringAfter('(').substringBeforeLast(')'))
            require(p.size in 3..4)
            fun channel(s:String):Int = if(s.endsWith('%')) (s.dropLast(1).toFloat()*2.55f).roundToInt() else s.toFloat().roundToInt()
            return Color.argb(if(p.size==4)(p[3].toFloat()*255).roundToInt().coerceIn(0,255)else 255,
                channel(p[0]).coerceIn(0,255),channel(p[1]).coerceIn(0,255),channel(p[2]).coerceIn(0,255))
        }
        return text.toColorInt()
    }

    fun easing(text: String): (Float) -> Float {
        val s=text.trim()
        if(s=="linear")return {it}
        if(s=="step-start")return {1f};if(s=="step-end")return {if(it>=1)1f else 0f}
        if(s.startsWith("steps(")) { val p=split(s.substringAfter('(').substringBeforeLast(')'));val n=p[0].toFloat();require(n>=1);val jump=p.getOrElse(1){"end"};require(jump in setOf("start","end","jump-start","jump-end"));return { x -> ((floor(x*n)+if(jump=="start"||jump=="jump-start")1 else 0)/n).coerceIn(0f,1f) } }
        val values=when(s){"ease"->listOf(.25f,.1f,.25f,1f);"ease-in"->listOf(.42f,0f,1f,1f);"ease-out"->listOf(0f,0f,.58f,1f);"ease-in-out"->listOf(.42f,0f,.58f,1f);else->{require(s.startsWith("cubic-bezier("));split(s.substringAfter('(').substringBeforeLast(')')).map(String::toFloat)}}
        require(values.size==4&&values[0] in 0f..1f&&values[2] in 0f..1f)
        fun cubic(t:Float,a:Float,b:Float):Float {val q=1-t;return 3*q*q*t*a+3*q*t*t*b+t*t*t}
        return { x -> var low=0f;var high=1f;repeat(16){val middle=(low+high)/2;if(cubic(middle,values[0],values[2])<x)low=middle else high=middle};cubic((low+high)/2,values[1],values[3]) }
    }

    fun filter(text: String): ColorMatrix? {
        if(text.isBlank()||text=="none")return null
        val result=ColorMatrix();var end=0
        Regex("([a-z-]+)\\(([^)]*)\\)").findAll(text).forEach { m ->
            require(text.substring(end,m.range.first).isBlank());end=m.range.last+1
            val next=ColorMatrix()
            when(m.groupValues[1]) {
                "hue-rotate"->{val a=number(m.groupValues[2]).at(0f,1f,1f,1f)*PI/180;val c=cos(a).toFloat();val s=sin(a).toFloat();next.set(floatArrayOf(.213f+.787f*c-.213f*s,.715f-.715f*c-.715f*s,.072f-.072f*c+.928f*s,0f,0f,.213f-.213f*c+.143f*s,.715f+.285f*c+.140f*s,.072f-.072f*c-.283f*s,0f,0f,.213f-.213f*c-.787f*s,.715f-.715f*c+.715f*s,.072f+.928f*c+.072f*s,0f,0f,0f,0f,0f,1f,0f))}
                "saturate"->next.setSaturation(number(m.groupValues[2]).at(0f,1f,1f,1f))
                else->error("Unsupported effect filter")
            };result.postConcat(next)
        }
        require(text.substring(end).isBlank());return result
    }
}

internal class EffectTransform private constructor(private val operations: List<Op>) {
    private data class Op(val kind: String,val args: List<EffectNumber>)
    private val scratch=FloatArray(16);private val product=FloatArray(16);private val androidValues=FloatArray(9)
    fun matrix(time:Float,width:Float,height:Float,vw:Float,vh:Float,out:FloatArray) {
        android.opengl.Matrix.setIdentityM(out,0)
        operations.forEach { op ->
            fun n(i:Int,basis:Float=1f,default:Float=0f)=op.args.getOrNull(i)?.at(time,basis,vw,vh)?:default
            android.opengl.Matrix.setIdentityM(scratch,0)
            when(op.kind){
                "translate"->android.opengl.Matrix.translateM(scratch,0,n(0,width),n(1,height),n(2))
                "scale"->android.opengl.Matrix.scaleM(scratch,0,n(0,default=1f),n(1,default=n(0,default=1f)),n(2,default=1f))
                "rotate"->android.opengl.Matrix.rotateM(scratch,0,n(0),n(1),n(2),n(3,default=1f))
                "matrix"->{scratch[0]=n(0);scratch[1]=n(1);scratch[4]=n(2);scratch[5]=n(3);scratch[12]=n(4);scratch[13]=n(5)}
            }
            android.opengl.Matrix.multiplyMM(product,0,out,0,scratch,0);product.copyInto(out)
        }
    }
    fun apply(out:FloatArray,matrix:Matrix){androidValues[0]=out[0];androidValues[1]=out[4];androidValues[2]=out[12];androidValues[3]=out[1];androidValues[4]=out[5];androidValues[5]=out[13];androidValues[6]=out[3];androidValues[7]=out[7];androidValues[8]=out[15];matrix.setValues(androidValues)}

    fun blend(other:EffectTransform,p:Float,t:Float,w:Float,h:Float,vw:Float,vh:Float,out:FloatArray,otherMatrix:FloatArray){
        val compatible=(0 until max(operations.size,other.operations.size)).all { i ->
            val a=operations.getOrNull(i);val b=other.operations.getOrNull(i)
            a==null||b==null||a.kind==b.kind&&(a.kind!="rotate"||a.args.drop(1).map{it.at(0f,1f,1f,1f)}==b.args.drop(1).map{it.at(0f,1f,1f,1f)})
        }
        if(compatible){
            android.opengl.Matrix.setIdentityM(out,0)
            for(i in 0 until max(operations.size,other.operations.size)){
                val a=operations.getOrNull(i);val b=other.operations.getOrNull(i);val kind=(a?:b)!!.kind
                fun value(op:Op?,index:Int,basis:Float):Float {
                    if(op==null)return when(kind) {
                        "scale"->1f
                        "rotate"->if(index==3)1f else 0f
                        else->0f
                    }
                    return op.args.getOrNull(index)?.at(t,basis,vw,vh)?:when(kind) {
                        "scale"->if(index==1)op.args[0].at(t,1f,vw,vh)else 1f
                        "rotate"->if(index==3)1f else 0f
                        else->0f
                    }
                }
                fun n(index:Int,basis:Float=1f):Float {val x=value(a,index,basis);return x+(value(b,index,basis)-x)*p}
                android.opengl.Matrix.setIdentityM(scratch,0)
                when(kind){"translate"->android.opengl.Matrix.translateM(scratch,0,n(0,w),n(1,h),n(2));"scale"->android.opengl.Matrix.scaleM(scratch,0,n(0),n(1),n(2));"rotate"->{val axis=a?:b!!;android.opengl.Matrix.rotateM(scratch,0,n(0),value(axis,1,1f),value(axis,2,1f),value(axis,3,1f))};else->{matrix(t,w,h,vw,vh,out);other.matrix(t,w,h,vw,vh,otherMatrix);interpolate2d(out,otherMatrix,p);return}}
                android.opengl.Matrix.multiplyMM(product,0,out,0,scratch,0);product.copyInto(out)
            }
        }else{matrix(t,w,h,vw,vh,out);other.matrix(t,w,h,vw,vh,otherMatrix);interpolate2d(out,otherMatrix,p)}
    }
    private fun interpolate2d(a:FloatArray,b:FloatArray,p:Float){
        // CSS falls back to matrix decomposition where transform lists stop matching.
        fun decompose(m:FloatArray):FloatArray{val sx=hypot(m[0],m[1]).coerceAtLeast(0.000001f);val sy=(m[0]*m[5]-m[1]*m[4])/sx;val skew=(m[0]*m[4]+m[1]*m[5])/(sx*sx);return floatArrayOf(m[12],m[13],sx,sy,atan2(m[1],m[0]),skew)}
        val x=decompose(a);val y=decompose(b);while(y[4]-x[4]>PI)y[4]-=(2*PI).toFloat();while(y[4]-x[4]<-PI)y[4]+=(2*PI).toFloat();for(i in x.indices)x[i]+=(y[i]-x[i])*p
        val c=cos(x[4]);val s=sin(x[4]);android.opengl.Matrix.setIdentityM(a,0);a[0]=c*x[2];a[1]=s*x[2];a[4]=c*x[2]*x[5]-s*x[3];a[5]=s*x[2]*x[5]+c*x[3];a[12]=x[0];a[13]=x[1]
    }
    companion object {
        val identity=EffectTransform(emptyList())
        fun parse(raw:Any?):EffectTransform {
            val template=EffectValues.template(raw);val text=template.text.trim();if(text.isEmpty()||text=="none")return identity
            // This invalid declaration exists in Steam's snowball component; CSS ignores it.
            if(text.startsWith("rotation("))return identity
            val ops=ArrayList<Op>();var at=0
            while(at<text.length){while(at<text.length&&text[at].isWhitespace())at++;if(at==text.length)break;val open=text.indexOf('(',at);require(open>at);val name=text.substring(at,open).trim();var depth=1;var end=open+1;while(end<text.length&&depth>0){if(text[end]=='(')depth++;if(text[end]==')')depth--;end++};require(depth==0)
                val content=text.substring(open+1,end-1);var args=EffectValues.split(content).let{if(it.size==1)EffectValues.split(content,' ')else it}.map {EffectValues.number(it,template.variables)}
                val kind=when(name){"translate","translate3d"->"translate";"translateX"->{args=listOf(args[0],EffectValues.zero);"translate"};"translateY"->{args=listOf(EffectValues.zero,args[0]);"translate"};"scale","scale3d"->"scale";"scaleX"->{args=listOf(args[0],EffectValues.one);"scale"};"scaleY"->{args=listOf(EffectValues.one,args[0]);"scale"};"rotate","rotateZ"->{args=listOf(args[0],EffectValues.zero,EffectValues.zero,EffectValues.one);"rotate"};"rotateX"->{args=listOf(args[0],EffectValues.one,EffectValues.zero,EffectValues.zero);"rotate"};"rotateY"->{args=listOf(args[0],EffectValues.zero,EffectValues.one,EffectValues.zero);"rotate"};"matrix"->{require(args.size==6);"matrix"};else->error("Unsupported effect transform: $name")}
                require(args.isNotEmpty());ops+=Op(kind,args);at=end
            };return EffectTransform(ops)
        }
    }
}
