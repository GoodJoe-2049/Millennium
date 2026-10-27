package steam.nova.app.features.profile.betterprofile

import android.content.Context
import org.json.JSONObject
import steam.nova.app.R
import steam.nova.app.core.ModuleLocalization
import steam.nova.app.features.profile.customization.SteamProfileCustomizationPage
import steam.nova.app.features.profile.customization.SteamProfileCustomizationScript

/** Bundle-gated React adapters. Preserve Steam's WebView props, navigation, cookies and user agent. */
internal object SteamBetterProfileScript {
    fun source(context: Context, betterProfileEnabled: Boolean = true, customizationBridgePrefix: String? = null): String {
        val labels = JSONObject()
            .put("title", ModuleLocalization.text(context, R.string.profile_sidebar_title))
            .put("open", ModuleLocalization.text(context, R.string.profile_sidebar_open))
            .put("close", ModuleLocalization.text(context, R.string.profile_sidebar_close))
        val page = JSONObject.quote(SteamBetterProfilePage.source())
        val customization = if (customizationBridgePrefix != null) SteamProfileCustomizationScript.configuration(context) else JSONObject()
        val customizationPage = JSONObject.quote(if (customizationBridgePrefix != null) SteamProfileCustomizationPage.source(context) else "")
        val customizationPrefix = JSONObject.quote(customizationBridgePrefix ?: "")
        return $$"""
    var betterProfileLabels=$$labels,betterProfilePage=$$page;
    var profileLayoutEnabled=$$betterProfileEnabled,profileCustomization=$$customization,profileCustomizationPage=$$customizationPage,profileCustomizationPrefix=$$customizationPrefix;
    var betterProfileSessions=new WeakMap(),betterProfileListeners=new Set();
    function betterProfileEmit(){betterProfileListeners.forEach(function(fn){fn();});}
    function betterProfileURL(url){return typeof url==='string'&&/^https:\/\/steamcommunity\.com\/(?:id\/[^/?#]+|profiles\/[0-9]{17}|my)\/?(?:[?#].*)?$/.test(url);}
    function betterProfileKey(url){return betterProfileURL(url)?url.split(/[?#]/)[0].replace(/\/$/,''):'';}
    function betterProfileCommand(session,command){
        if(!session||!session.ref||!betterProfileURL(session.url))return;
        session.ref.injectJavaScript('(function(){var p=window.__steamNovaBetterProfile;if(p&&p.token==='+JSON.stringify(session.token)+')p.'+command+'();})();true;');
    }
    function profileCustomizationCommand(session,command){
        if(!session||!session.ref||!betterProfileURL(session.url))return;
        session.ref.injectJavaScript('(function(){var p=window.__steamNovaProfileCustomization;if(p&&p.token==='+JSON.stringify(session.token)+')p.'+command+'();})();true;');
    }
    function profilePageScript(session){
        var config=JSON.stringify({token:session.token,labels:betterProfileLabels});
        var source=profileLayoutEnabled?';('+betterProfilePage+')('+config+');':'';
        if(profileCustomizationPrefix)source+=';('+profileCustomizationPage+')('+JSON.stringify(Object.assign({},profileCustomization,{token:session.token}))+');';
        return source+'true;';
    }
    function betterProfileWebRuntime(base,require){
        var R=require(115),wrappers=new WeakMap();
        function wrapper(Type){
            var cached=wrappers.get(Type);if(cached)return cached;
            var Wrapped=R.forwardRef(function(props,forwardedRef){
                var local=R.useRef(null);
                if(!local.current)local.current={token:'sn-bp-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2),ref:null,url:'',ready:false,open:false,customReady:false,customOpen:false,generation:0,pending:new Set()};
                var session=local.current;
                var setRef=R.useCallback(function(value){
                    if(session.ref)betterProfileSessions.delete(session.ref);session.ref=value;
                    if(value)betterProfileSessions.set(value,session);
                    if(typeof forwardedRef==='function')forwardedRef(value);else if(forwardedRef)forwardedRef.current=value;
                    betterProfileEmit();
                },[forwardedRef,session]);
                function inject(){if(session.ref)session.ref.injectJavaScript(profilePageScript(session));}
                function reset(url){session.generation++;session.pending.clear();session.url=url||'';session.ready=false;session.open=false;session.customReady=false;session.customOpen=false;betterProfileEmit();}
                R.useEffect(function(){return function(){
                    if(session.ref)betterProfileSessions.delete(session.ref);session.ref=null;reset('');
                };},[session]);
                var script=profilePageScript(session);
                var changed=Object.assign({},props,{ref:setRef,
                    injectedJavaScriptBeforeContentLoaded:(props.injectedJavaScriptBeforeContentLoaded||'')+script,
                    injectedJavaScript:(props.injectedJavaScript||'')+script,
                    onLoadStart:function(event){reset(event.nativeEvent.url);if(props.onLoadStart)props.onLoadStart(event);},
                    onLoadEnd:function(event){if(props.onLoadEnd)props.onLoadEnd(event);session.url=event.nativeEvent.url||session.url;inject();},
                    onNavigationStateChange:function(state){
                        if(state.url!==session.url){reset(state.url);inject();}
                        if(props.onNavigationStateChange)props.onNavigationStateChange(state);
                    },
                    onMessage:function(event){
                        var data=null;try{data=JSON.parse(event.nativeEvent.data);}catch(ignore){}
                        if(data&&data.event_name==='SteamNovaProfileCustomization'){
                            if(!profileCustomizationPrefix||data.token!==session.token||!betterProfileURL(event.nativeEvent.url)||data.url!==event.nativeEvent.url)return;
                            if(betterProfileKey(session.url)!==betterProfileKey(data.url))return;
                            if(data.type==='state'){session.customReady=data.ready===true;session.customOpen=data.open===true;betterProfileEmit();return;}
                            if(data.type!=='request'||!data.args||!['load','save','catalog','bundle','games'].includes(data.args.operation)||
                                !/^[0-9]{1,10}$/.test(data.id)||!/^[0-9]{17}$/.test(data.args.profile)||session.pending.has(data.id))return;
                            var generation=session.generation,id=data.id,url=data.url;
                            function respond(result){
                                if(generation!==session.generation||!session.ref||session.url!==url)return;
                                session.pending.delete(id);
                                session.ref.injectJavaScript('(function(){var p=window.__steamNovaProfileCustomization;if(p&&p.token==='+JSON.stringify(session.token)+')p.receive('+JSON.stringify(id)+','+JSON.stringify(result)+');})();true;');
                            }
                            if(session.pending.size>=6){respond({ok:false,error:'BUSY'});return;}
                            var payload=JSON.stringify(data.args);if(payload.length>24000){respond({ok:false,error:'TOO_LARGE'});return;}
                            session.pending.add(id);
                            Promise.resolve().then(function(){return require(16).Linking.openURL(profileCustomizationPrefix+encodeURIComponent(payload));})
                                .then(function(raw){
                                    var result=JSON.parse(raw);
                                    // A completed save also updates native pages if this WebView has navigated away.
                                    if(result.ok&&data.args.operation==='save'&&result.data&&result.data.scheme)
                                        profileAvatarSaved(data.args.profile,result.data.scheme);
                                    respond(result);
                                }).catch(function(){respond({ok:false,error:'REQUEST_FAILED'});});
                            return;
                        }
                        if(data&&data.event_name==='SteamNovaBetterProfile'){
                            if(data.token===session.token&&betterProfileURL(event.nativeEvent.url)&&data.url===event.nativeEvent.url){
                                session.url=data.url;session.ready=data.ready===true;session.open=session.ready&&data.open===true;betterProfileEmit();
                            }
                            return;
                        }
                        if(props.onMessage)props.onMessage(event);
                    }
                });
                return R.createElement(Type,changed);
            });
            wrappers.set(Type,Wrapped);return Wrapped;
        }
        function convert(original,type,props,key){
            if(props&&props.source&&typeof props.source.uri==='string'&&props.onMessage&&props.injectedJavaScriptBeforeContentLoaded){
                return original(wrapper(type),props,key);
            }
            return original(type,props,key);
        }
        return facade(facade(base,'jsx',function(type,props,key){return convert(base.jsx,type,props,key);}),
            'jsxs',function(type,props,key){return convert(base.jsxs,type,props,key);});
    }
    function betterProfileHeader(base,require){
        var R=require(115),N=require(16);
        function Header(props){
            var tick=R.useState(0)[1];
            R.useEffect(function(){function update(){tick(function(n){return n+1;});}betterProfileListeners.add(update);update();
                return function(){betterProfileListeners.delete(update);};},[]);
            var view=props.refWebview&&props.refWebview.current,session=view&&betterProfileSessions.get(view);
            var same=!!(session&&betterProfileURL(props.url)&&betterProfileKey(session.url)===betterProfileKey(props.url));
            var active=same&&profileLayoutEnabled&&session.ready,customActive=same&&!!profileCustomizationPrefix&&session.customReady;
            var opened=(active&&session.open)||(customActive&&session.customOpen);
            function closePanels(){betterProfileCommand(session,'close');profileCustomizationCommand(session,'close');}
            R.useEffect(function(){
                if(!opened)return;
                var sub=N.BackHandler.addEventListener('hardwareBackPress',function(){
                    if(props.navigation&&props.navigation.isFocused&&!props.navigation.isFocused())return false;
                    closePanels();return true;
                });return function(){sub.remove();};
            },[opened,session,props.navigation]);
            R.useEffect(function(){
                if(!props.navigation||!props.navigation.addListener)return;
                return props.navigation.addListener('blur',function(){closePanels();});
            },[session,props.navigation]);
            if(!active&&!customActive)return R.createElement(base.Header,props);
            var changed=Object.assign({},props),left=props.renderLeftContent;
            changed.renderLeftContent=function(content){
                var originalLeft=left?left(content):content&&content.backButton;
                if(opened&&R.isValidElement(originalLeft)&&typeof originalLeft.props.onPress==='function')
                    originalLeft=R.cloneElement(originalLeft,{onPress:closePanels});
                if(!active)return originalLeft;
                return R.createElement(N.View,{style:{flexDirection:'row',alignItems:'center'}},
                    R.createElement(N.Pressable,{accessibilityRole:'button',accessibilityLabel:session.open?betterProfileLabels.close:betterProfileLabels.open,
                        accessibilityState:{expanded:session.open},onPress:function(){profileCustomizationCommand(session,'close');betterProfileCommand(session,'toggle');},
                        style:function(s){return {width:44,height:44,alignItems:'center',justifyContent:'center',opacity:s.pressed?.6:1};}},
                        R.createElement(N.View,{accessible:false,style:{width:20,gap:4}},[0,1,2].map(function(n){return R.createElement(N.View,{key:n,style:{height:2,width:20,borderRadius:1,backgroundColor:'#DFE3E6'}});}))),originalLeft);
            };
            if(customActive)changed.renderRightContent=function(){
                return R.createElement(N.Pressable,{accessibilityRole:'button',accessibilityLabel:profileCustomization.labels.title,
                    accessibilityState:{expanded:session.customOpen},onPress:function(){betterProfileCommand(session,'close');profileCustomizationCommand(session,'toggle');},
                    style:function(s){return {width:44,height:44,alignItems:'center',justifyContent:'center',opacity:s.pressed?.6:1};}},
                    R.createElement(N.Image,{source:{uri:profileCustomization.icon},accessible:false,style:{width:24,height:24},resizeMode:'contain'}));
            };
            return R.createElement(base.Header,changed);
        }
        return facade(base,'Header',Header);
    }
""".trimIndent()
    }
}
