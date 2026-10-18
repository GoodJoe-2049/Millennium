package com.millennium.app.features.chat.ui

internal object SteamChatGroupScript {
    fun source() = """
    function chatGroups(env,ui,actions){
        var R=env.React,N=env.RN,h=R.createElement,t=ui.t,label=ui.label,Button=ui.Button,Panel=actions.Panel,SwitchRow=actions.SwitchRow;
        function Create(props){
            var state=env.useStore(),name=R.useState(''),chosen=R.useState([]),query=R.useState(''),busy=R.useState(false),error=R.useState('');
            R.useEffect(function(){env.load('friends').catch(function(e){error[1](ui.errorText(e.message));});},[]);
            return h(Panel,{title:t('创建群聊','Create group'),close:props.close},
                h(N.TextInput,{value:name[0],onChangeText:name[1],maxLength:64,placeholder:t('群名称','Group name'),placeholderTextColor:'#a7b0bc',style:{padding:16,color:'#dcdedf',fontSize:18,minHeight:52}}),
                h(N.TextInput,{value:query[0],onChangeText:query[1],placeholder:t('邀请好友（可选）','Invite friends (optional)'),placeholderTextColor:'#a7b0bc',style:{padding:16,color:'#dcdedf',minHeight:48}}),
                error[0]?label(error[0],{padding:12,color:'#e8a49d'}):null,
                h(N.FlatList,{data:(state.friends||[]).filter(function(f){return f.name.toLowerCase().indexOf(query[0].toLowerCase())>=0;}),keyExtractor:function(f){return f.id;},
                    renderItem:function(row){var friend=row.item,selected=chosen[0].indexOf(friend.id)>=0;return h(N.Pressable,{onPress:function(){chosen[1](selected?chosen[0].filter(function(id){return id!==friend.id;}):chosen[0].concat(friend.id));},style:{padding:16,flexDirection:'row',gap:12}},label(selected?'✓':'○',{color:'#66c0f4'}),label(friend.name));}}),
                h(Button,{title:busy[0]?t('正在创建…','Creating…'):t('创建','Create'),primary:true,disabled:busy[0]||!name[0].trim(),onPress:function(){busy[1](true);env.request('groupAction',{action:'createGroup',name:name[0],invitees:chosen[0]}).then(function(value){return env.refresh().then(function(){props.created(value.group);});}).catch(function(e){error[1](ui.errorText(e.message));busy[1](false);});}}));
        }
        function Admin(props){
            var state=env.useStore(),tab=R.useState('info'),snapshot=R.useState(null),busy=R.useState(false),error=R.useState(''),form=R.useState(null),values=R.useState({}),member=R.useState(null),role=R.useState(null),avatar=R.useState(null);
            var group=state.groups.find(function(g){return g.id===props.group;})||{id:props.group,rooms:[],title:props.group};
            function load(){return env.request('groupInfo',{group:props.group}).then(function(result){snapshot[1](result);}).catch(function(e){error[1](ui.errorText(e.message));});}
            R.useEffect(function(){load();env.load('friends').catch(function(){});},[props.group]);
            function run(action,args){busy[1](true);error[1]('');return env.request('groupAction',Object.assign({action:action,group:props.group},args||{}))
                .then(function(result){form[1](null);return env.refresh().then(load).then(function(){return result;});})
                .catch(function(e){error[1](ui.errorText(e.message));throw e;}).then(function(result){busy[1](false);return result;},function(e){busy[1](false);throw e;});}
            function act(action,args){run(action,args).catch(function(){});}
            function confirm(title,action,args){N.Alert.alert(title,t('此操作会更改 Steam 群聊，是否继续？','This changes the Steam group. Continue?'),[{text:t('取消','Cancel'),style:'cancel'},{text:t('确认','Confirm'),onPress:function(){act(action,args);}}]);}
            function edit(title,action,fields,args){var initial={};fields.forEach(function(f){initial[f.key]=String(f.value||'');});values[1](initial);form[1]({title:title,action:action,fields:fields,args:args||{}});}
            function field(title,key,value,numeric){return {title:title,key:key,value:value,numeric:numeric};}
            function button(title,onPress,disabled){return h(Button,{title:title,onPress:onPress,disabled:busy[0]||disabled});}
            function person(id){var friend=(state.friends||[]).find(function(f){return f.id===id;});return friend?friend.name:id;}
            function close(){if(form[0])form[1](null);else if(avatar[0])avatar[1](null);else if(role[0])role[1](null);else if(member[0])member[1](null);else props.close();}
            var data=snapshot[0]||{members:[],roles:[],links:[],bans:[],invites:[],unavailable:[]};
            var currentMember=data.members.find(function(m){return member[0]&&m.id===member[0].id;})||member[0];
            var content;
            if(form[0])content=h(N.View,null,form[0].fields.map(function(f){return h(N.View,{key:f.key,style:{paddingHorizontal:16}},label(f.title,{color:'#a7b0bc',paddingTop:12}),
                h(N.TextInput,{value:values[0][f.key],onChangeText:function(v){var next=Object.assign({},values[0]);next[f.key]=v;values[1](next);},keyboardType:f.numeric?'number-pad':'default',style:{color:'#dcdedf',fontSize:16,minHeight:48},maxLength:f.numeric?10:128}));}),
                button(t('保存','Save'),function(){var args=Object.assign({},form[0].args,values[0]);form[0].fields.forEach(function(f){if(f.numeric)args[f.key]=Number(args[f.key]);});act(form[0].action,args);}));
            else if(avatar[0])content=h(N.View,{style:{padding:16}},h(N.Image,{source:{uri:avatar[0].uri},style:{width:160,height:160,alignSelf:'center'},resizeMode:'contain'}),
                button(t('保存群头像','Save group avatar'),function(){busy[1](true);env.request('groupAvatar',{selection:avatar[0].selection}).then(function(value){return run('avatar',{sha:value.sha});}).then(function(){avatar[1](null);}).catch(function(e){error[1](ui.errorText(e.message));}).then(function(){busy[1](false);});}));
            else if(role[0])content=h(N.View,null,label(role[0].name,{fontSize:20,padding:16}),
                [t('管理频道','Manage channels'),t('移出成员','Kick members'),t('封禁成员','Ban members'),t('邀请成员','Invite members'),t('修改群资料','Edit group info'),t('发送消息','Send messages'),t('查看历史消息','View history'),t('管理角色','Manage roles'),t('分配角色','Assign roles'),t('提及所有成员','Mention everyone'),t('设置直播','Set broadcast')].map(function(title,i){return h(SwitchRow,{key:i,title:title,value:role[0].permissions[String(i+2)],onChange:function(value){var next=Object.assign({},role[0],{permissions:Object.assign({},role[0].permissions)});next.permissions[String(i+2)]=value;role[1](next);}});}),
                button(t('保存权限','Save permissions'),function(){act('rolePermissions',{role:role[0].id,permissions:role[0].permissions});}),
                button(t('重命名角色','Rename role'),function(){edit(t('角色名称','Role name'),'renameRole',[field(t('名称','Name'),'name',role[0].name)],{role:role[0].id});}),
                button(t('删除角色','Delete role'),function(){confirm(t('删除角色？','Delete role?'),'deleteRole',{role:role[0].id});role[1](null);}));
            else if(currentMember)content=h(N.View,null,label(person(currentMember.id),{fontSize:20,padding:16}),
                button(t('查看个人资料','View profile'),function(){N.Linking.openURL('https://steamcommunity.com/profiles/'+currentMember.id+'/').catch(function(){});}),
                button(t('禁言成员','Mute member'),function(){edit(t('禁言时长','Mute duration'),'mute',[field(t('秒，0 为解除','Seconds, 0 to lift'),'seconds',3600,true)],{member:currentMember.id});}),
                button(t('移出成员','Kick member'),function(){edit(t('移出成员','Kick member'),'kick',[field(t('禁止重新加入的秒数','Seconds before rejoining'),'seconds',3600,true)],{member:currentMember.id});}),
                button(t('封禁成员','Ban member'),function(){confirm(t('封禁此成员？','Ban this member?'),'ban',{member:currentMember.id,banned:true});}),
                label(t('分配角色','Assign roles'),{padding:16,fontWeight:'600'}),data.roles.map(function(r){return h(SwitchRow,{key:r.id,title:r.name,value:(currentMember.roles||[]).indexOf(r.id)>=0,disabled:busy[0],onChange:function(value){act(value?'addRole':'removeRole',{member:currentMember.id,role:r.id});}});}));
            else if(tab[0]==='info')content=h(N.View,null,data.avatar?h(N.Image,{source:{uri:data.avatar},style:{width:96,height:96,margin:16,borderRadius:4}}):null,label(data.name||group.title,{padding:16,fontSize:22}),label(data.tagline||group.tagline||'',{paddingHorizontal:16,color:'#a7b0bc'}),
                button(t('更换群头像','Change group avatar'),function(){env.request('pickFile').then(function(value){if(!value.cancelled){if(value.image)avatar[1](value);else error[1](t('请选择图片','Please choose an image'));}}).catch(function(e){error[1](ui.errorText(e.message));});}),
                button(t('修改群名称','Rename group'),function(){edit(t('群名称','Group name'),'renameGroup',[field(t('名称','Name'),'name',data.name||group.title)]);}),
                button(t('修改群简介','Edit group description'),function(){edit(t('群简介','Description'),'tagline',[field(t('简介','Description'),'text',data.tagline||group.tagline)]);}),
                button(t('邀请好友','Invite a friend'),function(){tab[1]('invite');}));
            else if(tab[0]==='channels')content=h(N.View,null,button(t('创建文字频道','Create text channel'),function(){edit(t('频道名称','Channel name'),'createChannel',[field(t('名称','Name'),'name','')]);}),
                group.rooms.map(function(room,index){return h(N.View,{key:room.room,style:{padding:12,borderBottomWidth:1,borderColor:'#343944'}},label(room.title,{fontWeight:'600'}),
                    h(N.View,{style:{flexDirection:'row',flexWrap:'wrap'}},button(t('重命名','Rename'),function(){edit(t('频道名称','Channel name'),'renameChannel',[field(t('名称','Name'),'name',room.title)],{room:room.room});}),
                        button(t('上移','Move up'),function(){act('reorderChannel',{room:room.room,after:index>1?group.rooms[index-2].room:''});},index===0),
                        button(t('删除','Delete'),function(){confirm(t('删除频道？','Delete channel?'),'deleteChannel',{room:room.room});})));}));
            else if(tab[0]==='members')content=h(N.View,null,data.members.map(function(m){return h(Button,{key:m.id,title:person(m.id),onPress:function(){member[1](m);}});}),
                label(t('已封禁成员','Banned members'),{padding:16,fontWeight:'600'}),data.bans.map(function(b){return h(N.View,{key:b.id,style:{padding:12}},label(person(b.id)),button(t('解除封禁','Unban'),function(){confirm(t('解除封禁？','Unban member?'),'ban',{member:b.id,banned:false});}));}),
                label(t('待接受邀请','Pending invitations'),{padding:16,fontWeight:'600'}),data.invites.map(function(m){return h(N.View,{key:m.id,style:{padding:12}},label(person(m.id)),button(t('撤销邀请','Revoke invitation'),function(){confirm(t('撤销邀请？','Revoke invitation?'),'revokeInvite',{member:m.id});}));}));
            else if(tab[0]==='roles')content=h(N.View,null,button(t('创建角色','Create role'),function(){edit(t('角色名称','Role name'),'createRole',[field(t('名称','Name'),'name','')]);}),data.roles.map(function(r){return h(Button,{key:r.id,title:r.name,onPress:function(){role[1](JSON.parse(JSON.stringify(r)));}});}));
            else if(tab[0]==='links')content=h(N.View,null,
                button(t('创建一天有效的邀请链接','Create a one-day invitation link'),function(){run('createLink',{seconds:86400,room:props.room||((group.rooms[0]||{}).room||'')}).then(function(result){if(result.code)env.request('copy',{text:'https://s.team/chat/'+result.code}).catch(function(){});}).catch(function(){});}),
                data.links.map(function(link){return h(N.View,{key:link.code,style:{padding:12,borderBottomWidth:1,borderColor:'#343944'}},label('https://s.team/chat/'+link.code,{color:'#66c0f4'},{selectable:true}),
                    button(t('复制','Copy'),function(){env.request('copy',{text:'https://s.team/chat/'+link.code}).catch(function(){});}),button(t('撤销链接','Revoke link'),function(){confirm(t('撤销此链接？','Revoke link?'),'deleteLink',{code:link.code});}));}));
            else if(tab[0]==='invite')content=h(N.View,null,(state.friends||[]).map(function(friend){return h(Button,{key:friend.id,title:friend.name,disabled:busy[0],onPress:function(){confirm(t('邀请此好友？','Invite this friend?'),'invite',{member:friend.id,room:props.room||((group.rooms[0]||{}).room||'')});}});}));
            return h(Panel,{title:form[0]?form[0].title:t('群资料与管理','Group info and management'),close:close},
                !form[0]&&!avatar[0]&&!member[0]&&!role[0]?h(N.ScrollView,{horizontal:true,style:{flexGrow:0}},['info','channels','members','roles','links'].map(function(k,i){return h(Button,{key:k,title:[t('资料','Info'),t('频道','Channels'),t('成员','Members'),t('角色','Roles'),t('链接','Links')][i],onPress:function(){tab[1](k);}});})):null,
                error[0]?label(error[0],{padding:12,color:'#e8a49d'}):null,
                data.unavailable.length?label(t('部分资料因权限或连接原因未能加载','Some sections could not be loaded due to permissions or connection.'),{padding:12,color:'#a7b0bc',fontSize:12}):null,
                busy[0]?h(N.ActivityIndicator,{color:'#66c0f4'}):null,
                h(N.ScrollView,{keyboardShouldPersistTaps:'handled'},content),button(t('刷新','Refresh'),function(){load();}));
        }
        return {Create:Create,Admin:Admin};
    }
""".trimIndent()
}
