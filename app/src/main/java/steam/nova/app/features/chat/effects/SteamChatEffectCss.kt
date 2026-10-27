package steam.nova.app.features.chat.effects

/** Converts the CSS subset used by room effects; rejects unfamiliar rendering rules. */
internal object SteamChatEffectCss {
    fun source() = """
var MillenniumEffectCss=(function(){
    'use strict';
    function fail(s){throw Error('Unsupported Steam effect CSS: '+s);}
    function split(s,separator){var out=[],depth=0,quote='',from=0;for(var i=0;i<s.length;i++){var c=s.charAt(i);if(quote){if(c===quote&&s.charAt(i-1)!=='\\')quote='';continue;}if(c==='"'||c==="'"){quote=c;continue;}if(c==='('||c==='[')depth++;else if(c===')'||c===']')depth--;else if(depth===0&&(separator===' '?/\s/.test(c):c===separator)){if(s.slice(from,i).trim())out.push(s.slice(from,i).trim());from=i+1;}}if(s.slice(from).trim())out.push(s.slice(from).trim());return out;}
    function declarations(s){var out={};split(s,';').forEach(function(pair){var i=pair.indexOf(':');if(i>0)out[pair.slice(0,i).trim()]=pair.slice(i+1).trim();});return out;}
    function blocks(s,fn){var at=0;while(at<s.length){var open=s.indexOf('{',at);if(open<0)break;var depth=1,quote='',i=open+1;for(;i<s.length&&depth;i++){var c=s.charAt(i);if(quote){if(c===quote&&s.charAt(i-1)!=='\\')quote='';}else if(c==='"'||c==="'")quote=c;else if(c==='{')depth++;else if(c==='}')depth--;}if(depth)fail('unclosed block');fn(s.slice(at,open).trim(),s.slice(open+1,i-1));at=i;}}
    var allowed=('position display inset top right bottom left width height min-width min-height max-width max-height margin margin-left margin-right margin-top margin-bottom opacity transform transform-origin filter background-color background-image background-position-x background-position-y background-size background-repeat overflow z-index pointer-events animation animation-name animation-duration animation-delay animation-timing-function animation-iteration-count animation-fill-mode animation-direction animation-play-state fill fill-rule clip-rule border-width border-color border-style will-change').split(' ');
    allowed.push('visibility');
    function check(style){Object.keys(style).forEach(function(k){if(k==='fl-random'){delete style[k];return;}if(allowed.indexOf(k)<0)fail('property '+k);if(typeof style[k]==='string'&&/!important\s*$/.test(style[k]))style[k]=style[k].replace(/\s*!important\s*$/,'');});}
    function animations(style){var result=[];
        if(style.animation)split(style.animation,',').forEach(function(part){var a={name:'none',duration:0,delay:0,timing:'ease',iterations:1,fill:'none',direction:'normal'},times=0;
            split(part,' ').forEach(function(v){if(/^-?[\d.]+m?s$/.test(v)){a[times++?'delay':'duration']=parseFloat(v)*(v.slice(-2)==='ms'?1:1000);}
                else if(/^(linear|ease|ease-in|ease-out|ease-in-out|step-start|step-end)$/.test(v)||/^(steps|cubic-bezier)\(/.test(v))a.timing=v;
                else if(/^(none|forwards|backwards|both)$/.test(v))a.fill=v;
                else if(/^(normal|reverse|alternate|alternate-reverse)$/.test(v))a.direction=v;
                else if(v==='infinite')a.iterations=-1;else if(/^[\d.]+$/.test(v))a.iterations=+v;else if(v==='running'||v==='paused')a.paused=v==='paused';else a.name=v;});result.push(a);
        });
        if(style['animation-name']){var names=split(style['animation-name'],',');if(!result.length)names.forEach(function(){result.push({name:'none',duration:0,delay:0,timing:'ease',iterations:1,fill:'none',direction:'normal'});});names.forEach(function(v,i){if(!result[i])result[i]={};result[i].name=v;});}
        var mapping={duration:'animation-duration',delay:'animation-delay',timing:'animation-timing-function',iterations:'animation-iteration-count',fill:'animation-fill-mode',direction:'animation-direction'};
        Object.keys(mapping).forEach(function(k){var v=style[mapping[k]];if(v===undefined)return;var parts=split(String(v),',');result.forEach(function(a,i){var x=parts[i%parts.length];a[k]=k==='duration'||k==='delay'?parseFloat(x)*(x.slice(-2)==='ms'?1:1000):k==='iterations'?(x==='infinite'?-1:+x):x;});});
        return result.filter(function(a){return a.name&&a.name!=='none'&&a.duration>0;});
    }
    function Sheet(css){var self=this;this.rules=[];this.index={};this.frames={};css=css.replace(/\/\*[\s\S]*?\*\//g,'');
        blocks(css,function(selector,body){if(/^@(?:-webkit-)?keyframes\s/.test(selector)){var name=selector.split(/\s+/).pop(),frames=[];blocks(body,function(offset,decl){var values=declarations(decl);split(offset,',').forEach(function(p){var n=p==='from'?0:p==='to'?1:parseFloat(p)/100;if(!isFinite(n)||n<0||n>1)fail('keyframe offset');frames.push({offset:n,style:values});});});self.frames[name]=frames.sort(function(a,b){return a.offset-b.offset;});}
            else if(selector.charAt(0)!=='@')self.rules.push({selectors:split(selector,','),style:declarations(body)});
            // Conditional effect rules require an adapter rather than silently dropping media behavior.
            else if(/^@(media|supports|layer)/.test(selector))self.rules.push({conditional:true,source:body,selectors:[],style:{}});
        });
        this.rules.forEach(function(rule,index){var source=rule.conditional?rule.source:rule.selectors.join(','),pattern=/\.([A-Za-z0-9_-]+)/g,match,seen={};
            while((match=pattern.exec(source))){var name=match[1];if(!seen[name]){seen[name]=true;(self.index[name]||(self.index[name]=[])).push(index);}}
        });
    }
    Sheet.prototype.convert=function(scene){var self=this,frames={},assets={},count=0;
        function walk(node){if(++count>5000)fail('node budget');var props=node.props,classes=(props.className||'').split(/\s+/).filter(Boolean),style={};
            Object.keys(props).forEach(function(k){if(('className style src viewBox width height x y version xmlns fill fillRule clipRule d points cx cy r rx ry transform').split(' ').indexOf(k)<0)fail('element attribute '+k);});
            var indices={};classes.forEach(function(c){(self.index[c]||[]).forEach(function(i){indices[i]=true;});});
            Object.keys(indices).map(Number).sort(function(a,b){return a-b;}).forEach(function(i){var rule=self.rules[i];
                if(rule.conditional)fail('conditional effect styles');
                rule.selectors.forEach(function(selector){
                    var tokens=selector.match(/\.[A-Za-z0-9_-]+/g)||[],referenced=classes.some(function(c){return tokens.indexOf('.'+c)>=0;});if(!referenced)return;
                    if(!/^(?:\.[A-Za-z0-9_-]+)+$/.test(selector))fail('selector '+selector);
                    var required=selector.slice(1).split('.');if(required.every(function(c){return classes.indexOf(c)>=0;}))Object.keys(rule.style).forEach(function(k){style[k]=rule.style[k];});
                });
            });
            Object.keys(props.style||{}).forEach(function(k){var key=k.replace(/[A-Z]/g,function(v){return '-'+v.toLowerCase();});style[key]=props.style[k];});check(style);
            if(['path','rect','ellipse','circle','g','polygon'].indexOf(node.tag)>=0)Object.keys(style).forEach(function(k){if(['fill','fill-rule','clip-rule'].indexOf(k)<0)fail('vector style '+k);});
            if(style.position&&['absolute','relative','static'].indexOf(style.position)<0)fail('position '+style.position);
            if(style.display&&['block','inline','inline-block','none'].indexOf(style.display)<0)fail('display '+style.display);
            if(style['background-size']&&style['background-size']!=='auto')fail('background sizing');
            if(style['background-repeat']&&style['background-repeat']!=='no-repeat')fail('background repeat');
            if(style['border-style']&&style['border-style']!=='none')fail('border style');
            if(style.overflow&&['hidden','visible'].indexOf(style.overflow)<0)fail('overflow');
            if(style['animation-play-state']&&style['animation-play-state']!=='running')fail('paused animation');
            node.style=style;node.animations=animations(style);delete props.style;
            node.animations.forEach(function(a){if(a.paused||!isFinite(a.duration)||!isFinite(a.delay)||!isFinite(a.iterations)||a.iterations===0)fail('animation timing');});
            if(props.src)assets[props.src]=true;
            Object.keys(style).forEach(function(k){if(k==='background-image'){var m=/^url\(["']?([^"')]+)["']?\)$/.exec(style[k]);if(!m)fail('background image');node.image=m[1];assets[m[1]]=true;}});
            node.animations.forEach(function(a){if(!self.frames[a.name])fail('missing keyframes '+a.name);frames[a.name]=self.frames[a.name];frames[a.name].forEach(function(frame){check(frame.style);
                Object.keys(frame.style).forEach(function(k){if(('opacity left right top bottom width height min-width max-width min-height max-height margin margin-left margin-right margin-top margin-bottom transform transform-origin background-position-x background-position-y visibility z-index animation-timing-function').split(' ').indexOf(k)<0)fail('animated property '+k);});
            });});
            node.children.forEach(walk);
        }
        scene.nodes.forEach(walk);scene.frames=frames;scene.assets=Object.keys(assets);return scene;
    };
    Sheet.prototype.snapshot=function(){return {rules:this.rules,index:this.index,frames:this.frames};};
    return {
        parse:function(css){return new Sheet(css);},
        restore:function(data){
            if(!data||!Array.isArray(data.rules)||!data.index||!data.frames)fail('cached stylesheet');
            var sheet=Object.create(Sheet.prototype);sheet.rules=data.rules;sheet.index=data.index;sheet.frames=data.frames;return sheet;
        }
    };
})();
""".trimIndent()
}
