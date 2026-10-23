package com.millennium.app.features.chat.ui

internal object SteamChatGroupScript {
    fun source() = """
    function chatGroups(env,ui,actions){
        var R=env.React,N=env.RN,h=R.createElement,t=ui.t,label=ui.label,Button=ui.Button,Panel=actions.Panel,SwitchRow=actions.SwitchRow;
        function Create(props){
            var state=env.useStore(),name=R.useState(props.initialName||''),chosen=R.useState(props.invitees||[]),query=R.useState(''),busy=R.useState(false),error=R.useState('');
            R.useEffect(function(){env.load('friends').catch(function(e){error[1](ui.errorText(e.message));});},[]);
            return h(Panel,{title:t('chat_create_group'),close:props.close},
                h(N.TextInput,{value:name[0],onChangeText:name[1],maxLength:64,placeholder:t('chat_group_name'),placeholderTextColor:'#a7b0bc',style:{padding:16,color:'#dcdedf',fontSize:18,minHeight:52}}),
                h(N.TextInput,{value:query[0],onChangeText:query[1],placeholder:t('chat_invite_friends_optional'),placeholderTextColor:'#a7b0bc',style:{padding:16,color:'#dcdedf',minHeight:48}}),
                error[0]?label(error[0],{padding:12,color:'#e8a49d'}):null,
                h(N.FlatList,{data:(state.friends||[]).filter(function(f){return f.name.toLowerCase().indexOf(query[0].toLowerCase())>=0;}),keyExtractor:function(f){return f.id;},
                    renderItem:function(row){var friend=row.item,selected=chosen[0].indexOf(friend.id)>=0;return h(N.Pressable,{onPress:function(){chosen[1](selected?chosen[0].filter(function(id){return id!==friend.id;}):chosen[0].concat(friend.id));},style:{padding:16,flexDirection:'row',gap:12}},label(selected?'✓':'○',{color:'#66c0f4'}),label(friend.name));}}),
                h(Button,{title:busy[0]?t('chat_creating'):t('chat_create'),primary:true,disabled:busy[0]||!name[0].trim(),onPress:function(){busy[1](true);env.request('groupAction',{action:'createGroup',name:name[0],invitees:chosen[0]}).then(function(value){return env.refresh().then(function(){props.created(value.group);});}).catch(function(e){error[1](ui.errorText(e.message));busy[1](false);});}}));
        }
        function Admin(props){
            var state=env.useStore(),tab=R.useState(props.initialTab||'info'),snapshot=R.useState(null),busy=R.useState(false),error=R.useState(''),form=R.useState(null),values=R.useState({}),member=R.useState(null),role=R.useState(null),avatar=R.useState(null);
            var group=state.groups.find(function(g){return g.id===props.group;})||{id:props.group,rooms:[],title:props.group};
            function load(){return env.request('groupInfo',{group:props.group}).then(function(result){snapshot[1](result);}).catch(function(e){error[1](ui.errorText(e.message));});}
            R.useEffect(function(){load();env.load('friends').catch(function(){});},[props.group]);
            function run(action,args){busy[1](true);error[1]('');return env.request('groupAction',Object.assign({action:action,group:props.group},args||{}))
                .then(function(result){form[1](null);return env.refresh().then(load).then(function(){return result;});})
                .catch(function(e){error[1](ui.errorText(e.message));throw e;}).then(function(result){busy[1](false);return result;},function(e){busy[1](false);throw e;});}
            function act(action,args){run(action,args).catch(function(){});}
            function confirm(title,action,args){N.Alert.alert(title,t('chat_group_change_confirm'),[{text:t('chat_cancel'),style:'cancel'},{text:t('chat_confirm'),onPress:function(){act(action,args);}}]);}
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
                button(t('chat_save'),function(){var args=Object.assign({},form[0].args,values[0]);form[0].fields.forEach(function(f){if(f.numeric)args[f.key]=Number(args[f.key]);});act(form[0].action,args);}));
            else if(avatar[0])content=h(N.View,{style:{padding:16}},h(N.Image,{source:{uri:avatar[0].uri},style:{width:160,height:160,alignSelf:'center'},resizeMode:'contain'}),
                button(t('chat_save_group_avatar'),function(){busy[1](true);env.request('groupAvatar',{selection:avatar[0].selection}).then(function(value){return run('avatar',{sha:value.sha});}).then(function(){avatar[1](null);}).catch(function(e){error[1](ui.errorText(e.message));}).then(function(){busy[1](false);});}));
            else if(role[0])content=h(N.View,null,label(role[0].name,{fontSize:20,padding:16}),
                [t('chat_manage_channels'),t('chat_kick_members'),t('chat_ban_members'),t('chat_invite_members'),t('chat_edit_group_info'),t('chat_send_messages'),t('chat_view_history'),t('chat_manage_roles'),t('chat_assign_roles'),t('chat_mention_everyone'),t('chat_set_broadcast')].map(function(title,i){return h(SwitchRow,{key:i,title:title,value:role[0].permissions[String(i+2)],onChange:function(value){var next=Object.assign({},role[0],{permissions:Object.assign({},role[0].permissions)});next.permissions[String(i+2)]=value;role[1](next);}});}),
                button(t('chat_save_permissions'),function(){act('rolePermissions',{role:role[0].id,permissions:role[0].permissions});}),
                button(t('chat_rename_role'),function(){edit(t('chat_role_name'),'renameRole',[field(t('chat_name'),'name',role[0].name)],{role:role[0].id});}),
                button(t('chat_delete_role'),function(){confirm(t('chat_delete_role_confirm'),'deleteRole',{role:role[0].id});role[1](null);}));
            else if(currentMember)content=h(N.View,null,label(person(currentMember.id),{fontSize:20,padding:16}),
                button(t('chat_view_profile'),function(){N.Linking.openURL('https://steamcommunity.com/profiles/'+currentMember.id+'/').catch(function(){});}),
                button(t('chat_mute_member'),function(){edit(t('chat_mute_duration'),'mute',[field(t('chat_seconds_0_to_lift'),'seconds',3600,true)],{member:currentMember.id});}),
                button(t('chat_kick_member'),function(){edit(t('chat_kick_member'),'kick',[field(t('chat_seconds_before_rejoining'),'seconds',3600,true)],{member:currentMember.id});}),
                button(t('chat_ban_member'),function(){confirm(t('chat_ban_this_member_confirm'),'ban',{member:currentMember.id,banned:true});}),
                label(t('chat_assign_roles'),{padding:16,fontWeight:'600'}),data.roles.map(function(r){return h(SwitchRow,{key:r.id,title:r.name,value:(currentMember.roles||[]).indexOf(r.id)>=0,disabled:busy[0],onChange:function(value){act(value?'addRole':'removeRole',{member:currentMember.id,role:r.id});}});}));
            else if(tab[0]==='info')content=h(N.View,null,data.avatar?h(N.Image,{source:{uri:data.avatar},style:{width:96,height:96,margin:16,borderRadius:4}}):null,label(data.name||group.title,{padding:16,fontSize:22}),label(data.tagline||group.tagline||'',{paddingHorizontal:16,color:'#a7b0bc'}),
                button(t('chat_change_group_avatar'),function(){env.request('pickFile').then(function(value){if(!value.cancelled){if(value.image)avatar[1](value);else error[1](t('chat_please_choose_an_image'));}}).catch(function(e){error[1](ui.errorText(e.message));});}),
                button(t('chat_rename_group'),function(){edit(t('chat_group_name'),'renameGroup',[field(t('chat_name'),'name',data.name||group.title)]);}),
                button(t('chat_edit_group_description'),function(){edit(t('chat_group_description'),'tagline',[field(t('chat_description'),'text',data.tagline||group.tagline)]);}),
                button(t('chat_invite_a_friend'),function(){tab[1]('invite');}));
            else if(tab[0]==='channels')content=h(N.View,null,button(t('chat_create_text_channel'),function(){edit(t('chat_channel_name'),'createChannel',[field(t('chat_name'),'name','')]);}),
                group.rooms.map(function(room,index){return h(N.View,{key:room.room,style:{padding:12,borderBottomWidth:1,borderColor:'#343944'}},label(room.title,{fontWeight:'600'}),
                    h(N.View,{style:{flexDirection:'row',flexWrap:'wrap'}},button(t('chat_rename'),function(){edit(t('chat_channel_name'),'renameChannel',[field(t('chat_name'),'name',room.title)],{room:room.room});}),
                        button(t('chat_move_up'),function(){act('reorderChannel',{room:room.room,after:index>1?group.rooms[index-2].room:''});},index===0),
                        button(t('chat_delete'),function(){confirm(t('chat_delete_channel_confirm'),'deleteChannel',{room:room.room});})));}));
            else if(tab[0]==='members')content=h(N.View,null,data.members.map(function(m){return h(Button,{key:m.id,title:person(m.id),onPress:function(){member[1](m);}});}),
                label(t('chat_banned_members'),{padding:16,fontWeight:'600'}),data.bans.map(function(b){return h(N.View,{key:b.id,style:{padding:12}},label(person(b.id)),button(t('chat_unban'),function(){confirm(t('chat_unban_member_confirm'),'ban',{member:b.id,banned:false});}));}),
                label(t('chat_pending_invitations'),{padding:16,fontWeight:'600'}),data.invites.map(function(m){return h(N.View,{key:m.id,style:{padding:12}},label(person(m.id)),button(t('chat_revoke_invitation'),function(){confirm(t('chat_revoke_invitation_confirm'),'revokeInvite',{member:m.id});}));}));
            else if(tab[0]==='roles')content=h(N.View,null,button(t('chat_create_role'),function(){edit(t('chat_role_name'),'createRole',[field(t('chat_name'),'name','')]);}),data.roles.map(function(r){return h(Button,{key:r.id,title:r.name,onPress:function(){role[1](JSON.parse(JSON.stringify(r)));}});}));
            else if(tab[0]==='links')content=h(N.View,null,
                button(t('chat_create_a_one_day_invitation_link'),function(){run('createLink',{seconds:86400,room:props.room||((group.rooms[0]||{}).room||'')}).then(function(result){if(result.code)env.request('copy',{text:'https://s.team/chat/'+result.code}).catch(function(){});}).catch(function(){});}),
                data.links.map(function(link){return h(N.View,{key:link.code,style:{padding:12,borderBottomWidth:1,borderColor:'#343944'}},label('https://s.team/chat/'+link.code,{color:'#66c0f4'},{selectable:true}),
                    button(t('chat_copy'),function(){env.request('copy',{text:'https://s.team/chat/'+link.code}).catch(function(){});}),button(t('chat_revoke_link'),function(){confirm(t('chat_revoke_link_confirm'),'deleteLink',{code:link.code});}));}));
            else if(tab[0]==='invite')content=h(N.View,null,(state.friends||[]).map(function(friend){return h(Button,{key:friend.id,title:friend.name,disabled:busy[0],onPress:function(){confirm(t('chat_invite_this_friend_confirm'),'invite',{member:friend.id,room:props.room||((group.rooms[0]||{}).room||'')});}});}));
            return h(Panel,{title:form[0]?form[0].title:t('chat_group_info_and_management'),close:close},
                !form[0]&&!avatar[0]&&!member[0]&&!role[0]?h(N.ScrollView,{horizontal:true,style:{flexGrow:0}},['info','channels','members','roles','links'].map(function(k,i){return h(Button,{key:k,title:[t('chat_info'),t('chat_channels'),t('chat_members'),t('chat_roles'),t('chat_links')][i],onPress:function(){tab[1](k);}});})):null,
                error[0]?label(error[0],{padding:12,color:'#e8a49d'}):null,
                data.unavailable.length?label(t('chat_group_sections_unavailable'),{padding:12,color:'#a7b0bc',fontSize:12}):null,
                busy[0]?h(N.ActivityIndicator,{color:'#66c0f4'}):null,
                h(N.ScrollView,{keyboardShouldPersistTaps:'handled'},content),button(t('chat_refresh'),function(){load();}));
        }
        return {Create:Create,Admin:Admin};
    }
""".trimIndent()
}
