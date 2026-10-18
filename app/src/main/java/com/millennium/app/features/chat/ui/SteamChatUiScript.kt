package com.millennium.app.features.chat.ui

/** Native React Native views using Steam's header, theme and icon components. */
internal object SteamChatUiScript {
    fun source() = """
    function chatUi(require) {
        var env=chatEnvironment(require);
        if(env.ui)return env.ui;
        var R=env.React,N=env.RN,h=R.createElement,Icons=require(1833);
        var language=require(1450).Localize('#MainMenu_ChatLink');
        var zh=/[\u3400-\u9fff]/.test(language);
        function t(c,e){return zh?c:e;}
        function colors(){
            var theme=require(1590).useTheme(),c=theme.color||{};
            return {bg:(c.background||{}).primary||'#1b1e24',panel:(c.background||{}).secondary||'#23262e',
                text:(c.foreground||{}).standard||'#dcdedf',muted:'#a7b0bc',blue:'#1a9fff',line:'#343944'};
        }
        function label(text,style,props){return h(N.Text,Object.assign({style:Object.assign({color:'#dcdedf',fontSize:15},style||{})},props||{}),text);}
        function errorText(code){
            if(code==='LOGIN_REQUIRED'||code==='AUTH_EXPIRED'||code==='ACCOUNT_CHANGED')return t('请重新登录 Steam 后重试','Please sign in to Steam again.');
            if(code==='IMAGE_TOO_LARGE'||code==='ATTACHMENT_TOO_LARGE')return t('附件不能超过 30 MB','Attachments must be 30 MB or smaller.');
            if(code==='REQUEST_REJECTED')return t('Steam 拒绝了操作，请检查群权限或操作条件','Steam rejected the action. Check group permissions and requirements.');
            if(code==='AVATAR_TOO_LARGE')return t('群头像不能超过 5 MB','Group avatars must be 5 MB or smaller.');
            if(code==='IMAGE_EXPIRED')return t('请重新选择图片','Please select the image again.');
            if(code==='RATE_LIMITED')return t('请求过于频繁，请稍后重试','Too many requests. Try again shortly.');
            return t('连接暂时不可用，请重试','Connection unavailable. Please try again.');
        }
        function Button(props){
            return h(N.Pressable,{onPress:props.onPress,disabled:props.disabled,accessibilityRole:'button',accessibilityLabel:props.title,
                accessibilityState:{disabled:!!props.disabled},style:function(s){return {minHeight:48,minWidth:48,paddingHorizontal:12,
                    justifyContent:'center',alignItems:'center',opacity:props.disabled ? 0.4 : s.pressed ? 0.65 : 1,backgroundColor:props.primary?'#1a6da0':'transparent',borderRadius:3};}},
                props.icon?h(props.icon,{size:24}):label(props.title,{color:props.primary?'#fff':'#66c0f4',fontSize:14}));
        }
        function Avatar(props){
            var profile=props.profile||{},size=props.size||44;
            return h(N.View,{style:{width:size,height:size,backgroundColor:'#313943',borderRadius:2,alignItems:'center',justifyContent:'center'}},
                profile.avatar?h(N.Image,{source:{uri:profile.avatar},fadeDuration:0,style:{width:size,height:size},accessibilityIgnoresInvertColors:true}):label((profile.name||'?').slice(0,1),{fontSize:20}));
        }
        function Banner(props){return h(N.View,{style:{paddingHorizontal:12,paddingVertical:4,backgroundColor:'#293443',flexDirection:'row',alignItems:'center'}},
            label(errorText(props.error),{flex:1,fontSize:13}),props.retry?h(Button,{title:t('重试','Retry'),onPress:props.retry}):null);}
        function Empty(props){return h(N.View,{style:{padding:32,alignItems:'center'}},props.loading?h(N.ActivityIndicator,{color:'#66c0f4'}):null,
            label(props.loading?t('正在加载…','Loading…'):props.title,{color:'#a7b0bc',paddingTop:16,textAlign:'center'}));}
        function allowed(){
            var settings=require(2125).useParentalSettings(),lock=require(2125).useParentalLock();
            var value=settings.data&&settings.data.settings,feature=require(962).k_EParentalFeature_k_EFeatureFriends;
            return !!value && !require(2117).BIsParentalFeatureBlocked(value,feature,lock.bLocked);
        }
        var components={t:t,label:label,Button:Button,colors:colors,errorText:errorText};
        var media=chatMedia(env,components),actions=chatActions(env,components,media),groupsUi=chatGroups(env,components,actions);
        var RichText=media.RichText,Catalog=media.Catalog;
        function Thread(props){
            var state=env.useStore(),target=props.target,data=env.thread(target),c=colors();
            var draft=R.useState(data.draft||''),picker=R.useState(false),attachment=R.useState(null),busy=R.useState(false),error=R.useState(''),viewer=R.useState(null);
            var menu=R.useState(null),info=R.useState(false),admin=R.useState(false),bottom=R.useState(true),spoiler=R.useState(false),progress=R.useState(0);
            var nearBottom=R.useRef(true),mounted=R.useRef(true),focused=require(1366).useIsFocused(),list=R.useRef(null),restored=R.useRef(false),jump=R.useRef(null),progressTimer=R.useRef(null),uncertain=R.useRef(false);
            R.useEffect(function(){mounted.current=true;env.history(target,false);return function(){mounted.current=false;clearTimeout(progressTimer.current);env.saveThread(target,true);env.visible(null);};},[target.key]);
            R.useEffect(function(){if(data.cached&&!data.edited)draft[1](data.draft||'');},[data.cached]);
            R.useEffect(function(){env.visible(focused?target:null);if(focused&&state.connected)env.history(target,false);return function(){env.visible(null);};},[focused,state.connected,state.syncRevision]);
            R.useEffect(function(){if(focused&&nearBottom.current)env.acknowledge(target);},[focused,data.messages.length]);
            function scrollTo(id){var index=data.messages.findIndex(function(m){return m.id===id;});if(index<0||!list.current)return;
                jump.current={index:index,attempts:0};list.current.scrollToIndex({index:index,animated:false,viewPosition:0.5});}
            R.useEffect(function(){if(restored.current||!data.cached||!data.messages.length)return;restored.current=true;
                if(data.position&&data.position.id&&!data.position.bottom){nearBottom.current=false;bottom[1](false);var timer=setTimeout(function(){scrollTo(data.position.id);},40);return function(){clearTimeout(timer);};}
            },[data.cached,data.messages.length]);
            var viewability=R.useRef(function(result){var item=result.viewableItems&&result.viewableItems[0];if(!restored.current||!item)return;
                data.position={id:item.item.id,bottom:nearBottom.current};env.saveThread(target,false);}).current;
            function edit(value){draft[1](value);env.editDraft(target,value);if(value.trim())env.typing(target);}
            function latest(){nearBottom.current=true;bottom[1](true);if(list.current)list.current.scrollToOffset({offset:0,animated:true});env.acknowledge(target);}
            function send(){if(busy[0]||!draft[0].trim())return;var text=draft[0];edit('');picker[1](false);env.send(target,text);latest();}
            function pick(){busy[1](true);error[1]('');env.request('pickFile').then(function(file){if(mounted.current&&!file.cancelled){attachment[1](file);spoiler[1](false);uncertain.current=false;}})
                .catch(function(e){if(mounted.current)error[1](e.message);}).then(function(){if(mounted.current)busy[1](false);});}
            function upload(){
                if(!attachment[0]||busy[0])return;
                if(uncertain.current){N.Alert.alert(t('再次发送附件？','Send attachment again?'),t('请先检查上一份附件是否已经送达。','Check whether the previous attachment was delivered first.'),[{text:t('取消','Cancel'),style:'cancel'},{text:t('再次发送','Send again'),onPress:function(){uncertain.current=false;upload();}}]);return;}
                busy[1](true);error[1]('');progress[1](0);var selection=attachment[0].selection,finished=false;
                function pollProgress(){if(finished||!mounted.current)return;env.request('uploadProgress',{selection:selection}).then(function(value){if(mounted.current)progress[1](value.percent);}).catch(function(){})
                    .then(function(){if(!finished&&mounted.current)progressTimer.current=setTimeout(pollProgress,400);});}pollProgress();
                env.request('upload',Object.assign({},target,{selection:selection,spoiler:spoiler[0]})).then(function(){if(mounted.current){attachment[1](null);env.history(target,false);latest();}})
                    .catch(function(e){uncertain.current=true;if(mounted.current)error[1](e.message);}).then(function(){finished=true;clearTimeout(progressTimer.current);if(mounted.current)busy[1](false);});
            }
            function retry(item){env.history(target,false).then(function(){if(!data.messages.some(function(m){return m.id===item.id;}))return;
                N.Alert.alert(t('重新发送？','Send again?'),t('请确认消息未送达，以免重复发送。','Check that this message was not delivered before sending it again.'),[
                    {text:t('取消','Cancel'),style:'cancel'},{text:t('重新发送','Send again'),onPress:function(){env.discard(target,item).then(function(){env.send(target,item.text);}).catch(function(e){error[1](e.message);});}}]);});}
            return h(N.KeyboardAvoidingView,{style:{flex:1},behavior:N.Platform.OS==='ios'?'padding':undefined},
                h(N.View,{style:{flexDirection:'row',justifyContent:'flex-end',backgroundColor:c.panel}},h(Button,{title:t('聊天详情','Conversation info'),onPress:function(){info[1](true);}})),
                error[0]?h(Banner,{error:error[0]}):null,data.error?h(Banner,{error:data.error,retry:function(){env.history(target,false);}}):null,
                h(N.FlatList,{ref:list,style:{flex:1},data:data.messages,inverted:true,contentContainerStyle:{flexGrow:1,justifyContent:'flex-end'},keyExtractor:function(m){return m.id;},keyboardShouldPersistTaps:'handled',
                    keyboardDismissMode:'on-drag',maintainVisibleContentPosition:{minIndexForVisible:0},initialNumToRender:24,windowSize:9,
                    onViewableItemsChanged:viewability,viewabilityConfig:{itemVisiblePercentThreshold:30,minimumViewTime:300},
                    onScroll:function(e){var near=e.nativeEvent.contentOffset.y<60;if(near!==nearBottom.current){nearBottom.current=near;bottom[1](near);}if(near&&focused)env.acknowledge(target);},scrollEventThrottle:120,
                    onScrollToIndexFailed:function(e){var pending=jump.current;if(!pending||pending.attempts++>3)return;if(list.current)list.current.scrollToOffset({offset:e.averageItemLength*e.index,animated:false});setTimeout(function(){if(mounted.current&&list.current)list.current.scrollToIndex({index:pending.index,animated:false,viewPosition:0.5});},120);},
                    onEndReachedThreshold:0.4,onEndReached:function(){if(data.loaded&&data.more&&!data.busy)env.history(target,true);},
                    ListEmptyComponent:h(N.View,{style:{transform:[{scaleY:-1}]}},h(Empty,{loading:!data.loaded&&!data.cached&&!data.error,title:t('发送消息，开始聊天','Send a message to start chatting')})),
                    ListFooterComponent:data.more?h(Button,{title:data.loadingOlder?t('正在加载…','Loading…'):t('加载更早的消息','Load earlier messages'),disabled:data.loadingOlder,onPress:function(){env.history(target,true);}}):null,
                    renderItem:function(row){var item=row.item,self=item.sender===state.account,profile=item.profile||{};
                        return h(N.Pressable,{onLongPress:function(){menu[1](item);},delayLongPress:350,accessibilityLabel:t('聊天消息','Chat message'),style:{paddingHorizontal:14,paddingVertical:10,flexDirection:'row',gap:10}},
                            h(Avatar,{profile:self?Object.assign({},profile.avatar?profile:(state.self||profile),{name:t('我','You')}):profile,size:32}),
                            h(N.View,{style:{flex:1}},h(N.View,{style:{flexDirection:'row',alignItems:'center',gap:10}},
                                label(self?t('我','You'):(profile.name||item.sender||t('系统','System')),{color:self?c.blue:c.text,fontSize:13,fontWeight:'600',flex:1}),
                                label(new Date(item.timestamp*1000).toLocaleString(),{color:c.muted,fontSize:10}),
                                h(N.Pressable,{onPress:function(){menu[1](item);},hitSlop:10,accessibilityLabel:t('消息操作','Message actions')},label('⋯',{fontSize:20,color:c.muted}))),
                                item.deleted?label(t('消息已删除','Message deleted'),{color:c.muted,fontStyle:'italic'}):h(RichText,{text:item.text,open:viewer[1]}),
                                !item.deleted?h(actions.Reactions,{target:target,item:item}):null,
                                item.pending?label(t('正在发送…','Sending…'),{color:c.muted,fontSize:11}):null,
                                item.failed?h(Button,{title:t('未确认送达 · 重试','Delivery unconfirmed · Retry'),onPress:function(){retry(item);}}):null));}}),
                !bottom[0]?h(Button,{title:t('跳到最新消息 ↓','Jump to latest ↓'),onPress:latest}):null,
                state.typing[target.key]?label(t('对方正在输入…','Typing…'),{fontSize:12,color:c.blue,paddingHorizontal:14,paddingVertical:4}):null,
                attachment[0]?h(N.View,{style:{backgroundColor:c.panel,padding:10}},
                    h(N.View,{style:{flexDirection:'row',alignItems:'center'}},attachment[0].image?h(N.Image,{source:{uri:attachment[0].uri},style:{width:64,height:64},resizeMode:'contain'}):null,
                        label(attachment[0].name||t('附件','Attachment'),{flex:1,padding:8},{numberOfLines:2}),
                        h(Button,{title:busy[0]?String(progress[0])+'%':t('发送附件','Send attachment'),primary:true,disabled:busy[0],onPress:upload}),
                        h(Button,{title:t('取消','Cancel'),disabled:busy[0],onPress:function(){attachment[1](null);}})),
                    h(actions.SwitchRow,{title:t('标记为剧透内容','Mark as spoiler'),value:spoiler[0],disabled:busy[0],onChange:spoiler[1]})):null,
                picker[0]?h(Catalog,{close:function(){picker[1](false);},choose:function(code){edit(/^\/(?:sticker|roomeffect) /.test(code)?code:draft[0]+code);picker[1](false);}}):null,
                h(N.View,{style:{backgroundColor:c.panel,paddingHorizontal:6,paddingVertical:6,flexDirection:'row',alignItems:'flex-end',borderTopWidth:1,borderColor:c.line}},
                    h(Button,{title:t('添加附件','Add attachment'),icon:Icons.IconPlus,onPress:pick,disabled:busy[0]}),
                    h(N.TextInput,{value:draft[0],onChangeText:edit,multiline:true,maxLength:2000,placeholder:t('发送消息','Send a message'),placeholderTextColor:c.muted,
                        accessibilityLabel:t('消息','Message'),style:{flex:1,minHeight:48,maxHeight:128,fontSize:16,color:c.text,paddingHorizontal:10,paddingVertical:12,backgroundColor:c.bg,borderRadius:3}}),
                    h(Button,{title:t('表情','Emotes'),onPress:function(){N.Keyboard.dismiss();picker[1](!picker[0]);}}),h(Button,{title:t('发送','Send'),onPress:send,disabled:!draft[0].trim()||busy[0]})),
                viewer[0]?h(media.Viewer,{uri:viewer[0],close:function(){viewer[1](null);}}):null,
                menu[0]?h(actions.MessageMenu,{target:target,item:menu[0],account:state.account,close:function(){menu[1](null);}}):null,
                info[0]?h(actions.Info,{target:target,close:function(){info[1](false);},jump:scrollTo,manage:function(){info[1](false);admin[1](true);}}):null,
                admin[0]?h(groupsUi.Admin,{group:target.group,room:target.room,close:function(){admin[1](false);}}):null);
        }
        function ChatHome(props){
            var state=env.useStore(),c=colors(),target=R.useState(null),section=R.useState('sessions'),group=R.useState(null),search=R.useState(''),error=R.useState('');
            var focused=require(1366).useIsFocused(),create=R.useState(false),manage=R.useState(false);
            R.useEffect(function(){if(state.openTarget){target[1](state.openTarget);state.openTarget=null;env.emit();}},[state.openTarget]);
            function back(){if(target[0]){target[1](null);return true;}if(group[0]){group[1](null);return true;}return false;}
            R.useEffect(function(){var sub=N.BackHandler.addEventListener('hardwareBackPress',function(){return focused&&back();});return function(){sub.remove();};},[focused,target[0],group[0]]);
            function friends(){error[1]('');env.load('friends').catch(function(e){error[1](e.message);});}
            function notifications(){
                function save(value){state.background=value;env.emit();}
                function settings(){N.Alert.alert(t('请允许 Steam 发送通知','Allow Steam notifications'),t('在 Steam 的系统通知设置中允许聊天消息','Allow chat messages in Steam’s notification settings.'),[
                    {text:t('取消','Cancel'),style:'cancel'},{text:t('设置','Settings'),onPress:function(){N.Linking.openSettings().catch(function(){});}}]);}
                if(state.background.enabled&&state.background.permitted){
                    N.Alert.alert(t('关闭后台通知？','Disable background notifications?'),'', [{text:t('取消','Cancel'),style:'cancel'},
                        {text:t('关闭','Disable'),onPress:function(){env.request('backgroundDisable').then(save).catch(function(e){error[1](e.message);});}}]);return;
                }
                N.Alert.alert(t('启用后台通知','Enable background notifications'),t('Steam 在后台运行时接收聊天通知。结束 Steam 进程后将停止接收。','Receive chat notifications while Steam runs in the background. Delivery stops when Steam’s process ends.'),[
                    {text:t('取消','Cancel'),style:'cancel'},{text:t('启用','Enable'),onPress:function(){
                        var account=state.account;
                        var permission=N.Platform.OS==='android'&&Number(N.Platform.Version)>=33
                            ?N.PermissionsAndroid.request('android.permission.POST_NOTIFICATIONS'):Promise.resolve('granted');
                        permission.then(function(result){
                            if(env.currentAccount()!==account)return;
                            if(result!=='granted'){settings();return;}
                            return env.request('backgroundEnable').then(function(value){save(value);if(!value.permitted)settings();});
                        }).catch(function(e){error[1](e.message);});
                    }}]);
            }
            R.useEffect(function(){if(section[0]==='friends'&&!state.friends)friends();},[section[0]]);
            var selectedGroup=group[0]?(state.groups.find(function(g){return g.id===group[0].id;})||group[0]):null;
            var rows=section[0]==='groups'?(selectedGroup?selectedGroup.rooms:state.groups):section[0]==='friends'?(state.friends||[]):state.sessions.slice().sort(function(a,b){var ap=(state.preferences[a.key]||{}).pinned?1:0,bp=(state.preferences[b.key]||{}).pinned?1:0;return bp-ap||(b.timestamp||0)-(a.timestamp||0);});
            rows=rows.slice().sort(function(a,b){return ((state.preferences[b.key]||{}).pinned?1:0)-((state.preferences[a.key]||{}).pinned?1:0);});
            rows=rows.filter(function(row){return (row.title||(row.profile||{}).name||row.name||'').toLowerCase().indexOf(search[0].toLowerCase())>=0;});
            var heading=target[0]?(target[0].title||(target[0].profile||{}).name||target[0].peer):selectedGroup?selectedGroup.title:t('聊天','Chat');
            var navigation=Object.create(props.navigation);
            Object.defineProperty(navigation,'canGoBack',{value:function(){return !!target[0]||!!group[0]||props.navigation.canGoBack();}});
            Object.defineProperty(navigation,'goBack',{value:function(){if(!back())props.navigation.goBack();}});
            return h(N.View,{style:{flex:1,backgroundColor:c.bg}},h(require(2212).Header,{title:heading,navigation:navigation}),
                state.error?h(Banner,{error:state.error,retry:env.refresh}):null,
                create[0]?h(groupsUi.Create,{close:function(){create[1](false);},created:function(id){create[1](false);section[1]('groups');group[1](state.groups.find(function(g){return g.id===id;})||null);}}):null,
                manage[0]&&group[0]?h(groupsUi.Admin,{group:group[0].id,close:function(){manage[1](false);}}):null,
                target[0]?h(Thread,{key:target[0].key,target:target[0]}):h(N.View,{style:{flex:1}},
                    h(N.View,{style:{flexDirection:'row',backgroundColor:c.panel}},['sessions','groups','friends'].map(function(key,i){return h(N.Pressable,{key:key,
                        onPress:function(){section[1](key);group[1](null);search[1]('');},accessibilityRole:'tab',accessibilityState:{selected:section[0]===key},
                        style:{flex:1,minHeight:48,alignItems:'center',justifyContent:'center',borderBottomWidth:2,borderColor:section[0]===key?c.blue:'transparent'}},
                        label([t('消息','Messages'),t('群聊','Groups'),t('好友','Friends')][i],{color:section[0]===key?c.blue:c.text}));})),
                    h(N.View,{style:{flexDirection:'row',justifyContent:'space-between'}},h(Button,{title:t('创建群聊','Create group'),onPress:function(){create[1](true);}}),
                        group[0]?h(Button,{title:t('群管理','Manage group'),onPress:function(){manage[1](true);}}):h(Button,{
                            title:state.background.enabled&&state.background.permitted?t('后台通知：已启用','Background notifications: on'):t('启用后台通知','Enable background notifications'),
                            onPress:notifications})),
                    h(N.TextInput,{value:search[0],onChangeText:search[1],placeholder:t('搜索','Search'),placeholderTextColor:c.muted,accessibilityLabel:t('搜索聊天','Search chats'),
                        style:{minHeight:48,margin:12,paddingHorizontal:12,color:c.text,fontSize:16,backgroundColor:c.panel,borderRadius:3}}),
                    error[0]?h(Banner,{error:error[0],retry:friends}):null,
                    section[0]==='groups'&&state.groupsError?h(Banner,{error:'REQUEST_FAILED',retry:env.refresh}):null,
                    h(N.FlatList,{data:rows,keyExtractor:function(row){return row.key||row.id;},keyboardShouldPersistTaps:'handled',refreshing:state.refreshing,
                        onRefresh:section[0]==='friends'?function(){state.friends=null;friends();}:function(){env.refresh(true);},
                        ListEmptyComponent:h(Empty,{loading:state.loading||(section[0]==='groups'&&state.groupsLoading)||(section[0]==='friends'&&!state.friends&&!error[0]),title:t('暂无聊天','No conversations yet')}),
                        renderItem:function(row){var item=row.item,profile=item.profile||item,title=item.title||profile.name||item.peer,unread=item.unread||0;
                            if(item.rooms)unread=item.rooms.reduce(function(n,r){return n+(r.unread||0);},0);
                            return h(N.Pressable,{onPress:function(){if(item.rooms){group[1](item);search[1]('');}else target[1](item.key?item:{key:'p:'+item.id,peer:item.id,kind:'private',profile:item});},
                                accessibilityRole:'button',accessibilityLabel:title,style:function(s){return {paddingHorizontal:16,paddingVertical:14,flexDirection:'row',alignItems:'center',gap:12,backgroundColor:s.pressed?c.panel:'transparent',borderBottomWidth:1,borderColor:c.line};}},
                                h(Avatar,{profile:profile}),h(N.View,{style:{flex:1}},label(title,{color:profile.online?'#66c0f4':c.text,fontWeight:'500'},{numberOfLines:1}),
                                    label(item.rooms?t(item.rooms.length+' 个频道',item.rooms.length+' channels'):(item.preview||profile.game||(profile.online?t('在线','Online'):t('离线','Offline'))),{color:c.muted,fontSize:13,marginTop:4},{numberOfLines:1})),
                                unread?label(String(Math.min(unread,99)),{color:'#fff',backgroundColor:'#1a6da0',paddingHorizontal:7,paddingVertical:3,borderRadius:10,fontSize:12}):null);}})));
        }
        // This source is injected directly into Hermes, without Metro/Babel class transforms.
        function ChatBoundary(props,context,updater){
            R.Component.call(this,props,context,updater);
            this.state={failed:false};
        }
        ChatBoundary.prototype=Object.create(R.Component.prototype);
        Object.defineProperty(ChatBoundary.prototype,'constructor',{value:ChatBoundary,writable:true,configurable:true});
        ChatBoundary.getDerivedStateFromError=function(){return {failed:true};};
        ChatBoundary.prototype.render=function(){
            var self=this;return this.state.failed?h(N.View,{style:{flex:1,paddingTop:32,backgroundColor:'#1b1e24'}},
                label(t('聊天','Chat'),{paddingHorizontal:16,fontSize:20,fontWeight:'600'}),
                h(Empty,{title:t('聊天界面暂时不可用','Chat is temporarily unavailable')}),
                h(Button,{title:t('重试','Retry'),onPress:function(){self.setState({failed:false});}}),
                h(Button,{title:t('返回','Back'),onPress:function(){self.props.navigation.goBack();}})):this.props.children;
        };
        function ScreenContent(props){
            var auth=require(1136).useAuth(),canChat=allowed(),state=env.useStore(),c=colors();
            if(!auth.bLoggedIn||!canChat||state.account!==auth.activeSteamID)return h(N.View,{style:{flex:1,backgroundColor:c.bg}},
                h(require(2212).Header,{title:t('聊天','Chat'),navigation:props.navigation}),h(Empty,{loading:!!auth.bLoggedIn&&canChat,title:auth.bLoggedIn?t('聊天当前不可用','Chat is currently unavailable'):t('请先登录 Steam','Sign in to Steam to chat')}));
            return h(ChatHome,Object.assign({},props,{key:auth.activeSteamID}));
        }
        function Screen(props){
            // Include the auth/theme hooks and every header state inside the page boundary.
            // The fallback above uses only native primitives, so a broken host header cannot escape it.
            return h(ChatBoundary,{navigation:props.navigation},h(ScreenContent,props));
        }
        env.ui={Screen:Screen,allowed:allowed,t:t,colors:colors,label:label};
        return env.ui;
    }
""".trimIndent()
}
