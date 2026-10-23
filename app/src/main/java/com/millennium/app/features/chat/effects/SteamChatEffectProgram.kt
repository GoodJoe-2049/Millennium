package com.millennium.app.features.chat.effects

/** Restricted AST evaluator. Downloaded programs never execute through eval or access Java. */
internal object SteamChatEffectProgram {
    fun source() = $$"""
var MillenniumEffectProgram = (function(){
    'use strict';
    function fail(s){throw Error('Unsupported Steam effect: '+s);}
    function own(o,k){return Object.prototype.hasOwnProperty.call(o,k);}
    function object(){return Object.create(null);}
    function Scope(parent,mod){this.parent=parent;this.mod=mod||(parent&&parent.mod);this.values=object();}
    function Program(files,main,cached){this.files=files;this.main=main;this.modules=object();this.cached=cached||null;this.prepared=null;this.steps=0;this.symbols=[];this.width=430;this.height=700;this.now=1;this.seed=1;}
    Program.prototype.tick=function(){if(++this.steps>3000000)fail('instruction budget');};
    Program.prototype.random=function(){this.seed=(Math.imul?Math.imul(this.seed,1664525):this.seed*1664525)+1013904223;this.seed=this.seed>>>0;return this.seed/4294967296;};
    Program.prototype.module=function(url){
        if(this.modules[url])return this.modules[url];
        if(!own(this.files,url))throw {need:url};
        var text=this.files[url],m={url:url,defs:object(),imports:object(),exports:object(),loading:object()},self=this;
        m.scope=new Scope(null,m);this.modules[url]=m;
        // Only declaration data is restored. Scopes, functions and random state are always fresh.
        var cached=this.cached&&this.cached.modules[url];
        if(cached){
            if(!cached.defs||!cached.imports||!cached.exports)fail('cached module');
            m.defs=cached.defs;m.imports=cached.imports;m.exports=cached.exports;return m;
        }
        // Keep only top-level declaration ranges. Parse the reachable effect expressions lazily.
        var tokens=acorn.tokenizer(text,{ecmaVersion:2022,sourceType:'module'}),token=tokens.getToken();
        function next(){token=tokens.getToken();return token;}
        function label(){return token.type.label;}
        function skip(stops){var depth=0;while(label()!=='eof'){
            var l=label();if(depth===0&&stops.indexOf(l)>=0)break;
            if(l==='('||l==='['||l==='{'||l==='${')depth++;
            if(l===')'||l===']'||l==='}')depth--;
            if(depth<0)fail('declaration boundaries at '+token.start+' '+text.slice(Math.max(0,token.start-80),token.end+20));next();
        }}
        while(label()!=='eof'){
            self.tick();var start=token.start,l=label();
            if(l==='name'&&token.value==='async'){next();l=label();if(l!=='function'){skip([';']);if(label()===';')next();continue;}}
            if(l==='var'||l==='let'||l==='const'){
                next();while(label()!=='eof'){
                    if(label()!=='name')fail('top-level binding');var name=token.value;next();
                    if(label()==='='){next();var from=token.start;skip([',',';']);m.defs[name]={source:text.slice(from,token.start),expression:true};}
                    else m.defs[name]={source:'undefined',expression:true};
                    if(label()!==',')break;next();
                }if(label()===';')next();
            }else if(l==='function'||l==='class'){
                next();if(label()==='*')fail('generator declaration');var id=token.value;
                var headerDepth=0;while(label()!=='eof'){
                    var head=label();if(head==='{'&&headerDepth===0)break;
                    if(head==='('||head==='['||head==='{')headerDepth++;
                    if(head===')'||head===']'||head==='}')headerDepth--;next();
                }if(label()==='eof')fail('declaration body');
                next();skip(['}']);var end=token.end;next();m.defs[id]={source:text.slice(start,end),expression:l==='class'};
            }else if(l==='import'||l==='export'){
                skip([';']);var end=token.end;if(label()===';')next();
                var declaration=text.slice(start,end);
                // A sliced export statement has no declarations; prepend inert bindings for Acorn's validation.
                if(l==='export'){
                    var exportTokens=acorn.tokenizer(declaration,{ecmaVersion:2022,sourceType:'module'}),bindingNames=[],et;
                    while((et=exportTokens.getToken()).type.label!=='eof')if(et.type.label==='name'&&bindingNames.indexOf(et.value)<0)bindingNames.push(et.value);
                    var prefix=bindingNames.length?'var '+bindingNames.join(',')+';':'';
                    var statements=acorn.parse(prefix+declaration,{ecmaVersion:2022,sourceType:'module'}).body;
                    var parsed=statements[statements.length-1];
                }else var parsed=acorn.parse(declaration,{ecmaVersion:2022,sourceType:'module'}).body[0];
                if(parsed.type==='ImportDeclaration')parsed.specifiers.forEach(function(s){m.imports[s.local.name]={path:parsed.source.value,name:s.imported?s.imported.name:'default'};});
                else if(parsed.type==='ExportNamedDeclaration'&&!parsed.source)parsed.specifiers.forEach(function(s){m.exports[s.exported.name]=s.local.name;});
                else fail('module export');
            }else {skip([';']);if(label()===';')next();}
        }
        return m;
    };
    Program.prototype.path=function(base,relative){
        if(!/^\.\//.test(relative))fail('external module');return base.substring(0,base.lastIndexOf('/')+1)+relative.substring(2);
    };
    Program.prototype.ast=function(d){if(!d.ast)d.ast=d.expression?acorn.parseExpressionAt(d.source,0,{ecmaVersion:2022}):acorn.parse(d.source,{ecmaVersion:2022}).body[0];return d.ast;};
    Program.prototype.lookup=function(scope,name){
        this.tick();for(var s=scope;s;s=s.parent)if(own(s.values,name))return s.values[name];
        var m=scope.mod,d=m.defs[name];
        if(d){if(m.loading[name])fail('cyclic binding '+name);m.loading[name]=true;
            try{var value=this.eval(this.ast(d),m.scope);m.scope.values[name]=value;return value;}finally{delete m.loading[name];}}
        var i=m.imports[name];if(i){var dep=this.module(this.path(m.url,i.path)),target=dep.exports[i.name];if(!target)fail('missing export '+i.name);return this.lookup(dep.scope,target);}
        if(own(this.globals,name))return this.globals[name];fail('binding '+name);
    };
    Program.prototype.bind=function(pattern,value,scope){
        var self=this;if(!pattern)return;
        if(pattern.type==='Identifier'){scope.values[pattern.name]=value;return;}
        if(pattern.type==='AssignmentPattern'){this.bind(pattern.left,value===undefined?this.eval(pattern.right,scope):value,scope);return;}
        if(pattern.type==='RestElement'){this.bind(pattern.argument,value,scope);return;}
        if(pattern.type==='ArrayPattern'){pattern.elements.forEach(function(p,i){if(p)self.bind(p,p.type==='RestElement'?(value||[]).slice(i):(value||[])[i],scope);});return;}
        if(pattern.type==='ObjectPattern'){var used=[];pattern.properties.forEach(function(p){if(p.type==='RestElement'){var rest=object();Object.keys(value||{}).forEach(function(k){if(used.indexOf(k)<0)rest[k]=value[k];});self.bind(p.argument,rest,scope);}else{var k=p.computed?self.eval(p.key,scope):(p.key.name||p.key.value);used.push(k);self.bind(p.value,self.get(value,k),scope);}});return;}
        fail('binding '+pattern.type);
    };
    Program.prototype.fn=function(node,scope){return {fn:node,scope:scope,lexical:node.type==='ArrowFunctionExpression'?this.currentThis:undefined};};
    Program.prototype.invoke=function(f,receiver,args){
        this.tick();var self=this;
        if(typeof f==='function')return f.apply(receiver,Array.isArray(receiver)?args.map(function(v){return v&&v.fn?function(){return self.invoke(v,this,Array.prototype.slice.call(arguments));}:v;}):args);
        if(!f||!f.fn)fail('non-callable');var node=f.fn,env=new Scope(f.scope),prior=this.currentThis;
        this.currentThis=node.type==='ArrowFunctionExpression'?f.lexical:receiver;
        env.values.arguments=args;
        try{node.params.forEach(function(p,i){self.bind(p,p.type==='RestElement'?args.slice(i):args[i],env);});
            if(node.body.type!=='BlockStatement')return this.eval(node.body,env);
            try{this.exec(node.body,env);}catch(r){if(r&&r.control==='return')return r.value;throw r;}
        }finally{this.currentThis=prior;}
    };
    Program.prototype.get=function(o,k){
        this.tick();if(k==='__proto__'||k==='prototype'||k==='constructor')fail('prototype access');
        if(o==null)fail('property '+k);
        if(o.getters&&own(o.getters,k))return this.invoke(o.getters[k],null,[]);
        if(o.expression&&k==='interpolate'){var self=this;return function(callback){return self.invoke(callback,null,[o]);};}
        if(o.klass&&own(o.static,k))return o.static[k];
        if(o.instance){var c=o.instance;while(c){if(own(c.methods,k))return c.methods[k];c=c.base;}}
        return o[k];
    };
    Program.prototype.assign=function(node,scope,value){
        if(node.type==='Identifier'){for(var s=scope;s;s=s.parent)if(own(s.values,node.name)){s.values[node.name]=value;return value;}scope.mod.scope.values[node.name]=value;return value;}
        if(node.type==='MemberExpression'){var o=this.eval(node.object,scope),k=node.computed?this.eval(node.property,scope):node.property.name;
            if(k==='__proto__'||k==='prototype'||k==='constructor')fail('prototype write');
            if(o.klass)o.static[k]=value;else o[k]=value;return value;}
        fail('assignment');
    };
    function dynamic(v){return v&&typeof v==='object'&&(v.expression||v.template);}
    function template(parts){var out=[];parts.forEach(function(p){if(p&&p.template)out=out.concat(p.template);else if(p!==undefined)out.push(p);});return out.some(dynamic)?{template:out}:out.join('');}
    Program.prototype.binary=function(op,a,b){
        if(op==='+'&&(typeof a==='string'||typeof b==='string'||a&&a.template||b&&b.template))return template([a,b]);
        if(dynamic(a)||dynamic(b)){if(['+','-','*','/','%','**'].indexOf(op)<0)fail('symbolic '+op);return {expression:op,args:[a,b]};}
        switch(op){case '+':return a+b;case '-':return a-b;case '*':return a*b;case '/':return a/b;case '%':return a%b;case '**':return Math.pow(a,b);case '<':return a<b;case '>':return a>b;case '<=':return a<=b;case '>=':return a>=b;case '==':return a==b;case '!=':return a!=b;case '===':return a===b;case '!==':return a!==b;case '|':return a|b;case '&':return a&b;case '^':return a^b;case '<<':return a<<b;case '>>':return a>>b;case '>>>':return a>>>b;case 'in':return b!=null&&own(b,a);default:fail('operator '+op);}
    };
    Program.prototype.eval=function(n,e){
        this.tick();if(!n)return undefined;var self=this,a,b,k;
        switch(n.type){
        case 'Literal':if(n.regex)return new RegExp(n.regex.pattern,n.regex.flags);return n.value;
        case 'Identifier':return this.lookup(e,n.name);
        case 'ThisExpression':return this.currentThis;
        case 'Super':return {superclass:this.currentClass.base};
        case 'ChainExpression':return this.eval(n.expression,e);
        case 'ArrayExpression':a=[];n.elements.forEach(function(x){if(!x)a.push(undefined);else if(x.type==='SpreadElement')a=a.concat(self.eval(x.argument,e));else a.push(self.eval(x,e));});return a;
        case 'ObjectExpression':a=object();n.properties.forEach(function(p){if(p.type==='SpreadElement'){var v=self.eval(p.argument,e);Object.keys(v||{}).forEach(function(k){a[k]=v[k];});}else{var k=p.computed?self.eval(p.key,e):(p.key.name||p.key.value);if(k==='__proto__')fail('prototype literal');a[k]=self.eval(p.value,e);}});return a;
        case 'FunctionDeclaration':case 'FunctionExpression':case 'ArrowFunctionExpression':return this.fn(n,e);
        case 'SequenceExpression':n.expressions.forEach(function(x){a=self.eval(x,e);});return a;
        case 'TemplateLiteral':a=[];n.quasis.forEach(function(q,i){a.push(q.value.cooked);if(i<n.expressions.length)a.push(self.eval(n.expressions[i],e));});return template(a);
        case 'MemberExpression':
            k=n.computed?this.eval(n.property,e):n.property.name;
            if(!n.computed&&['jsx','jsxs','Fragment','Component','createRef','useState','useRef','useMemo','useCallback','useEffect','useLayoutEffect'].indexOf(k)>=0&&n.object.type==='Identifier')return this.react[k];
            if(k==='COMMUNITY_CDN_ASSET_URL')return 'https://cdn.akamai.steamstatic.com/steamcommunity/public/assets/';
            if(k==='animated'&&n.object.type==='CallExpression')return this.tags;
            a=this.eval(n.object,e);if(n.optional&&a==null)return undefined;return this.get(a,k);
        case 'UnaryExpression':a=this.eval(n.argument,e);switch(n.operator){case '!':return !a;case '-':return this.binary('*',-1,a);case '+':return +a;case '~':return ~a;case 'void':return undefined;case 'typeof':return a&&a.fn?'function':typeof a;default:fail('unary '+n.operator);}
        case 'BinaryExpression':return this.binary(n.operator,this.eval(n.left,e),this.eval(n.right,e));
        case 'LogicalExpression':a=this.eval(n.left,e);return n.operator==='&&'?(a?this.eval(n.right,e):a):n.operator==='||'?(a?a:this.eval(n.right,e)):(a==null?this.eval(n.right,e):a);
        case 'ConditionalExpression':return this.eval(this.eval(n.test,e)?n.consequent:n.alternate,e);
        case 'AssignmentExpression':a=this.eval(n.right,e);if(n.operator!=='=')a=this.binary(n.operator.slice(0,-1),this.eval(n.left,e),a);return this.assign(n.left,e,a);
        case 'UpdateExpression':a=this.eval(n.argument,e);b=this.binary(n.operator==='++'?'+':'-',a,1);this.assign(n.argument,e,b);return n.prefix?b:a;
        case 'CallExpression':case 'NewExpression':
            a=[];n.arguments.forEach(function(v){if(v.type==='SpreadElement')a=a.concat(self.eval(v.argument,e));else a.push(self.eval(v,e));});
            // Bundler CSS exports are getters, not arbitrary module initialization.
            if(n.type==='CallExpression'&&n.arguments.length===1&&n.arguments[0].type==='ObjectExpression'&&Object.keys(a[0]).length&&Object.keys(a[0]).every(function(k){return a[0][k]&&a[0][k].fn;}))return {getters:a[0]};
            // Adapt react-spring's fixed-duration scalar to a native time expression.
            if(a[0]&&a[0].config&&typeof a[0].config.duration==='number'&&a[0].from){b=object();Object.keys(a[0].from).forEach(function(k){if(typeof a[0][k]!=='number')fail('spring value');b[k]={expression:'lerp',args:[a[0].from[k],a[0][k],{expression:'progress',args:[a[0].config.duration]}]};});return b;}
            b=null;if(n.callee.type==='MemberExpression'){k=n.callee.computed?this.eval(n.callee.property,e):n.callee.property.name;
                if(n.callee.object.type==='Identifier'&&n.callee.object.name==='console')return undefined;
                // React/runtime members are intercepted without importing the application.
                if(['jsx','jsxs','useState','useRef','useMemo','useCallback','useEffect','useLayoutEffect','createRef'].indexOf(k)>=0)k=this.react[k];
                else {b=this.eval(n.callee.object,e);if(n.optional&&b==null)return undefined;k=this.get(b,k);}
            }else k=this.eval(n.callee,e);
            if(n.type==='NewExpression')return this.construct(k,a);
            if(k&&own(k,'superclass')){if(k.superclass)this.initialize(k.superclass,this.currentThis,a);return undefined;}
            return this.invoke(k,b,a);
        case 'ClassExpression':case 'ClassDeclaration':
            a={klass:true,base:null,methods:object(),fields:[],static:object(),scope:e,ctor:null};
            if(n.id){e=new Scope(e);e.values[n.id.name]=a;a.scope=e;}
            if(n.superClass){b=this.eval(n.superClass,e);if(b!==this.react.Component)a.base=b;}
            n.body.body.forEach(function(p){var key=p.computed?self.eval(p.key,e):(p.key.name||p.key.value);
                if(p.type==='MethodDefinition'){var f=self.fn(p.value,e);if(key==='constructor')a.ctor=f;else if(p.static)a.static[key]=f;else a.methods[key]=f;}
                else if(p.type==='PropertyDefinition'){if(p.static)a.static[key]=self.eval(p.value,e);else a.fields.push({key:key,node:p.value});}
                else fail('class member');});return a;
        default:fail('expression '+n.type);
        }
    };
    Program.prototype.initialize=function(c,o,args){
        if(!c)return;var self=this,prior=this.currentThis,pc=this.currentClass;this.currentThis=o;this.currentClass=c;
        try{c.fields.forEach(function(p){o[p.key]=self.eval(p.node,c.scope);});if(c.ctor)this.invoke(c.ctor,o,args);else if(c.base)this.initialize(c.base,o,args);}finally{this.currentThis=prior;this.currentClass=pc;}
    };
    Program.prototype.construct=function(c,args){
        if(c===RegExp)return new RegExp(args[0],args[1]);if(c===Error)return Error(args[0]);if(!c||!c.klass)fail('constructor');
        var o={instance:c,props:args[0],state:{},setState:function(v){Object.keys(v).forEach(function(k){o.state[k]=v[k];});}};
        this.initialize(c,o,args);return o;
    };
    Program.prototype.exec=function(n,e){
        this.tick();var self=this,i,v,body=function(){try{self.exec(n.body,e);return true;}catch(r){if(r&&r.control==='break')return false;if(r&&r.control==='continue')return true;throw r;}};
        switch(n.type){
        case 'BlockStatement':e=new Scope(e);n.body.forEach(function(s){if(s.type==='FunctionDeclaration')e.values[s.id.name]=self.fn(s,e);});n.body.forEach(function(s){if(s.type!=='FunctionDeclaration')self.exec(s,e);});return;
        case 'ExpressionStatement':this.eval(n.expression,e);return;
        case 'VariableDeclaration':n.declarations.forEach(function(d){self.bind(d.id,self.eval(d.init,e),e);});return;
        case 'ReturnStatement':throw {control:'return',value:this.eval(n.argument,e)};
        case 'IfStatement':if(this.eval(n.test,e))this.exec(n.consequent,e);else if(n.alternate)this.exec(n.alternate,e);return;
        case 'ForStatement':e=new Scope(e);if(n.init)n.init.type==='VariableDeclaration'?this.exec(n.init,e):this.eval(n.init,e);while(!n.test||this.eval(n.test,e)){if(!body())break;if(n.update)this.eval(n.update,e);}return;
        case 'WhileStatement':while(this.eval(n.test,e))if(!body())break;return;
        case 'ForOfStatement':case 'ForInStatement':v=this.eval(n.right,e);v=n.type==='ForInStatement'?Object.keys(v):v;e=new Scope(e);for(i=0;i<v.length;i++){if(n.left.type==='VariableDeclaration')this.bind(n.left.declarations[0].id,v[i],e);else this.assign(n.left,e,v[i]);if(!body())break;}return;
        case 'SwitchStatement':v=this.eval(n.discriminant,e);var active=false;try{for(i=0;i<n.cases.length;i++){var c=n.cases[i];if(!c.test||this.eval(c.test,e)===v)active=true;if(active)c.consequent.forEach(function(s){self.exec(s,e);});}}catch(r){if(!r||r.control!=='break')throw r;}return;
        case 'BreakStatement':throw {control:'break'};
        case 'ContinueStatement':throw {control:'continue'};
        case 'ThrowStatement':throw this.eval(n.argument,e);
        case 'EmptyStatement':return;
        default:fail('statement '+n.type);
        }
    };
    Program.prototype.setup=function(){
        var p=this;function call(f){return p.invoke(f,null,[]);}
        var math=object();Object.getOwnPropertyNames(Math).forEach(function(k){math[k]=Math[k];});math.random=function(){return p.random();};
        // Reflection APIs must not expose Function or bypass the member-access restrictions.
        var objects={keys:Object.keys,values:Object.values,entries:Object.entries,assign:function(target){
            for(var i=1;i<arguments.length;i++){var value=arguments[i];Object.keys(value||{}).forEach(function(k){if(k==='__proto__'||k==='prototype'||k==='constructor')fail('prototype write');target[k]=value[k];});}return target;
        },create:function(proto){if(proto!==null)fail('object prototype');return object();}};
        this.globals={undefined:undefined,NaN:NaN,Infinity:Infinity,Math:math,Number:Number,String:String,Boolean:Boolean,Array:Array,Object:objects,RegExp:RegExp,Error:Error,parseInt:parseInt,parseFloat:parseFloat,isNaN:isNaN,isFinite:isFinite,Date:{now:function(){return p.now;}}};
        this.tags={div:'div',img:'img',svg:'svg',path:'path',rect:'rect',ellipse:'ellipse',circle:'circle',g:'g',polygon:'polygon'};
        function rect(){return {width:p.width,height:p.height,offsetWidth:p.width,offsetHeight:p.height,getBoundingClientRect:function(){return {width:p.width,height:p.height};}};}
        function vnode(type,props){return {vnode:true,type:type,props:props||{}};}
        this.react={jsx:vnode,jsxs:vnode,Fragment:'fragment',Component:{component:true},createRef:function(){return {current:rect()};},useRef:function(v){return {current:v};},
            useState:function(v){return [v&&v.fn?call(v):v,function(){}];},useMemo:call,useCallback:function(f){return f;},useEffect:function(){},useLayoutEffect:function(){}};
    };
    Program.prototype.expand=function(v,delay,depth){
        this.tick();if(depth>40)fail('component depth');var self=this;
        if(v==null||v===false||v===true||typeof v==='string'||typeof v==='number')return [];
        if(Array.isArray(v)){var all=[];v.forEach(function(x){all=all.concat(self.expand(x,delay,depth));});return all;}
        if(!v.vnode)fail('render value');var type=v.type,props=v.props;
        if(typeof props.msDelay==='number'&&props.children)return this.expand(props.children,delay+props.msDelay,depth+1);
        if(type&&type.klass){var instance=this.construct(type,[props]),mount=this.get(instance,'componentDidMount');if(mount)this.invoke(mount,instance,[]);return this.expand(this.invoke(this.get(instance,'render'),instance,[]),delay,depth+1);}
        if(type&&type.fn)return this.expand(this.invoke(type,null,[props]),delay,depth+1);
        if(type==='fragment')return this.expand(props.children,delay,depth+1);
        if(['div','img','svg','path','rect','ellipse','circle','g','polygon'].indexOf(type)<0)fail('element '+type);
        var output={tag:type,props:object(),delay:delay,children:this.expand(props.children,delay,depth+1)};
        Object.keys(props).forEach(function(k){if(['children','ref','key'].indexOf(k)<0){if(typeof props[k]==='function'||props[k]&&props[k].fn)fail('event handler');output.props[k]=props[k];}});return [output];
    };
    Program.prototype.prepare=function(){
        this.setup();var main=this.module(this.main),text=this.files[this.main],match,registry=null,re=/this\.m_ChatRoomEffectSettings=([\w\x24]+)/g;
        var self=this,cached=this.cached&&this.cached.prepared,bounds=[];
        if(cached){
            registry=cached.registry;bounds=cached.bounds;
            if(!own(main.defs,registry)||!Array.isArray(bounds)||!cached.catalog||!cached.queues||!cached.limits)fail('cached effect registry');
        }else{
            while((match=re.exec(text)))if(main.defs[match[1]])registry=match[1];if(!registry)fail('effect registry');
            // Adapt the bounds hook, by its DOM measurement contract rather than minified name.
            Object.keys(main.defs).forEach(function(k){var d=main.defs[k];if(d.source.length<500&&d.source.indexOf('getBoundingClientRect')>=0&&d.source.indexOf('useState')>=0)bounds.push(k);});
        }
        bounds.forEach(function(k){if(!own(main.defs,k))fail('cached bounds hook');main.scope.values[k]=function(){return [{width:self.width,height:self.height},function(){}];};});
        if(cached){
            this.effects=this.lookup(main.scope,registry);this.queues=cached.queues;this.limits=cached.limits;this.catalog=cached.catalog;this.prepared=cached;
            if(Object.keys(this.effects).sort().join(',')!==Object.keys(this.catalog).sort().join(','))fail('cached effect names');
            return {catalog:this.catalog,limits:this.limits};
        }
        this.effects=this.lookup(main.scope,registry);this.queues=null;this.limits=null;
        Object.keys(main.defs).forEach(function(k){var d=main.defs[k];if(d.source.indexOf('static k_QueueForEffect=')>=0||d.source.indexOf('static k_MaxRoomEffectRunning=')>=0){var n=self.ast(d);n.body.body.forEach(function(p){if(p.static&&p.key.name==='k_QueueForEffect')self.queues=self.eval(p.value,main.scope);if(p.static&&p.key.name==='k_MaxRoomEffectRunning')self.limits=self.eval(p.value,main.scope);});}});
        if(!this.queues||!this.limits)fail('queue definitions');var catalog=object();
        Object.keys(this.effects).forEach(function(name){var d=self.effects[name];if(!d||typeof d.timeout!=='number'||!d.render||!d.render.fn||!self.queues[name])fail('effect definition '+name);
            var icon=self.expand(self.invoke(d.renderEffectIcon,null,[]),0,0);catalog[name]={duration:d.timeout,queue:self.queues[name],icon:icon[0]&&icon[0].props.src||'',buttonToken:d.buttonToken,messageToken:d.locToken};});
        this.catalog=catalog;this.prepared={registry:registry,bounds:bounds,queues:this.queues,limits:this.limits,catalog:catalog};return {catalog:catalog,limits:this.limits};
    };
    Program.prototype.labels=function(url){
        this.steps=0;var m=this.module(url),strings=this.lookup(m.scope,m.exports.default),out={},self=this;
        Object.keys(this.catalog).forEach(function(name){var entry=self.catalog[name];out[name]={title:self.get(strings,entry.buttonToken.replace(/^#/,''))||name,message:self.get(strings,entry.messageToken.replace(/^#/,''))||''};});return out;
    };
    Program.prototype.scene=function(name,width,height,seed){
        this.steps=0;this.width=width;this.height=height;this.seed=seed>>>0;this.now=seed;var d=this.effects[name];if(!d)fail('unknown effect');
        var effect={name:name,timestamp:this.now,settings:d,expires:this.now+d.timeout};
        var nodes=this.expand(this.invoke(d.render,null,[effect]),0,0);return {name:name,duration:d.timeout,width:width,height:height,nodes:nodes};
    };
    Program.prototype.snapshot=function(){
        var modules=object(),self=this;
        Object.keys(this.modules).forEach(function(url){
            var m=self.modules[url];
            // Short string constants often name CSS classes behind random branches not hit by validation.
            Object.keys(m.defs).forEach(function(name){var d=m.defs[name];if(!d.ast&&d.source.length<=512&&/^[`'"]/.test(d.source))self.ast(d);});
            modules[url]={defs:m.defs,imports:m.imports,exports:m.exports};
        });
        return {modules:modules,prepared:this.prepared};
    };
    return {create:function(files,main,cached){var p=new Program(files,main,cached);p.prepare();return p;}};
})();
""".trimIndent()
}
