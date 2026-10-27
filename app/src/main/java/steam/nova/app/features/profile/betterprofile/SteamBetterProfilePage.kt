package steam.nova.app.features.profile.betterprofile

import org.json.JSONObject

/** Executed only in the main frame of a Steam profile, independently of Steam's own scripts. */
internal object SteamBetterProfilePage {
    fun source(): String {
        val style = JSONObject.quote(SteamBetterProfileStyle.source())
        return $$"""
function(config) {
    'use strict';
    if(window.top!==window)return;
    function profileURL(){return location.protocol==='https:'&&location.hostname==='steamcommunity.com'&&
        /^\/(?:id\/[^/]+|profiles\/[0-9]{17}|my)\/?$/.test(location.pathname);}
    var key=location.origin+location.pathname,previous=window.__steamNovaBetterProfile;
    if(previous&&previous.key===key&&previous.token===config.token){previous.refresh();return;}
    if(previous)previous.destroy();
    if(!profileURL())return;
    var disposed=false,frame=0,mounted=null,observer=null,styleNode=null;
    function send(ready,open){
        if(window.ReactNativeWebView)window.ReactNativeWebView.postMessage(JSON.stringify({
            event_name:'SteamNovaBetterProfile',token:config.token,url:location.href,ready:ready,open:open
        }));
    }
    function node(tag,cls){var el=document.createElement(tag);if(cls)el.className=cls;return el;}
    function fitHeader(state){
        var box=state.headerBox,layout=state.headerLayout;
        if(!box.isConnected||!layout.isConnected)return;
        var width=state.left.getBoundingClientRect().width;
        // Zoom participates in layout, so long biographies grow without leaving a large empty gap.
        if(width>0)layout.style.setProperty('--sn-bp-header-scale',String(width/484));
    }
    function restore(){
        if(!mounted)return;
        setOpen(false);var state=mounted;mounted=null;
        if(state.headerObserver)state.headerObserver.disconnect();
        window.removeEventListener('resize',state.fitHeader);
        if(state.headerScale)state.headerLayout.style.setProperty('--sn-bp-header-scale',state.headerScale,state.headerScalePriority);
        else state.headerLayout.style.removeProperty('--sn-bp-header-scale');
        state.moves.forEach(function(move){
            if(move.anchor.isConnected){move.anchor.parentNode.insertBefore(move.el,move.anchor);move.anchor.remove();}
        });
        state.host.remove();document.documentElement.classList.remove('sn-better-profile');
        if(styleNode){styleNode.remove();styleNode=null;}
    }
    function setOpen(open){
        if(!mounted)return;
        var state=mounted;if(state.open===open)return;
        state.open=open;state.host.dataset.open=String(open);
        if(open){state.host.setAttribute('aria-hidden','false');state.panel.inert=false;}
        document.documentElement.classList.toggle('sn-bp-open',open);
        if(open){
            state.focus=document.activeElement;
            state.blocked=[state.header,state.content].map(function(el){var value={el:el,inert:el.hasAttribute('inert')};el.inert=true;return value;});
            state.close.focus({preventScroll:true});
        }else{
            state.blocked.forEach(function(item){if(!item.inert)item.el.removeAttribute('inert');});state.blocked=[];
            if(state.focus&&state.focus.isConnected&&state.focus!==document.body)state.focus.focus({preventScroll:true});
            if(state.panel.contains(document.activeElement))document.activeElement.blur();
            state.panel.inert=true;state.host.setAttribute('aria-hidden','true');
        }
        send(true,open);
    }
    function install(){
        frame=0;if(disposed)return;
        if(!profileURL()||location.origin+location.pathname!==key){api.destroy();return;}
        var left=document.querySelector('.profile_leftcol'),content=document.querySelector('.profile_content'),
            header=document.querySelector('.profile_header_bg'),headerBox=header&&header.querySelector('.profile_header'),
            headerLayout=header&&header.querySelector('.profile_header_content'),
            persona=headerLayout&&headerLayout.querySelector('.profile_header_centered_persona'),
            avatar=headerLayout&&headerLayout.querySelector('.playerAvatar.profile_header_size');
        if(mounted){
            if(left===mounted.left&&content===mounted.content&&header===mounted.header&&
                headerLayout===mounted.headerLayout&&persona===mounted.persona&&avatar===mounted.avatar&&
                mounted.host.isConnected){fitHeader(mounted);return;}
            restore();
        }
        var right=document.querySelector('.profile_rightcol'),badge=document.querySelector('.profile_header_badgeinfo');
        if(!left||!right||!content||!headerBox||!headerLayout||!persona||!avatar||!right.children.length){send(false,false);return;}
        var root=left.closest('div.profile_page')||document.body;
        styleNode=node('style');styleNode.id='sn-bp-style';styleNode.textContent=$$style;document.head.appendChild(styleNode);
        var host=node('div'),shade=node('div','sn-bp-shade'),panel=node('section','sn-bp-panel'),
            heading=node('div','sn-bp-heading'),title=node('h2'),close=node('button','sn-bp-close'),scroll=node('div','sn-bp-scroll');
        host.id='sn-bp-drawer';host.dataset.open='false';host.setAttribute('aria-hidden','true');
        panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');panel.setAttribute('aria-labelledby','sn-bp-title');panel.inert=true;
        title.id='sn-bp-title';title.textContent=config.labels.title;close.type='button';close.textContent='\u00d7';close.setAttribute('aria-label',config.labels.close);
        heading.append(title,close);panel.append(heading,scroll);host.append(shade,panel);
        var state={host:host,panel:panel,close:close,left:left,content:content,header:header,moves:[],open:false,blocked:[],focus:null,
            headerBox:headerBox,headerLayout:headerLayout,persona:persona,avatar:avatar,headerObserver:null,
            headerScale:headerLayout.style.getPropertyValue('--sn-bp-header-scale'),
            headerScalePriority:headerLayout.style.getPropertyPriority('--sn-bp-header-scale')};
        state.fitHeader=function(){fitHeader(state);};
        function move(el,destination,before){if(!el)return;var anchor=document.createComment('SteamNova profile position');el.before(anchor);state.moves.push({el:el,anchor:anchor});(destination||scroll).insertBefore(el,before||null);}
        try{
            // Hoist above Steam's content stacking context; retain the profile's theme variables.
            var theme=getComputedStyle(root);
            for(var i=0;i<theme.length;i++){var property=theme[i];if(property.indexOf('--')===0)host.style.setProperty(property,theme.getPropertyValue(property));}
            move(badge);move(right);
            // Keep the live avatar/frame nodes and their Steam interactions intact.
            move(avatar,persona,persona.firstChild);
            // Steam moves these previews before the inventory links on narrow screens.
            Array.from(right.querySelectorAll('.responsive_groupfriends_element')).forEach(function(el){move(el,right);});
            document.body.appendChild(host);mounted=state;
            document.documentElement.classList.add('sn-better-profile');
            fitHeader(state);
            if(window.ResizeObserver){state.headerObserver=new ResizeObserver(state.fitHeader);state.headerObserver.observe(headerBox);state.headerObserver.observe(left);}
            window.addEventListener('resize',state.fitHeader);
            shade.addEventListener('click',function(){setOpen(false);});close.addEventListener('click',function(){setOpen(false);});
            scroll.addEventListener('click',function(event){if(event.target.closest('a[href]'))setOpen(false);});
            send(true,false);
        }catch(error){mounted=state;restore();send(false,false);console.warn('[SteamNova BetterProfile] mount failed');}
    }
    function schedule(){if(!disposed&&!frame)frame=requestAnimationFrame(install);}
    function keydown(event){
        if(!mounted||!mounted.open)return;
        if(event.key==='Escape'){event.preventDefault();setOpen(false);return;}
        if(event.key!=='Tab')return;
        var items=Array.from(mounted.panel.querySelectorAll('a[href],button,input,select,textarea,[tabindex]')).filter(function(el){
            return !el.disabled&&el.tabIndex>=0&&el.getClientRects().length;
        });
        var first=items[0]||mounted.close,last=items[items.length-1]||first;
        if(event.shiftKey&&(document.activeElement===first||!mounted.panel.contains(document.activeElement))){event.preventDefault();last.focus();}
        else if(!event.shiftKey&&(document.activeElement===last||!mounted.panel.contains(document.activeElement))){event.preventDefault();first.focus();}
    }
    function hidden(){if(document.hidden)setOpen(false);}
    var api={key:key,token:config.token,refresh:function(){schedule();send(!!mounted,!!mounted&&mounted.open);},
        toggle:function(){if(mounted)setOpen(!mounted.open);},close:function(){setOpen(false);},
        destroy:function(){if(disposed)return;disposed=true;cancelAnimationFrame(frame);if(observer)observer.disconnect();
            restore();document.removeEventListener('keydown',keydown,true);document.removeEventListener('visibilitychange',hidden);
            document.removeEventListener('DOMContentLoaded',schedule);window.removeEventListener('popstate',schedule);window.removeEventListener('hashchange',schedule);
            window.removeEventListener('pagehide',onPageHide);if(window.__steamNovaBetterProfile===api)delete window.__steamNovaBetterProfile;send(false,false);
        }};
    function onPageHide(){setOpen(false);}
    window.__steamNovaBetterProfile=api;
    document.addEventListener('keydown',keydown,true);document.addEventListener('visibilitychange',hidden);
    document.addEventListener('DOMContentLoaded',schedule);window.addEventListener('popstate',schedule);window.addEventListener('hashchange',schedule);
    window.addEventListener('pagehide',onPageHide);
    observer=new MutationObserver(schedule);observer.observe(document.documentElement,{childList:true,subtree:true});schedule();
}
""".trimIndent()
    }
}
