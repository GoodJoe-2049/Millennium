package com.millennium.app.features.chat.ui

internal object SteamChatActionsScript {
    fun source() = """
    function chatActions(env,ui,media){
        var R=env.React,N=env.RN,h=R.createElement,t=ui.t,label=ui.label,Button=ui.Button;
        function Panel(props){return h(N.Modal,{visible:true,animationType:'slide',onRequestClose:props.close},
            h(N.SafeAreaView,{style:{flex:1,backgroundColor:'#1b1e24'}},
                h(N.View,{style:{minHeight:52,flexDirection:'row',alignItems:'center',borderBottomWidth:1,borderColor:'#343944'}},h(Button,{title:t('返回','Back'),onPress:props.close}),label(props.title,{fontSize:18,fontWeight:'600',flex:1,paddingRight:12})),props.children));}
        function SwitchRow(props){return h(N.View,{style:{paddingHorizontal:16,minHeight:54,flexDirection:'row',alignItems:'center',gap:12}},label(props.title,{flex:1}),
            h(N.Switch,{value:!!props.value,onValueChange:props.onChange,disabled:props.disabled,accessibilityLabel:props.title,trackColor:{false:'#46505d',true:'#1a6da0'},thumbColor:'#dcdedf'}));}
        function MessageMenu(props){
            var item=props.item,choose=R.useState(false),busy=R.useState(false),error=R.useState('');
            function execute(operation,args){busy[1](true);error[1]('');env.request(operation,Object.assign({},props.target,item,args||{})).then(function(){
                if(operation==='deleteMessage')item.deleted=true;env.history(props.target,false);props.close();
            }).catch(function(e){error[1](ui.errorText(e.message));}).then(function(){busy[1](false);});}
            function report(reason){N.Alert.alert(t('举报这条消息？','Report this message?'),t('这会将消息提交给 Steam 审核。','This submits the message to Steam for review.'),[
                {text:t('取消','Cancel'),style:'cancel'},{text:t('举报','Report'),onPress:function(){execute('report',{reason:reason});}}]);}
            return h(Panel,{title:t('消息操作','Message actions'),close:props.close},h(N.ScrollView,{keyboardShouldPersistTaps:'handled'},
                label(item.deleted?t('消息已删除','Message deleted'):item.text,{padding:16,color:'#a7b0bc'},{numberOfLines:5}),error[0]?label(error[0],{padding:12,color:'#e8a49d'}):null,
                h(Button,{title:t('复制','Copy'),disabled:busy[0]||item.deleted,onPress:function(){env.request('copy',{text:item.text}).then(props.close).catch(function(e){error[1](ui.errorText(e.message));});}}),
                item.failed?h(Button,{title:t('移除此条待确认记录','Dismiss this unconfirmed entry'),disabled:busy[0],onPress:function(){env.discard(props.target,item).then(props.close).catch(function(e){error[1](ui.errorText(e.message));});}}):null,
                !item.pending&&!item.failed&&!item.deleted?h(Button,{title:t('添加表情回应','Add a reaction'),disabled:busy[0],onPress:function(){choose[1](!choose[0]);}}):null,
                choose[0]?h(media.Catalog,{reactions:true,close:function(){choose[1](false);},choose:function(code,kind,emote){execute('reaction',{name:emote.name,type:kind==='stickers'?2:1,add:true});}}):null,
                !item.pending&&!item.failed&&!item.deleted&&item.sender!==props.account&&!item.system? h(N.View,null,label(t('举报原因','Report reason'),{padding:16,fontWeight:'600'}),
                    [[3,t('骚扰','Harassment')],[13,t('诈骗','Scam')],[28,t('垃圾消息','Spam')],[2,t('其他','Other')]].map(function(row){return h(Button,{key:row[0],title:row[1],disabled:busy[0],onPress:function(){report(row[0]);}});})):null,
                props.target.group&&!item.pending&&!item.failed&&!item.deleted?h(Button,{title:t('删除群消息','Delete group message'),disabled:busy[0],onPress:function(){N.Alert.alert(t('删除消息？','Delete message?'),t('删除需要相应群权限，确认后无法恢复。','Requires group permission. This cannot be undone.'),[
                    {text:t('取消','Cancel'),style:'cancel'},{text:t('删除','Delete'),style:'destructive',onPress:function(){execute('deleteMessage');}}]);}}):null));
        }
        function Reactions(props){var error=R.useState(''),busy=R.useState(false);return h(N.View,null,
            h(N.View,{style:{flexDirection:'row',flexWrap:'wrap',gap:6,paddingTop:6}},(props.item.reactions||[]).filter(function(r){return r.count>0;}).map(function(r){return h(N.Pressable,{key:r.type+':'+r.name,disabled:busy[0],accessibilityRole:'button',accessibilityLabel:r.name+' '+r.count,
                onPress:function(){busy[1](true);env.request('reaction',Object.assign({},props.target,props.item,{name:r.name,type:r.type,add:!r.selected})).then(function(){env.history(props.target,false);}).catch(function(e){error[1](ui.errorText(e.message));}).then(function(){busy[1](false);});},
                style:{flexDirection:'row',alignItems:'center',gap:5,paddingHorizontal:7,paddingVertical:3,borderRadius:5,borderWidth:1,borderColor:r.selected?'#66c0f4':'#454d59',backgroundColor:r.selected?'#263f56':'#252d38'}},
                h(N.Image,{source:{uri:'https://steamcommunity.com/economy/'+(r.type===2?'sticker/':'emoticon/')+encodeURIComponent(r.name)},style:{width:22,height:22}}),label(String(r.count),{fontSize:12}));})),error[0]?label(error[0],{fontSize:12,color:'#e8a49d'}):null);}
        function Info(props){
            var state=env.useStore(),prefs=state.preferences[props.target.key]||{},query=R.useState(''),search=R.useState(false),error=R.useState(''),busy=R.useState(false);
            var target=props.target,data=env.thread(target),profile=target.profile||{},title=target.title||profile.name||target.peer;
            function preference(value){busy[1](true);env.preference(target,value).catch(function(e){error[1](ui.errorText(e.message));}).then(function(){busy[1](false);});}
            var results=data.messages.filter(function(m){return !m.deleted&&(m.text+' '+((m.profile||{}).name||m.sender)).toLowerCase().indexOf(query[0].toLowerCase())>=0;});
            return h(Panel,{title:search[0]?t('搜索聊天记录','Search messages'):t('聊天详情','Conversation info'),close:function(){if(search[0])search[1](false);else props.close();}},
                search[0]?h(N.View,{style:{flex:1}},h(N.TextInput,{autoFocus:true,value:query[0],onChangeText:query[1],placeholder:t('搜索已加载的消息或发送者','Search loaded messages or senders'),placeholderTextColor:'#a7b0bc',style:{padding:16,color:'#dcdedf',fontSize:16,minHeight:52}}),
                    h(N.FlatList,{data:results,keyExtractor:function(m){return m.id;},ListEmptyComponent:label(t('没有匹配的消息','No matching messages'),{padding:24,color:'#a7b0bc'}),
                        ListFooterComponent:data.more?h(Button,{title:data.busy?t('正在加载…','Loading…'):t('加载更早的记录','Load earlier history'),disabled:data.busy,onPress:function(){env.history(target,true);}}):null,
                        renderItem:function(row){return h(N.Pressable,{onPress:function(){props.jump(row.item.id);props.close();},style:{padding:16,borderBottomWidth:1,borderColor:'#343944'}},label((row.item.profile||{}).name||row.item.sender,{fontSize:12,color:'#66c0f4'}),label(row.item.text,{paddingTop:6},{numberOfLines:4}));}})):
                h(N.ScrollView,null,label(title,{padding:20,fontSize:22,fontWeight:'600'}),
                    target.peer?label(profile.game||(profile.online?t('在线','Online'):t('离线','Offline')),{paddingHorizontal:20,color:'#a7b0bc'}):null,
                    error[0]?label(error[0],{padding:16,color:'#e8a49d'}):null,
                    h(SwitchRow,{title:t('置顶聊天','Pin conversation'),value:prefs.pinned,disabled:busy[0],onChange:function(value){preference({pinned:value});}}),
                    h(SwitchRow,{title:t('消息免打扰','Mute notifications'),value:prefs.muted,disabled:busy[0],onChange:function(value){preference({muted:value});}}),
                    h(Button,{title:t('搜索聊天记录','Search messages'),onPress:function(){search[1](true);}}),
                    target.peer?h(Button,{title:t('查看 Steam 个人资料','View Steam profile'),onPress:function(){N.Linking.openURL('https://steamcommunity.com/profiles/'+target.peer+'/').catch(function(){});}}):null,
                    target.group?h(Button,{title:t('群资料与管理','Group info and management'),onPress:props.manage}):null));
        }
        return {Panel:Panel,SwitchRow:SwitchRow,MessageMenu:MessageMenu,Reactions:Reactions,Info:Info};
    }
""".trimIndent()
}
