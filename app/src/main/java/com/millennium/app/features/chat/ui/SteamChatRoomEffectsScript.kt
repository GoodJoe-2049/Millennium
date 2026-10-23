package com.millennium.app.features.chat.ui

/** Native cards and a tagged Canvas slot. All rendering rules come from the official resource adapter. */
internal object SteamChatRoomEffectsScript {
    fun source() = """
    function chatRoomEffects(env,ui){
        if(env.roomEffects)return env.roomEffects;
        var R=env.React,N=env.RN,h=R.createElement,t=ui.t,catalog={},listeners=[],player=null,metadataRequests={};
        function emit(){listeners.slice().forEach(function(fn){fn();});}
        function useUpdate(){var tick=R.useState(0)[1];R.useEffect(function(){function update(){tick(function(n){return n+1;});}listeners.push(update);update();
            return function(){listeners=listeners.filter(function(fn){return fn!==update;});};},[]);}
        function parse(body){
            var match=/^\/roomeffect\s+([A-Za-z0-9_-]+)\s*$/i.exec((body||'').trim());if(match)return match[1];
            match=/^\[roomeffect\b([^\]]*)\](?:([\s\S]*?)\[\/roomeffect\])?\s*$/i.exec((body||'').trim());if(!match)return null;
            var type=/(?:^|\s)type=(?:"([^"]+)"|'([^']+)'|([^\s\]]+))/i.exec(match[1]);
            var name=type&&(type[1]||type[2]||type[3]);return name&&/^[A-Za-z0-9_-]+$/.test(name)?name:null;
        }
        function metadata(force,account){
            var language=moduleI18n.steamLanguage,key=account+':'+language;
            if(metadataRequests[key])return metadataRequests[key];
            metadataRequests[key]=env.request('roomEffectsCatalog',{language:language,force:!!force},account).then(function(data){
                catalog=data.catalog||{};emit();return catalog;
            }).then(function(value){delete metadataRequests[key];return value;},function(error){delete metadataRequests[key];throw error;});return metadataRequests[key];
        }
        function Card(props){
            useUpdate();var cooldown=R.useState(false),busy=R.useState(false),error=R.useState(false),timer=R.useRef(null),alive=R.useRef(true),meta=catalog[props.name]||{};
            R.useEffect(function(){alive.current=true;return function(){alive.current=false;clearTimeout(timer.current);};},[]);
            function replay(){if(!player||!player.ready||player.key!==props.targetKey||busy[0]||cooldown[0])return;busy[1](true);error[1](false);
                player.play(props.name,true).then(function(ok){if(!alive.current)return;if(!ok){error[1](true);return;}cooldown[1](true);
                    timer.current=setTimeout(function(){if(alive.current)cooldown[1](false);},5000);
                },function(){if(alive.current)error[1](true);}).then(function(){if(alive.current)busy[1](false);});}
            var title=meta.message?meta.message.replace(/%1\x24s|%s/g,function(){return props.sender||'';}):
                (props.sender?props.sender+' · ':'')+(meta.title||props.name);
            var preparing=player&&player.key===props.targetKey&&player.status==='loading';
            return h(N.View,{style:{minHeight:44,flexDirection:'row',alignItems:'center',gap:8,flexWrap:'wrap',paddingVertical:6}},
                meta.icon?h(N.Image,{source:{uri:meta.icon},style:{width:30,height:30},resizeMode:'contain',fadeDuration:0}):null,
                ui.label(title,{flexGrow:1,flexShrink:1,fontSize:14,color:'#dcdedf'}),
                h(ui.Button,{title:preparing?t('chat_effects_preparing'):busy[0]?t('chat_effect_playback_preparing'):cooldown[0]?t('chat_effect_replay_shortly'):t('chat_effect_replay'),disabled:busy[0]||cooldown[0]||props.disabled||!player||!player.ready||player.key!==props.targetKey,onPress:replay}),
                error[0]?ui.label(t('chat_effect_unavailable'),{fontSize:12,color:'#a7b0bc'}):null);
        }
        function Layer(props){
            var status=R.useState('loading'),version=R.useState(0),app=R.useState(!N.AppState.currentState||N.AppState.currentState==='active');
            var allowed=!!props.focused&&app[0],account=env.currentAccount();
            // Every mounted session owns a distinct native tag. Late replies cannot affect its successor.
            var session=R.useMemo(function(){return {owner:'effect-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2),
                account:account,key:props.target.key,active:false,layout:false,ready:false,attaching:false,status:'loading',pending:[]};},[account,props.target.key,allowed,version[0]]);
            function request(op,args){return env.request(op,Object.assign({owner:session.owner,key:session.key},args||{}),session.account);}
            function submit(name,replay){return request('roomEffectsPlay',{name:name,replay:!!replay}).then(function(result){return !!result.accepted;});}
            function play(name,replay){
                if(!session.active||env.currentAccount()!==session.account)return Promise.resolve(false);
                if(!session.ready){if(replay)return Promise.resolve(false);
                    session.pending=session.pending.filter(function(p){return Date.now()-p.time<15000;});
                    if(session.pending.length<32)session.pending.push({name:name,time:Date.now()});return Promise.resolve(true);}
                if(!catalog[name])return Promise.resolve(false);
                return submit(name,replay);
            }
            function attach(){
                if(!session.active||!session.layout||session.ready||session.attaching)return;
                session.attaching=true;
                request('roomEffectsAttach').then(function(){if(!session.active)return;return metadata(version[0]>0,session.account);}).then(function(){
                    if(!session.active)return;session.ready=true;session.attaching=false;session.status='ready';status[1]('ready');emit();var queue=session.pending;session.pending=[];
                    queue.forEach(function(item){if(Date.now()-item.time<15000&&catalog[item.name])submit(item.name,false).catch(function(){});});
                },function(){if(session.active){session.attaching=false;session.status='error';status[1]('error');emit();}});
            }
            R.useEffect(function(){var sub=N.AppState.addEventListener('change',function(value){app[1](value==='active');});return function(){sub.remove();};},[]);
            R.useLayoutEffect(function(){
                status[1]('loading');
                if(!allowed)return;
                session.active=true;session.play=play;player=session;emit();attach();
                var unsubscribe=env.subscribeRoomEffects(function(message){if(message.key===session.key){var name=parse(message.text);if(name)play(name,false).catch(function(){});}});
                return function(){unsubscribe();session.active=false;session.ready=false;session.pending=[];
                    if(player===session){player=null;emit();}request('roomEffectsDetach').catch(function(){});};
            },[session]);
            if(!allowed)return null;
            return h(N.View,{pointerEvents:'box-none',style:{position:'absolute',left:0,right:0,top:0,bottom:0}},
                h(N.View,{key:session.owner,collapsable:false,testID:'millennium.chat.effects.'+session.owner,
                    pointerEvents:'none',accessible:false,importantForAccessibility:'no-hide-descendants',onLayout:function(){session.layout=true;attach();},
                    style:{position:'absolute',left:0,right:0,top:0,bottom:0,backgroundColor:'transparent'}}),
                status[0]!=='ready'?h(N.View,{pointerEvents:status[0]==='error'?'auto':'none',style:{position:'absolute',right:8,top:8,backgroundColor:'#29323f'}},
                    status[0]==='error'?h(ui.Button,{title:t('chat_effects_retry'),onPress:function(){version[1](function(n){return n+1;});}}):
                        ui.label(t('chat_effects_loading'),{fontSize:12,color:'#a7b0bc',paddingHorizontal:12,paddingVertical:10})):null);
        }
        env.roomEffects={parse:parse,Card:Card,Layer:Layer,metadata:function(name){return catalog[name];},useUpdate:useUpdate};return env.roomEffects;
    }
""".trimIndent()
}
