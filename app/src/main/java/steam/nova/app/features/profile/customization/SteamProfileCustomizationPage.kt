package steam.nova.app.features.profile.customization

import android.content.Context
import org.json.JSONObject

/** Profile-only local preview. No account tokens, equip calls, UA or viewport changes. */
internal object SteamProfileCustomizationPage {
    fun source(context: Context): String {
        val css = JSONObject.quote(SteamProfileCustomizationStyle.source())
        val animationPlayer = SteamProfileCustomizationScript.animationPlayer(context)
        return $$"""
function(config){
    'use strict';
    if(window.top!==window)return;
    function allowed(){return location.protocol==='https:'&&location.hostname==='steamcommunity.com'&&/^\/(?:id\/[^/]+|profiles\/[0-9]{17}|my)\/?$/.test(location.pathname);}
    var key=location.origin+location.pathname,old=window.__steamNovaProfileCustomization;
    if(old&&old.key===key&&old.token===config.token){old.refresh();return;}
    if(old)old.destroy();if(!allowed())return;
    var L=config.labels,disposed=false,profile='',root=null,avatar=null,sheet=null,style=null,observer=null,mountTimer=0;
    var ready=false,opened=false,busy=false,saved={},draft={},undo=[],pending=new Map(),serial=0,epoch=0,loadEpoch=0;
    var category='background',kinds={background:3,avatar:15,frame:14,special:8,mini:13},items=[],cursor='',ended=false,scanned=0,total=0;
    var search='',mode='name',appid=0,gameName='',searchTimer=0,loading=false,loadError=false,stale=false,selection=0;
    var status,grid,scroll,mini,tabs,input,searchMode,more,restore,save,close,handle,body,footer,games,refresh,focusBefore,closeAfterSave=false;
    var filterLabel,filterOptions,filterOpen=false,filterIcon,panelIcon,refreshIcon,iconPlayer=null,icons=[];
    var snap='half',position=0,drag=null,animation=null,miniUndo=[],miniTarget='',miniCurrent=null,miniSignature='';
    var themeControllers=new Set(),themeColorsCache=new Map(),themeProperties=['--gradient-right','--gradient-left','--gradient-background','--gradient-background-right','--gradient-background-left','--color-showcase-header','--gradient-showcase-header-left','--btn-background','--btn-background-hover','--btn-outline'];
    var reduce=window.matchMedia('(prefers-reduced-motion: reduce)');
    function el(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;}
    function button(text,fn,cls){var n=el('button',cls,text);n.type='button';n.addEventListener('click',fn);return n;}
    function copy(value){return JSON.parse(JSON.stringify(value));}
    function createIconPlayer(){
        // Keep the bundled SVG player private; do not replace Steam's own window.lottie.
        var module={exports:{}},exports=module.exports;
        $$animationPlayer
        return module.exports;
    }
    function icon(host,name,fallback){
        var canvas=el('span','sn-pc-motion'),placeholder=el('span','sn-pc-icon-fallback',fallback);
        canvas.setAttribute('aria-hidden','true');placeholder.setAttribute('aria-hidden','true');host.append(canvas,placeholder);
        var value={host:host,canvas:canvas,placeholder:placeholder,name:name,item:null,frame:0,target:0,tick:0,running:false};icons.push(value);return value;
    }
    function stopIcon(value){if(value.tick)cancelAnimationFrame(value.tick);value.tick=0;if(value.item)value.item.pause();value.running=false;}
    function moveIcon(value,target,immediate){
        if(!value)return;value.target=target;stopIcon(value);
        if(!value.item)return;
        if(immediate||reduce.matches||!opened){value.frame=target;value.item.goToAndStop(target,true);return;}
        var start=value.frame,time=performance.now(),duration=value.name==='filter'?200:260;
        if(Math.abs(start-target)<.01)return;
        // Seek the supplied Lottie keyframes, preserving the current frame on rapid reversals.
        function step(now){if(disposed||!value.item)return;var progress=Math.min(1,(now-time)/duration);
            value.frame=start+(target-start)*progress;value.item.goToAndStop(value.frame,true);
            if(progress<1)value.tick=requestAnimationFrame(step);else value.tick=0;}
        value.tick=requestAnimationFrame(step);
    }
    function updateRefreshIcon(){
        if(!refreshIcon||!refreshIcon.item)return;
        var running=opened&&snap!=='small'&&loading&&!document.hidden&&!reduce.matches;
        refresh.setAttribute('aria-busy',String(loading));
        if(running&&!refreshIcon.running){refreshIcon.running=true;refreshIcon.item.loop=true;refreshIcon.item.setSpeed(1.5);refreshIcon.item.play();}
        else if(!running){stopIcon(refreshIcon);refreshIcon.item.goToAndStop(0,true);}
    }
    function initializeIcons(){
        if(iconPlayer)return;
        try{iconPlayer=createIconPlayer();}catch(ignore){return;}
        icons.forEach(function(value){
            var data=config.animations&&config.animations[value.name];if(!data)return;
            try{
                value.item=iconPlayer.loadAnimation({container:value.canvas,renderer:'svg',loop:false,autoplay:false,
                    animationData:copy(data),rendererSettings:{preserveAspectRatio:'xMidYMid meet',progressiveLoad:false}});
                function loaded(){if(disposed)return;value.placeholder.hidden=true;value.frame=value.target;value.item.goToAndStop(value.target,true);updateRefreshIcon();}
                value.item.addEventListener('DOMLoaded',loaded);if(value.item.isLoaded)loaded();
            }catch(ignore){value.placeholder.hidden=false;}
        });
    }
    function setFilterOpen(value,focus){
        filterOpen=value;searchMode.setAttribute('aria-expanded',String(value));filterOptions.setAttribute('aria-hidden',String(!value));filterOptions.inert=!value;
        filterOptions.style.height=(value?filterOptions.firstElementChild.scrollHeight:0)+'px';filterOptions.classList.toggle('sn-pc-filter-open',value);
        moveIcon(filterIcon,value?3:0,false);if(!value&&focus)searchMode.focus({preventScroll:true});
    }
    function chooseMode(value){
        if(mode!==value){mode=value;filterLabel.textContent=mode==='name'?L.byName:L.byGame;
            filterOptions.querySelectorAll('button').forEach(function(b){b.setAttribute('aria-pressed',String(b.dataset.mode===mode));});searchChanged();}
        setFilterOpen(false,true);
    }
    function emit(what){if(window.ReactNativeWebView)window.ReactNativeWebView.postMessage(JSON.stringify(Object.assign({event_name:'SteamNovaProfileCustomization',token:config.token,url:location.href},what)));}
    function state(){emit({type:'state',ready:!!sheet&&!disposed,open:opened});}
    function request(operation,args){
        return new Promise(function(resolve,reject){
            if(disposed){reject(new Error('DISPOSED'));return;}
            var id=String(++serial),timer=setTimeout(function(){pending.delete(id);reject(new Error('TIMEOUT'));},35000);
            pending.set(id,{resolve:resolve,reject:reject,timer:timer});
            emit({type:'request',id:id,args:Object.assign({operation:operation,profile:profile,language:config.language},args||{})});
        });
    }
    function receive(id,response){var p=pending.get(id);if(!p)return;pending.delete(id);clearTimeout(p.timer);if(response&&response.ok)p.resolve(response.data);else p.reject(new Error('REQUEST_FAILED'));}
    function mediaURL(value){return typeof value==='string'&&/^https:\/\/shared\.(?:fastly|akamai)\.steamstatic\.com\/community_assets\/images\/items\/[0-9]+\/[A-Za-z0-9_-]+\.(?:jpg|jpeg|png|apng|gif|webp|mp4|webm)$/i.test(value);}
    function picture(item){
        var p=el('picture'),img=el('img');img.alt='';
        if(mediaURL(item.poster)){var source=el('source');source.media='(prefers-reduced-motion: reduce)';source.srcset=item.poster;p.append(source);}
        if(mediaURL(item.image))img.src=item.image;p.append(img);return p;
    }
    function video(item){
        var v=el('video');v.muted=true;v.defaultMuted=true;v.loop=true;v.playsInline=true;v.autoplay=!reduce.matches;v.preload='metadata';
        v.setAttribute('playsinline','');v.setAttribute('muted','');if(mediaURL(item.poster))v.poster=item.poster;
        if(!reduce.matches){['webm','mp4'].forEach(function(kind){if(mediaURL(item[kind])){var s=el('source');s.src=item[kind];s.type='video/'+kind;v.append(s);}});}
        return v;
    }
    function resume(v){if(v&&!reduce.matches){var p=v.play();if(p&&p.catch)p.catch(function(){});}}
    function patchStyle(node,name,value,list){var hadStyle=node.hasAttribute('style'),before=node.style.getPropertyValue(name),priority=node.style.getPropertyPriority(name);node.style.setProperty(name,value);
        (list||undo).push(function(){if(before)node.style.setProperty(name,before,priority);else node.style.removeProperty(name);if(!hadStyle&&!node.style.length)node.removeAttribute('style');});}
    function insert(node,parent,before,list){parent.insertBefore(node,before||null);(list||undo).push(function(){if(node.tagName==='VIDEO')node.pause();node.querySelectorAll('video').forEach(function(v){v.pause();});node.remove();});}
    function hide(node,list){if(!node)return;patchStyle(node,'display','none',list);node.querySelectorAll('video').forEach(function(v){var playing=!v.paused;v.pause();(list||undo).push(function(){if(playing&&node.isConnected)resume(v);});});}
    function restoreList(list){list.splice(0).reverse().forEach(function(fn){fn();});}
    function themeDrawer(){var drawer=document.getElementById('sn-bp-drawer');if(!drawer||!root)return;var computed=getComputedStyle(root);
        Array.from(drawer.style).filter(function(p){return p.indexOf('--')===0;}).forEach(function(p){drawer.style.removeProperty(p);});
        for(var i=0;i<computed.length;i++)if(computed[i].indexOf('--')===0)drawer.style.setProperty(computed[i],computed.getPropertyValue(computed[i]));}
    function loadTheme(item){
        if(!/^GameProfile/.test(item.theme))return Promise.resolve(item);
        if(item.colors&&item.colors['--gradient-background'])return Promise.resolve(item);
        if(themeColorsCache.has(item.id))return Promise.resolve(Object.assign({},item,{theme:'GameProfileTheme',colors:themeColorsCache.get(item.id)}));
        var controller=new AbortController(),timer=setTimeout(function(){controller.abort();},20000);themeControllers.add(controller);
        // Steam renders per-game colors in its preview HTML. Read only those colors; never execute the document.
        return fetch('/my/?previewprofile=1&appid='+encodeURIComponent(item.appid)+'&itemtype='+encodeURIComponent(item.itemtype),
            {credentials:'same-origin',signal:controller.signal}).then(function(response){if(!response.ok)throw new Error('THEME_HTTP');return response.text();})
            .then(function(html){
                if(html.length>4*1024*1024)throw new Error('THEME_TOO_LARGE');
                var parsed=new DOMParser().parseFromString(html,'text/html'),colors={};
                parsed.querySelectorAll('style').forEach(function(style){
                    var rule=style.textContent.match(/body\.GameProfileTheme\s*\{([^}]+)\}/);if(!rule)return;
                    rule[1].split(';').forEach(function(declaration){var colon=declaration.indexOf(':'),name=declaration.slice(0,colon).trim(),value=declaration.slice(colon+1).trim();
                        if(themeProperties.includes(name)&&value.length<=100&&CSS.supports('color',value))colors[name]=value;});
                });
                if(!colors['--gradient-background'])throw new Error('THEME_MISSING');
                themeColorsCache.set(item.id,colors);return Object.assign({},item,{theme:'GameProfileTheme',colors:colors});
            }).finally(function(){clearTimeout(timer);themeControllers.delete(controller);});
    }
    function apply(){
        restoreList(undo);restoreList(miniUndo);miniCurrent=null;miniSignature='';if(!root||!avatar)return;
        var theme=draft.special&&draft.special.theme;
        if(theme&&/^[A-Za-z0-9_]+Theme$/.test(theme)){
            var original=Array.from(document.body.classList).filter(function(c){return /^[A-Za-z0-9_]+Theme$/.test(c);});
            original.forEach(function(c){document.body.classList.remove(c);});document.body.classList.add(theme);
            undo.push(function(){document.body.classList.remove(theme);original.forEach(function(c){document.body.classList.add(c);});});
        }
        if(draft.special&&draft.special.colors)themeProperties.forEach(function(name){var value=draft.special.colors[name];if(typeof value==='string'&&CSS.supports('color',value))patchStyle(document.body,name,value);});
        var bg=draft.background;
        if(bg){
            var originalBg=root.querySelector(':scope > .profile_animated_background');hide(originalBg);
            var had=root.classList.contains('has_profile_background');root.classList.add('has_profile_background');
            undo.push(function(){if(!had)root.classList.remove('has_profile_background');});
            patchStyle(root,'background-image',mediaURL(bg.poster)?'url("'+bg.poster+'")':'none');
            patchStyle(root,'background-repeat',bg.tiled?'repeat':'no-repeat');patchStyle(root,'background-position','center top');
            patchStyle(root,'background-size',bg.tiled?'auto':'cover');
            if(bg.webm||bg.mp4){var holder=el('div','profile_animated_background');var v=video(bg);holder.append(v);insert(holder,root,root.firstChild);resume(v);}
        }
        if(draft.avatar){var originalPicture=avatar.querySelector(':scope > picture')||avatar.querySelector(':scope > img');hide(originalPicture);insert(picture(draft.avatar),avatar);}
        if(draft.frame){hide(avatar.querySelector(':scope > .profile_avatar_frame'));var frame=el('div','profile_avatar_frame');frame.append(picture(draft.frame));insert(frame,avatar,avatar.firstChild);}
        themeDrawer();renderMini();patchMini();
    }
    function renderMini(){
        if(!mini)return;mini.replaceChildren();mini.hidden=category!=='mini';if(category!=='mini')return;
        mini.setAttribute('aria-label',L.miniPreview);var item=draft.mini;
        if(item){if(mediaURL(item.poster))mini.style.backgroundImage='url("'+item.poster+'")';if(item.mp4||item.webm){var v=video(item);mini.append(v);resume(v);}}
        else mini.style.removeProperty('background-image');
        var originalPicture=avatar&&avatar.querySelector(':scope > picture'),p=draft.avatar?picture(draft.avatar):originalPicture&&originalPicture.cloneNode(true);
        if(p){p.style.removeProperty('display');mini.append(p);}
        var f=draft.frame?picture(draft.frame):avatar&&avatar.querySelector('.profile_avatar_frame picture')?.cloneNode(true);
        if(f){f.classList.add('sn-pc-mini-frame');mini.append(f);}
        mini.append(el('strong','',window.g_rgProfileData?.personaname||document.querySelector('.actual_persona_name')?.textContent||''));
    }
    function patchMini(){
        // Scope overrides to the current profile's hover card; preserve Steam's live nodes.
        var container=document.querySelector('.miniprofile_hover .miniprofile_container'),box=container&&container.querySelector('.miniprofile_playersection');
        var signature=JSON.stringify([draft.mini,draft.avatar,draft.frame]);
        if(!box||miniTarget!==profile){if(miniUndo.length)restoreList(miniUndo);miniCurrent=null;return;}
        if(box===miniCurrent&&signature===miniSignature)return;
        restoreList(miniUndo);miniCurrent=box;miniSignature=signature;
        if(draft.mini){
            hide(container.querySelector('.miniprofile_nameplatecontainer'),miniUndo);
            var plate=el('div','miniprofile_nameplatecontainer'),v=video(draft.mini);v.className='miniprofile_nameplate';plate.append(v);
            insert(plate,container,container.firstChild,miniUndo);resume(v);
            [box,container.querySelector('.miniprofile_detailssection')].forEach(function(node,index){if(!node)return;
                var name=index?'miniprofile_backdropblur':'text_shadow',had=node.classList.contains(name);node.classList.add(name);
                miniUndo.push(function(){if(!had)node.classList.remove(name);});});
        }
        if(draft.avatar){var imageBox=box.querySelector('.playersection_avatar');if(imageBox){
            Array.from(imageBox.children).forEach(function(child){hide(child,miniUndo);});var avatarPicture=picture(draft.avatar);
            avatarPicture.style.cssText='display:block;width:100%;height:100%';avatarPicture.querySelector('img').style.cssText='width:100%;height:100%';insert(avatarPicture,imageBox,null,miniUndo);}}
        if(draft.frame){hide(box.querySelector('.playersection_avatar_frame'),miniUndo);var frame=el('div','playersection_avatar_frame');
            frame.append(picture(draft.frame));frame.querySelector('img').style.cssText='width:100%;height:100%';insert(frame,box,box.firstChild,miniUndo);}
    }
    function pointerover(event){var target=event.target.closest&&event.target.closest('[data-miniprofile]');if(!target)return;
        try{miniTarget=String(BigInt(target.dataset.miniprofile)+76561197960265728n);}catch(ignore){miniTarget='';}patchMini();}
    function setStatus(text){if(status){status.textContent=text;status.hidden=!text;}}
    function updateButtons(){if(!sheet)return;save.disabled=busy||!ready;restore.disabled=busy||!ready;close.disabled=busy;refresh.disabled=busy||loading||!ready;
        searchMode.disabled=busy||!ready;input.disabled=busy||!ready;filterOptions.querySelectorAll('button').forEach(function(b){b.disabled=busy||!ready;});
        tabs.querySelectorAll('button').forEach(function(b){b.disabled=busy||!ready;});updateRefreshIcon();}
    function renderSelection(){grid.querySelectorAll('[data-id]').forEach(function(card){card.setAttribute('aria-pressed',String(card.dataset.id===(draft[category]?.id||'')));});updateButtons();}
    function choose(item){
        if(!ready||busy)return;var id=++selection,cat=category;
        if(!item){delete draft[cat];apply();renderSelection();return;}
        if(cat!=='special'){draft[cat]=item;apply();renderSelection();return;}
        busy=true;updateButtons();setStatus(L.loading);
        var ids=item.bundle||[];
        Promise.all([ids.length?request('bundle',{ids:ids}):Promise.resolve({items:[]}),loadTheme(item)]).then(function(results){
            var result=results[0],themed=results[1];
            if(disposed||!opened||id!==selection)return;
            var returned=new Set(result.items.map(function(i){return Number(i.id);}));
            if(ids.some(function(def){return !returned.has(Number(def));}))throw new Error('INCOMPLETE_BUNDLE');
            draft.special=themed;result.items.forEach(function(part){Object.keys(kinds).forEach(function(k){if(k!=='special'&&kinds[k]===part.kind)draft[k]=part;});});
            apply();setStatus('');
        }).catch(function(){if(!disposed&&id===selection)setStatus(L.bundleError);}).finally(function(){if(id===selection){busy=false;updateButtons();renderSelection();}});
    }
    function render(){
        if(!grid)return;grid.classList.toggle('sn-pc-square',category==='avatar'||category==='frame');grid.replaceChildren();
        var original=button(L.original,function(){choose(null);},'sn-pc-card sn-pc-original');original.dataset.id='';grid.append(original);
        var query=search.toLocaleLowerCase();
        items.filter(function(item){return mode==='game'||!query||item.name.toLocaleLowerCase().includes(query);}).forEach(function(item){
            var card=button('',function(){choose(item);},'sn-pc-card'),img=el('img');img.alt='';img.loading='lazy';img.decoding='async';
            if(mediaURL(item.thumb||item.poster))img.src=item.thumb||item.poster;
            card.dataset.id=item.id;card.title=item.name;card.append(img,el('span','',item.name));grid.append(card);
        });
        more.hidden=ended&&!loadError;more.disabled=loading||busy;more.textContent=loadError?L.retry:(search&&mode==='name'?L.continueSearch:L.more);
        renderSelection();
    }
    function catalogStatus(){
        if(stale)return L.cached;
        if(search&&mode==='name')return L.scan.replace('{count}',String(scanned)).replace('{total}',String(total))+(ended&&grid.children.length===1?' \u00b7 '+L.noMatches:'');
        return ended&&items.length===0?L.noMatches:'';
    }
    function loadPage(force){
        if(loading||ended||disposed||!opened||!ready)return;
        if(mode==='game'&&!appid&&search){findGames();return;}
        var version=epoch,startCursor=cursor;loading=true;loadError=false;render();updateButtons();setStatus(L.loading);
        request('catalog',{kind:kinds[category],cursor:cursor,appid:appid,refresh:!!force}).then(function(result){
            if(disposed||version!==epoch)return;
            var known=new Set(items.map(function(i){return i.id;}));result.items.forEach(function(i){if(!known.has(i.id)){known.add(i.id);items.push(i);}});
            scanned+=result.items.length;total=result.total||0;cursor=result.cursor||'';
            ended=!cursor||cursor===startCursor||!result.items.length||scanned>=total;
            stale=result.stale===true;render();setStatus(catalogStatus());
        }).catch(function(){if(!disposed&&version===epoch){loadError=true;setStatus(L.error);}}).finally(function(){if(version===epoch){loading=false;render();updateButtons();}});
    }
    function resetCatalog(force){epoch++;loading=false;loadError=false;items=[];cursor='';ended=false;scanned=0;total=0;stale=false;games.replaceChildren();scroll.scrollTop=0;render();renderMini();loadPage(force);}
    function findGames(){
        var version=epoch;loading=true;updateButtons();setStatus(L.loading);more.hidden=true;
        var term=search.trim();
        var promise=/^[0-9]{1,9}$/.test(term)?Promise.resolve({items:[{id:Number(term),name:term}]}):request('games',{query:term});
        promise.then(function(result){if(disposed||version!==epoch)return;games.replaceChildren();grid.replaceChildren();
            result.items.forEach(function(game){games.append(button(game.name,function(){appid=game.id;gameName=game.name;resetCatalog(false);},''));});setStatus(result.items.length?L.byGame:L.noMatches);
        }).catch(function(){if(version===epoch){setStatus(L.error);loadError=true;}}).finally(function(){if(version===epoch){loading=false;more.hidden=!loadError;more.disabled=false;more.textContent=L.retry;updateButtons();}});
    }
    function searchChanged(){clearTimeout(searchTimer);epoch++;loading=false;updateButtons();search=input.value.trim().slice(0,100);appid=0;gameName='';
        searchTimer=setTimeout(function(){if(opened)resetCatalog(false);},350);}
    function height(){return Math.min(window.visualViewport?window.visualViewport.height:innerHeight,innerHeight)-12;}
    function targetPosition(which){var h=Math.max(120,height());return which==='large'?0:which==='small'?Math.max(0,h-74):Math.max(0,h-Math.max(300,h*.56));}
    function positionSheet(y){position=y;sheet.style.transform='translateY('+y+'px)';var visible=Math.max(0,height()-y);
        // The panel translates as a single surface; its inner viewport follows the available space.
        body.style.height=Math.max(0,visible-handle.offsetHeight-footer.offsetHeight)+'px';
        body.inert=visible<=90;footer.inert=visible<=90;body.setAttribute('aria-hidden',String(visible<=90));footer.setAttribute('aria-hidden',String(visible<=90));}
    function cancelAnimation(){if(animation){cancelAnimationFrame(animation);animation=null;}}
    function settle(which,velocity){
        snap=which;var target=targetPosition(which);cancelAnimation();moveIcon(panelIcon,which==='large'?30:0,false);updateRefreshIcon();
        if(which==='small'&&filterOpen)setFilterOpen(false,false);
        if(reduce.matches||!velocity){sheet.style.transition='';positionSheet(target);return;}
        // Critically damped spring, retaining gesture velocity. Only translate the outer surface.
        var speed=velocity,previous=performance.now();sheet.style.transition='none';
        function step(now){var dt=Math.min(.032,(now-previous)/1000);previous=now;speed+=(-260*(position-target)-32*speed)*dt;
            positionSheet(Math.max(0,Math.min(height()-60,position+speed*dt)));
            if(Math.abs(position-target)<.5&&Math.abs(speed)<5){positionSheet(target);sheet.style.transition='';animation=null;}else animation=requestAnimationFrame(step);}
        animation=requestAnimationFrame(step);
    }
    function openPanel(){
        if(disposed||!sheet||opened||busy)return;
        window.__steamNovaBetterProfile?.close();opened=true;draft=copy(saved);focusBefore=document.activeElement;initializeIcons();
        sheet.inert=false;sheet.setAttribute('aria-hidden','false');settle('half',0);state();apply();if(!ready)loadSaved();else resetCatalog(false);
        // Keep the keyboard closed until the user chooses to search.
        close.focus({preventScroll:true});
    }
    function closePanel(){
        if(!opened)return;
        if(busy&&save.dataset.saving==='true'){closeAfterSave=true;}
        opened=false;setFilterOpen(false,false);themeControllers.forEach(function(c){c.abort();});if(save.dataset.saving!=='true')busy=false;selection++;epoch++;loading=false;clearTimeout(searchTimer);cancelAnimation();
        icons.forEach(stopIcon);updateRefreshIcon();
        draft=copy(saved);apply();sheet.style.transition='';sheet.style.transform='translateY(105%)';sheet.inert=true;sheet.setAttribute('aria-hidden','true');
        if(sheet.contains(document.activeElement))document.activeElement.blur();if(focusBefore&&focusBefore.isConnected)focusBefore.focus({preventScroll:true});state();
    }
    function saveScheme(){
        if(!ready||busy)return;busy=true;save.dataset.saving='true';updateButtons();setStatus(L.saving);
        var value=copy(draft);request('save',{scheme:value}).then(function(result){if(disposed)return;saved=copy(result.scheme);draft=copy(saved);apply();setStatus(L.saved);})
            .catch(function(){if(!disposed)setStatus(L.error);}).finally(function(){busy=false;save.dataset.saving='false';updateButtons();if(closeAfterSave){closeAfterSave=false;closePanel();}});
    }
    function loadSaved(){
        var version=++loadEpoch;setStatus(L.loading);updateButtons();
        request('load').then(function(result){if(disposed||version!==loadEpoch)return;saved=copy(result.scheme);draft=copy(saved);ready=true;apply();updateButtons();state();if(opened)resetCatalog(false);})
            .catch(function(){if(disposed||version!==loadEpoch)return;setStatus(L.error);more.hidden=false;more.disabled=false;more.textContent=L.retry;state();});
    }
    function resize(){if(opened&&!drag)settle(snap,0);if(filterOpen)filterOptions.style.height=filterOptions.firstElementChild.scrollHeight+'px';}
    function keydown(event){if(!opened)return;if(event.key==='Escape'){event.preventDefault();if(filterOpen)setFilterOpen(false,true);else closePanel();}}
    function motionChanged(){apply();icons.forEach(function(value){if(value!==refreshIcon)moveIcon(value,value.target,true);});updateRefreshIcon();if(opened)settle(snap,0);}
    function outsideFilter(event){if(filterOpen&&!searchMode.contains(event.target)&&!filterOptions.contains(event.target))setFilterOpen(false,false);}
    function hidden(){if(document.hidden)closePanel();}
    function build(){
        style=el('style');style.id='sn-pc-style';style.textContent=$$css;document.head.append(style);
        sheet=el('section');sheet.id='sn-pc-sheet';sheet.setAttribute('role','dialog');sheet.setAttribute('aria-modal','false');sheet.setAttribute('aria-labelledby','sn-pc-title');sheet.setAttribute('aria-hidden','true');sheet.inert=true;
        handle=el('div','sn-pc-handle');var title=el('h2','',L.title);title.id='sn-pc-title';
        var resizeButton=button('',function(){settle(snap==='large'?'small':snap==='small'?'half':'large',0);},'sn-pc-icon');resizeButton.setAttribute('aria-label',L.resize);
        panelIcon=icon(resizeButton,'panel','\u2195');
        close=button('\u00d7',closePanel,'sn-pc-icon');close.setAttribute('aria-label',L.close);
        handle.append(title,resizeButton,close);
        handle.addEventListener('pointerdown',function(event){if(drag||event.target.closest('button')||event.button!==0)return;
            cancelAnimation();try{position=new DOMMatrix(getComputedStyle(sheet).transform).m42;}catch(ignore){}sheet.style.transition='none';positionSheet(position);drag={id:event.pointerId,start:event.clientY,y:position,last:event.clientY,time:performance.now(),speed:0};handle.setPointerCapture(event.pointerId);});
        handle.addEventListener('pointermove',function(event){if(!drag||event.pointerId!==drag.id)return;var now=performance.now();drag.speed=(event.clientY-drag.last)/Math.max(1,now-drag.time)*1000;drag.last=event.clientY;drag.time=now;positionSheet(Math.max(0,Math.min(height()-60,drag.y+event.clientY-drag.start)));});
        function release(event){if(!drag||event.pointerId!==drag.id)return;var speed=event.type==='pointercancel'?0:drag.speed;drag=null;
            var projected=position+speed*.12,best=['large','half','small'].sort(function(a,b){return Math.abs(targetPosition(a)-projected)-Math.abs(targetPosition(b)-projected);})[0];settle(best,speed||1);}
        handle.addEventListener('pointerup',release);handle.addEventListener('pointercancel',release);
        body=el('div','sn-pc-body');tabs=el('div','sn-pc-tabs');tabs.setAttribute('role','tablist');
        Object.keys(kinds).forEach(function(k){var tab=button(L[k],function(){if(busy||!ready)return;category=k;tabs.querySelectorAll('button').forEach(function(b){b.setAttribute('aria-selected',String(b===tab));});resetCatalog(false);});
            tab.setAttribute('role','tab');tab.setAttribute('aria-selected',String(k===category));tabs.append(tab);});
        var searchArea=el('div','sn-pc-search-area'),searchRow=el('div','sn-pc-search');
        searchMode=button('',function(){setFilterOpen(!filterOpen,false);},'sn-pc-filter-toggle');searchMode.setAttribute('aria-expanded','false');searchMode.setAttribute('aria-controls','sn-pc-filter-options');
        filterLabel=el('span','',L.byName);searchMode.append(filterLabel);filterIcon=icon(searchMode,'filter','\u2304');
        filterOptions=el('div','sn-pc-filter-options');filterOptions.id='sn-pc-filter-options';filterOptions.setAttribute('aria-hidden','true');filterOptions.inert=true;
        var choices=el('div','sn-pc-filter-choices');choices.setAttribute('role','group');choices.setAttribute('aria-label',L.search);
        [['name',L.byName],['game',L.byGame]].forEach(function(pair){var option=button(pair[1],function(){chooseMode(pair[0]);});option.dataset.mode=pair[0];option.setAttribute('aria-pressed',String(pair[0]===mode));choices.append(option);});filterOptions.append(choices);
        input=el('input');input.type='search';input.maxLength=100;input.placeholder=L.search;input.setAttribute('aria-label',L.search);
        input.addEventListener('input',searchChanged);
        input.addEventListener('focus',function(){settle('large',0);});
        refresh=button('',function(){clearTimeout(searchTimer);appid=0;gameName='';resetCatalog(true);},'sn-pc-icon');refresh.setAttribute('aria-label',L.refresh);refreshIcon=icon(refresh,'refresh','\u21bb');searchRow.append(searchMode,input,refresh);searchArea.append(searchRow,filterOptions);
        status=el('div','sn-pc-status',L.loading);status.setAttribute('role','status');status.setAttribute('aria-live','polite');
        scroll=el('div','sn-pc-scroll');mini=el('div','sn-pc-mini');mini.hidden=true;games=el('div','sn-pc-games');grid=el('div','sn-pc-grid');
        more=button(L.more,function(){if(!ready)loadSaved();else loadPage(false);},'sn-pc-more');scroll.append(mini,games,grid,more);body.append(tabs,searchArea,status,scroll);
        footer=el('div','sn-pc-footer');restore=button(L.restore,function(){if(busy||!ready)return;draft={};apply();renderSelection();setStatus('');});save=button(L.save,saveScheme);
        footer.append(restore,save);sheet.append(handle,body,footer);document.body.append(sheet);updateButtons();state();
    }
    function mount(){
        mountTimer=0;if(disposed)return;if(!allowed()||location.origin+location.pathname!==key){api.destroy();return;}
        if(sheet){
            if(!root.isConnected||!avatar.isConnected){var nextRoot=document.querySelector('div.profile_page'),nextAvatar=document.querySelector('.profile_header .playerAvatarAutoSizeInner');
                if(nextRoot&&nextAvatar){restoreList(undo);root=nextRoot;avatar=nextAvatar;apply();}}
            patchMini();return;
        }
        var data=window.g_rgProfileData,id=data&&String(data.steamid||'');
        if(!/^[0-9]{17}$/.test(id))id=(location.pathname.match(/^\/profiles\/([0-9]{17})/)||[])[1]||'';
        var page=document.querySelector('div.profile_page'),image=document.querySelector('.profile_header .playerAvatarAutoSizeInner');
        if(!page||!image||!id)return;profile=id;root=page;avatar=image;build();loadSaved();
    }
    function schedule(){if(!disposed&&!mountTimer)mountTimer=setTimeout(mount,80);}
    function visibility(){hidden();if(!document.hidden)schedule();}
    var api={key:key,token:config.token,receive:receive,refresh:function(){schedule();state();},toggle:function(){if(opened)closePanel();else openPanel();},close:closePanel,
        destroy:function(){if(disposed)return;closePanel();disposed=true;epoch++;loadEpoch++;clearTimeout(mountTimer);clearTimeout(searchTimer);cancelAnimation();
            if(observer)observer.disconnect();pending.forEach(function(p){clearTimeout(p.timer);p.reject(new Error('DISPOSED'));});pending.clear();
            icons.forEach(function(value){stopIcon(value);if(value.item)value.item.destroy();});icons=[];if(iconPlayer)iconPlayer.destroy();iconPlayer=null;
            restoreList(miniUndo);restoreList(undo);themeDrawer();if(sheet)sheet.remove();if(style)style.remove();
            document.removeEventListener('pointerover',pointerover,true);document.removeEventListener('keydown',keydown,true);document.removeEventListener('visibilitychange',visibility);
            document.removeEventListener('DOMContentLoaded',schedule);window.removeEventListener('pagehide',closePanel);window.removeEventListener('popstate',schedule);
            window.removeEventListener('resize',resize);window.visualViewport?.removeEventListener('resize',resize);reduce.removeEventListener('change',motionChanged);document.removeEventListener('pointerdown',outsideFilter,true);
            if(window.__steamNovaProfileCustomization===api)delete window.__steamNovaProfileCustomization;ready=false;opened=false;state();}};
    window.__steamNovaProfileCustomization=api;
    document.addEventListener('pointerover',pointerover,true);document.addEventListener('keydown',keydown,true);document.addEventListener('visibilitychange',visibility);
    document.addEventListener('DOMContentLoaded',schedule);window.addEventListener('pagehide',closePanel);window.addEventListener('popstate',schedule);
    window.addEventListener('resize',resize);window.visualViewport?.addEventListener('resize',resize);reduce.addEventListener('change',motionChanged);document.addEventListener('pointerdown',outsideFilter,true);
    observer=new MutationObserver(schedule);observer.observe(document.documentElement,{childList:true,subtree:true});schedule();
}
""".trimIndent()
    }
}
