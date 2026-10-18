package com.millennium.app.features.chat.ui

internal object SteamChatMediaScript {
    fun source() = """
    function chatMedia(env,ui){
        var R=env.React,N=env.RN,h=R.createElement,t=ui.t,label=ui.label,Button=ui.Button,colors=ui.colors;
        function safe(url){return /^https:\/\/(?:[a-z0-9-]+\.)*(?:steamusercontent\.com|steamcontent\.com|steamstatic\.com|akamaihd\.net|steamcommunity\.com)\//i.test(url)?url:null;}
        function openLink(url){if(/^https?:\/\/[^\s<>]+$/i.test(url))N.Linking.openURL(url).catch(function(){});}
        function attrs(raw){var result={},re=/([a-z_]+)=(?:"([^"]*)"|'([^']*)'|([^\s\]]+))/gi,m;while((m=re.exec(raw||''))!==null)result[m[1].toLowerCase()]=(m[2]||m[3]||m[4]||'').replace(/&amp;/g,'&');return result;}
        function MediaImage(props){
            var fail=R.useState(false),uri=safe(props.uri);if(!uri||fail[0])return label(t('图片无法加载','Image unavailable'),{color:'#a7b0bc'});
            return h(N.Pressable,{accessibilityRole:'button',accessibilityLabel:t('查看图片','View image'),onPress:function(){props.open(uri);}},
                h(N.Image,{source:{uri:uri},resizeMode:'contain',onError:function(){fail[1](true);},style:{width:props.small?128:220,height:props.small?128:180,maxWidth:'100%'}}));
        }
        function Spoiler(props){var revealed=R.useState(false);return revealed[0]?h(RichText,{text:props.text,open:props.open,depth:props.depth}):
            h(N.Pressable,{onPress:function(){revealed[1](true);},accessibilityRole:'button',style:{backgroundColor:'#39414d',padding:18,borderRadius:4}},label(t('剧透内容 · 点击查看','Spoiler · Tap to reveal'),{color:'#b7bec8'}));}
        var gameMetadata={},gameRequests={},gameFailures={};
        function gameInfo(app,language){
            var key=language+':'+app;
            if(gameMetadata[key])return Promise.resolve(gameMetadata[key]);
            if(gameRequests[key])return gameRequests[key];
            if(gameFailures[key]&&Date.now()-gameFailures[key]<30000)return Promise.resolve(null);
            gameRequests[key]=env.request('gameInfo',{appId:app,language:language}).then(function(value){
                if(value&&value.appId===app){gameMetadata[key]=value;var keys=Object.keys(gameMetadata);if(keys.length>128)delete gameMetadata[keys[0]];return value;}return null;
            }).catch(function(){gameFailures[key]=Date.now();var keys=Object.keys(gameFailures);if(keys.length>128)delete gameFailures[keys[0]];return null;})
                .then(function(value){delete gameRequests[key];return value;});return gameRequests[key];
        }
        function Invite(props){
            var a=props.attributes||{},app=String(a.appid||a.app_id||a.gameid||a.game_id||''),lobby=a.lobbyid||a.lobby_id||a.lobby||'',inviter=a.steamid||a.steamid64||a.steam_id||a.steamid_inviter||a.inviter||'';
            var valid=/^[0-9]{1,10}$/.test(app)&&Number(app)>0&&Number(app)<=4294967295;
            if(valid)app=String(Number(app));
            var language=t('schinese','english'),key=language+':'+app,info=R.useState(function(){return {key:key,value:gameMetadata[key]};}),failed=R.useState({key:key,index:0}),c=colors();
            R.useEffect(function(){
                var active=true;info[1]({key:key,value:gameMetadata[key]});failed[1]({key:key,index:0});
                if(valid)gameInfo(app,language).then(function(value){if(active)info[1]({key:key,value:value});});
                return function(){active=false;};
            },[key,valid]);
            var value=info[0].key===key?info[0].value:null,artwork=value&&Array.isArray(value.artwork)?value.artwork:[],index=failed[0].key===key?failed[0].index:0,uri=safe(artwork[index]||'');
            var fallback=(props.title||a.name||a.title||'').trim();
            if(/^(?:steam game|steam game invit(?:ation|e)|game invit(?:ation|e)|join(?: game)?|游戏邀请|加入游戏|Steam 游戏|App(?: ID)? \d+)$/i.test(fallback)||/steam:\/\//i.test(fallback))fallback='';
            var title=(value&&value.name)||fallback||t('游戏信息暂不可用','Game details unavailable');
            var join=valid&&/^[0-9]{1,20}$/.test(lobby)&&(!inviter||/^7656119\d{10}$/.test(inviter));
            function openInvite(){N.Alert.alert(t('游戏邀请','Game invitation'),t('使用 Steam 打开这条游戏邀请？','Open this game invitation with Steam?'),[
                {text:t('取消','Cancel'),style:'cancel'},{text:t('打开','Open'),onPress:function(){N.Linking.openURL('steam://joinlobby/'+app+'/'+lobby+(inviter?'/'+inviter:'')).catch(function(){});}}]);}
            return h(N.View,{style:{backgroundColor:c.panel||'#26374a',borderColor:c.line||'#343944',borderWidth:1,borderRadius:10,padding:12,width:320,maxWidth:'100%'}},
                h(join?N.Pressable:N.View,{onPress:join?openInvite:undefined,accessibilityRole:join?'button':undefined,accessibilityLabel:join?t('打开游戏邀请','Open game invitation'):undefined,
                    style:{flexDirection:'row',alignItems:'center'}},
                    h(N.View,{style:{width:'48%',maxWidth:144,aspectRatio:1.6,flexShrink:0,borderRadius:6,overflow:'hidden',backgroundColor:c.bg||'#1b1e24',alignItems:'center',justifyContent:'center'}},
                        uri?h(N.Image,{source:{uri:uri},resizeMode:'contain',fadeDuration:0,accessibilityLabel:title,
                            onError:function(){failed[1]({key:key,index:index+1});},style:{width:'100%',height:'100%'}}):label(t('游戏封面','Game artwork'),{color:c.muted||'#a7b0bc',fontSize:12,textAlign:'center',padding:6})),
                    h(N.View,{style:{flex:1,minWidth:0,marginLeft:16}},
                        label(t('游戏邀请','Game invitation'),{color:c.blue||'#66c0f4',fontSize:12,fontWeight:'500'}),
                        label(title,{fontSize:14,lineHeight:20,minHeight:40,fontWeight:'500',paddingTop:4},{numberOfLines:2}),
                        valid?label('App ID '+app,{fontSize:11,color:c.muted||'#a7b0bc',paddingTop:4}):null)),
                valid?h(N.Pressable,{onPress:function(){openLink('https://store.steampowered.com/app/'+app+'/');},accessibilityRole:'button',accessibilityLabel:t('查看商店','View store'),
                    style:function(pressed){return {alignSelf:'flex-end',minHeight:48,minWidth:112,marginTop:6,justifyContent:'center',opacity:pressed.pressed?0.65:1};}},
                    h(N.View,{style:{minHeight:32,paddingVertical:6,paddingHorizontal:14,borderRadius:4,backgroundColor:'#1a6da0',alignItems:'center',justifyContent:'center'}},
                        label(t('查看商店','View store'),{color:'#fff',fontSize:13}))):null);
        }
        function FileCard(props){var saving=R.useState(false),error=R.useState('');
            return h(N.View,{style:{padding:12,backgroundColor:'#29323f',borderRadius:4,maxWidth:'100%'}},
                label(props.title||t('附件','Attachment'),{fontWeight:'500'}),
                h(N.View,{style:{flexDirection:'row'}},h(Button,{title:t('打开','Open'),onPress:function(){openLink(props.url);}}),
                    safe(props.url)?h(Button,{title:saving[0]?t('正在保存…','Saving…'):t('保存','Save'),disabled:saving[0],onPress:function(){saving[1](true);error[1]('');env.request('saveMedia',{url:props.url}).then(function(){error[1](t('已保存','Saved'));}).catch(function(){error[1](t('保存失败','Could not save'));}).then(function(){saving[1](false);});}}):null),
                error[0]?label(error[0],{fontSize:12,color:'#a7b0bc'}):null);
        }
        function RichText(props){
            var body=props.text||'',m;
            if((props.depth||0)>8)return label(body,{lineHeight:22},{selectable:true});
            if((m=/^\[spoiler(?:=[^\]]+)?\]([\s\S]*)\[\/spoiler\]$/i.exec(body.trim())))return h(Spoiler,{text:m[1],open:props.open,depth:(props.depth||0)+1});
            if((m=/^\/(?:me)\s+([\s\S]+)/i.exec(body)))return label(m[1],{fontStyle:'italic',color:'#b6c7da',lineHeight:22},{selectable:true});
            if((m=/^\/(sticker|roomeffect)\s+([^\s]+)\s*$/i.exec(body)))return m[1].toLowerCase()==='sticker'?
                h(MediaImage,{uri:'https://steamcommunity.com/economy/sticker/'+encodeURIComponent(m[2]),small:true,open:props.open}):
                label(t('聊天特效：','Chat effect: ')+m[2],{color:'#66c0f4',fontStyle:'italic',paddingVertical:8});
            if((m=/^\[(sticker|roomeffect|emoticon)([^\]]*)\](?:([\s\S]*?)\[\/\1\])?\s*$/i.exec(body.trim()))){
                var a=attrs(m[2]),name=a.type||a.name||m[3]||'';name=name.replace(/^:|:$/g,'');
                if(m[1].toLowerCase()==='emoticon')body=':'+name+':';else return h(RichText,{text:'/'+m[1].toLowerCase()+' '+name,open:props.open,depth:(props.depth||0)+1});
            }
            if((m=/^\[(gameinvite|lobbyinvite)([^\]]*)\](?:([\s\S]*?)\[\/\1\])?\s*$/i.exec(body.trim())))return h(Invite,{attributes:attrs(m[2]),title:m[3]});
            if((m=/^steam:\/\/joinlobby\/(\d+)\/(\d+)\/(\d+)\/?$/i.exec(body.trim())))return h(Invite,{attributes:{appid:m[1],lobbyid:m[2],steamid:m[3]}});
            if((m=/^https?:\/\/store\.steampowered\.com\/app\/(\d+)(?:\/[^\s]*)?$/i.exec(body.trim())))return h(Invite,{attributes:{appid:m[1]},title:t('Steam 游戏','Steam game')});
            if((m=/^\[steamstore([^\]]*)\]([\s\S]*?)\[\/steamstore\]$/i.exec(body.trim()))){var store=attrs(m[1]),app=store.appid||store.app_id||(/^\d+$/.test(m[2].trim())?m[2].trim():'');var link=/\/app\/(\d+)/.exec(m[2]);if(!app&&link)app=link[1];if(app)return h(Invite,{attributes:{appid:app},title:store.title||store.name||t('Steam 游戏','Steam game')});}
            if((m=/^\[url=(?:"([^"]+)"|([^\]]+))\]([\s\S]*?)\[\/url\]$/i.exec(body.trim()))){var link=m[1]||m[2],store=/^https?:\/\/store\.steampowered\.com\/app\/(\d+)/i.exec(link);if(store)return h(Invite,{attributes:{appid:store[1]},title:m[3]});if(/\.(?:mp4|webm|mpg|mpeg|ogv|zip)(?:\?|$)/i.test(link))return h(FileCard,{url:link,title:m[3]});}
            if((m=/^\[(?:video|file|attachment)([^\]]*)\]([\s\S]*?)(?:\[\/(?:video|file|attachment)\])?$/i.exec(body.trim()))){
                var info=attrs(m[1]),url=info.src||info.url||m[2];if(/^https:\/\//i.test(url))return h(FileCard,{url:url,title:info.name||info.filename||t('附件','Attachment')});
            }
            if((m=/^\[(tradeoffer|trade_offer|incomingtradeoffer|gift|giftreceived|giftnotification|friendinvite|friendrequest|groupinvite|claninvite|broadcastinvite|broadcastviewrequest|playtestinvite|remoteplayinvite|inventoryitem|itemnotification|newitem|eventnotification|commentnotification|marketnotification|invite|steam[_-][A-Za-z0-9_-]+)([^\]]*)\](?:([\s\S]*?)\[\/\1\])?\s*$/i.exec(body.trim()))){
                var info=attrs(m[2]),url=info.url||info.link;
                return h(N.View,{style:{padding:12,backgroundColor:'#29323f',borderLeftWidth:3,borderColor:'#66c0f4'}},
                    label(m[3]||info.title||info.name||t('Steam 通知','Steam notification'),{lineHeight:22},{selectable:true}),url?h(Button,{title:t('查看','View'),onPress:function(){openLink(url);}}):null);
            }
            body=body.replace(/\[img\s+([^\]]*)\](?:[^\[]*\[\/img\])?/gi,function(_,raw){var a=attrs(raw),url=a.src||a.thumbnail_src;return url?'[img]'+url+'[/img]':_;});
            body=body.replace(/\[emoticon(?:\s+[^\]]*)?\]([^\[]+)\[\/emoticon\]/gi,function(_,name){return ':'+name.replace(/^:|:$/g,'')+':';});
            var parts=[],pattern=/\[img(?:[^\]]*)\](https:\/\/[^\s\[]+)\[\/img\]|\[(b|i|u|s)\]([\s\S]*?)\[\/\2\]|\[url=(?:"([^"]+)"|([^\]]+))\]([\s\S]*?)\[\/url\]|https?:\/\/[^\s<>"'\[\]]+|:[A-Za-z0-9_\-]{2,80}:/g,match,last=0;
            while((match=pattern.exec(body))!==null&&parts.length<100){
                if(match.index>last)parts.push(label(body.slice(last,match.index).replace(/\\\[/g,'['),{lineHeight:22},{key:'t'+last,selectable:true}));
                var token=match[0],image=match[1]&&safe(match[1]),key='p'+match.index;
                if(!image&&/^https:/.test(token)&&/(?:\/ugc\/|\.(?:png|jpe?g|gif|webp|avif)(?:\?|$))/i.test(token)&&! /\.(?:mp4|webm|mpg|mpeg|ogv|zip)(?:\?|$)/i.test(token))image=safe(token);
                if(image)parts.push(h(MediaImage,{key:key,uri:image,open:props.open}));
                else if(match[2])parts.push(label(match[3],{lineHeight:22,fontWeight:match[2]==='b'?'700':undefined,fontStyle:match[2]==='i'?'italic':undefined,textDecorationLine:match[2]==='u'?'underline':match[2]==='s'?'line-through':undefined},{key:key,selectable:true}));
                else if(match[4]||match[5]){var link=match[4]||match[5];parts.push(h(Button,{key:key,title:match[6],onPress:(function(url){return function(){openLink(url);};})(link)}));}
                else if(token[0]===':')parts.push(h(N.Image,{key:key,source:{uri:'https://steamcommunity.com/economy/emoticon/'+encodeURIComponent(token.slice(1,-1))},style:{width:24,height:24},accessibilityLabel:token}));
                else if(/\.(?:mp4|webm|mpg|mpeg|ogv|zip)(?:\?|$)/i.test(token))parts.push(h(FileCard,{key:key,url:token,title:t('附件','Attachment')}));
                else parts.push(label(token,{color:'#66c0f4',lineHeight:22},{key:key,selectable:true,onPress:(function(url){return function(){openLink(url);};})(token)}));
                last=pattern.lastIndex;
            }
            if(last<body.length)parts.push(label(body.slice(last).replace(/\\\[/g,'['),{lineHeight:22},{key:'end',selectable:true}));
            return h(N.View,{style:{flexDirection:'row',flexWrap:'wrap',alignItems:'center',gap:3}},parts);
        }
        function Catalog(props){
            var state=env.useStore(),tab=R.useState('emoticons'),error=R.useState(''),query=R.useState('');
            function load(){env.load('catalog',true).catch(function(e){error[1](e.message);});}
            R.useEffect(load,[]);var kind=props.reactions&&tab[0]==='effects'?'emoticons':tab[0];
            var items=((state.catalog||{})[kind]||[]).filter(function(item){return item.name.toLowerCase().indexOf(query[0].toLowerCase())>=0;});
            return h(N.View,{style:{maxHeight:300,backgroundColor:'#23262e',borderTopWidth:1,borderColor:'#343944'}},
                h(N.View,{style:{flexDirection:'row'}},['emoticons','stickers'].concat(props.reactions?[]:['effects']).map(function(k,i){return h(Button,{key:k,title:[t('表情','Emoticons'),t('贴纸','Stickers'),t('特效','Effects')][i],onPress:function(){tab[1](k);}});}),h(Button,{title:t('关闭','Close'),onPress:props.close})),
                h(N.TextInput,{value:query[0],onChangeText:query[1],placeholder:t('搜索表情','Search items'),placeholderTextColor:'#a7b0bc',style:{minHeight:42,color:'#dcdedf',paddingHorizontal:12}}),
                error[0]?h(Button,{title:t('加载失败 · 重试','Could not load · Retry'),onPress:load}):null,
                h(N.FlatList,{data:items,numColumns:kind==='effects'?2:5,key:kind,keyExtractor:function(item){return item.name;},keyboardShouldPersistTaps:'always',
                    ListEmptyComponent:label(t('暂无可用项目','No owned items'),{padding:16,color:'#a7b0bc'}),
                    renderItem:function(row){var item=row.item;return h(N.Pressable,{style:{flex:1,minHeight:56,padding:8,alignItems:'center'},accessibilityRole:'button',accessibilityLabel:item.name,
                        onPress:function(){props.choose(kind==='emoticons'?':'+item.name+':':(kind==='stickers'?'/sticker ':'/roomeffect ')+item.name,kind,item);}},
                        kind==='effects'?label(item.name+(item.infinite?' ∞':' ×'+item.count),{fontSize:13}):h(N.Image,{source:{uri:'https://steamcommunity.com/economy/'+(kind==='emoticons'?'emoticonlarge/':'sticker/')+encodeURIComponent(item.name)},style:{width:40,height:40}}));}}));
        }
        function Viewer(props){
            var transform=R.useRef({scale:1,x:0,y:0,width:1,height:1,ratio:1}),tick=R.useState(0)[1],gesture=R.useRef(null),lastTap=R.useRef(0),saving=R.useState(false),status=R.useState('');
            function update(scale,x,y){var v=transform.current;v.scale=Math.max(1,Math.min(5,scale));var w=Math.min(v.width,v.height*v.ratio),ht=w/v.ratio;
                var bx=Math.max(0,(w*v.scale-v.width)/2),by=Math.max(0,(ht*v.scale-v.height)/2);v.x=Math.max(-bx,Math.min(bx,x));v.y=Math.max(-by,Math.min(by,y));tick(function(n){return n+1;});}
            function points(e){var p=e.nativeEvent.touches||[];if(p.length<2)return null;var a=p[0],b=p[1];return {distance:Math.sqrt(Math.pow(a.pageX-b.pageX,2)+Math.pow(a.pageY-b.pageY,2)),x:(a.locationX+b.locationX)/2,y:(a.locationY+b.locationY)/2};}
            var responder=R.useRef(N.PanResponder.create({
                onStartShouldSetPanResponder:function(){return true;},onMoveShouldSetPanResponder:function(){return true;},
                onPanResponderGrant:function(e,g){var v=transform.current;gesture.current={pinch:points(e),scale:v.scale,x:v.x,y:v.y,dx:g.dx,dy:g.dy};},
                onPanResponderMove:function(e,g){var v=transform.current,start=gesture.current,p=points(e);if(!start)return;
                    if(p&&!start.pinch){gesture.current={pinch:p,scale:v.scale,x:v.x,y:v.y,dx:g.dx,dy:g.dy};return;}
                    if(p){var scale=start.scale*p.distance/Math.max(1,start.pinch.distance),factor=Math.max(1,Math.min(5,scale))/start.scale;
                        update(scale,p.x-v.width/2-(start.pinch.x-v.width/2-start.x)*factor,p.y-v.height/2-(start.pinch.y-v.height/2-start.y)*factor);
                    }else if(start.pinch){gesture.current={pinch:null,scale:v.scale,x:v.x,y:v.y,dx:g.dx,dy:g.dy};}
                    else update(v.scale,start.x+g.dx-start.dx,start.y+g.dy-start.dy);
                },
                onPanResponderRelease:function(e,g){if(Math.abs(g.dx)+Math.abs(g.dy)<8&&!((gesture.current||{}).pinch)){
                    if(Date.now()-lastTap.current<280){var v=transform.current;update(v.scale>1?1:2.5,0,0);lastTap.current=0;}else lastTap.current=Date.now();}gesture.current=null;},
                onPanResponderTerminationRequest:function(){return false;},onPanResponderTerminate:function(){gesture.current=null;}
            })).current;
            R.useEffect(function(){var live=true;update(1,0,0);if(props.uri)N.Image.getSize(props.uri,function(w,ht){if(live){transform.current.ratio=w/Math.max(1,ht);tick(function(n){return n+1;});}},function(){});return function(){live=false;};},[props.uri]);
            var v=transform.current;
            return h(N.Modal,{visible:!!props.uri,onRequestClose:props.close},h(N.SafeAreaView,{style:{flex:1,backgroundColor:'#111318'}},
                h(N.View,{style:{flexDirection:'row',justifyContent:'space-between'}},h(Button,{title:t('关闭','Close'),onPress:props.close}),h(Button,{title:t('重置','Reset'),onPress:function(){update(1,0,0);}}),
                    h(Button,{title:saving[0]?t('正在保存…','Saving…'):t('保存','Save'),disabled:saving[0],onPress:function(){saving[1](true);env.request('saveMedia',{url:props.uri}).then(function(){status[1](t('已保存','Saved'));}).catch(function(){status[1](t('保存失败，请检查存储权限','Could not save. Check storage permission.'));}).then(function(){saving[1](false);});}})),
                status[0]?label(status[0],{paddingHorizontal:12,color:'#a7b0bc'}):null,
                h(N.View,Object.assign({style:{flex:1,overflow:'hidden',justifyContent:'center',alignItems:'center'},onLayout:function(e){v.width=e.nativeEvent.layout.width;v.height=e.nativeEvent.layout.height;update(v.scale,v.x,v.y);}},responder.panHandlers),
                    props.uri?h(N.Image,{pointerEvents:'none',source:{uri:props.uri},resizeMode:'contain',style:{width:'100%',height:'100%',transform:[{translateX:v.x},{translateY:v.y},{scale:v.scale}]}}):null)));
        }
        return {RichText:RichText,Catalog:Catalog,Viewer:Viewer,MediaImage:MediaImage};
    }
""".trimIndent()
}
