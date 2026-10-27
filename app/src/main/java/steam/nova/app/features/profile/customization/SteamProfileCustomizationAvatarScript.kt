package steam.nova.app.features.profile.customization

/** Shared local appearance for Steam's native personas and the module's chat avatars. */
internal object SteamProfileCustomizationAvatarScript {
    fun source() = """
    var profileAvatarProfiles=Object.create(null),profileAvatarListeners=new Set(),profileAvatarChanges=new Map();
    var profileAvatarLoaded=false,profileAvatarPending=null,profileAvatarRevision=0,profileAvatarAppState=null;
    function profileAvatarMedia(scheme){
        var result={};
        ['avatar','frame'].forEach(function(key){
            var item=scheme&&scheme[key];if(!item)return;
            var media={};
            ['image','poster'].forEach(function(field){
                var url=item[field];
                if(typeof url==='string'&&/^https:\/\/shared\.(?:fastly|akamai)\.steamstatic\.com\/community_assets\/images\/items\/[0-9]+\/[A-Za-z0-9_-]+\.(?:jpg|jpeg|png|apng|gif|webp)$/i.test(url))media[field]=url;
            });
            if(media.image||media.poster)result[key]=media;
        });
        return result;
    }
    function profileAvatarEmit(){profileAvatarListeners.forEach(function(listener){listener();});}
    function profileAvatarSaved(id,scheme){
        if(!/^[0-9]{17}$/.test(id))return;
        profileAvatarChanges.set(id,++profileAvatarRevision);
        profileAvatarProfiles[id]=profileAvatarMedia(scheme);profileAvatarEmit();
    }
    function profileAvatarLoad(require){
        if(profileAvatarLoaded||profileAvatarPending)return;
        var revision=profileAvatarRevision;
        // One local snapshot, not a disk/network request for every visible avatar.
        profileAvatarPending=Promise.resolve().then(function(){
            return require(16).Linking.openURL(profileCustomizationPrefix+encodeURIComponent(JSON.stringify({operation:'avatars'})));
        }).then(function(raw){
            var result=JSON.parse(raw);if(!result.ok||!result.data||!result.data.profiles)throw new Error('AVATARS_UNAVAILABLE');
            Object.keys(result.data.profiles).forEach(function(id){
                // A late startup response must never undo a more recent successful save/reset.
                if(/^[0-9]{17}$/.test(id)&&(profileAvatarChanges.get(id)||0)<=revision)
                    profileAvatarProfiles[id]=profileAvatarMedia(result.data.profiles[id]);
            });
            profileAvatarLoaded=true;profileAvatarEmit();
        }).catch(function(){}).finally(function(){profileAvatarPending=null;});
    }
    function useProfileAvatar(require,id){
        var R=require(115),tick=R.useState(0)[1];
        R.useEffect(function(){
            function changed(){tick(function(n){return n+1;});}
            profileAvatarListeners.add(changed);
            if(!profileAvatarAppState)profileAvatarAppState=require(16).AppState.addEventListener('change',function(state){
                if(state==='active')profileAvatarLoad(require);
            });
            profileAvatarLoad(require);changed();
            return function(){
                profileAvatarListeners.delete(changed);
                if(!profileAvatarListeners.size&&profileAvatarAppState){profileAvatarAppState.remove();profileAvatarAppState=null;}
            };
        },[id]);
        // Read by the current ID during render: account switches never reuse the previous avatar.
        return profileAvatarProfiles[id];
    }
    function profileAvatarPersona(exports,require){
        ['usePersonaState','usePersonaStatePreAuth'].forEach(function(name){
            var original=exports[name];if(typeof original!=='function')return;
            exports[name]=function(id){
                var persona=original.apply(this,arguments),custom=useProfileAvatar(require,id);
                return require(115).useMemo(function(){
                    if(!persona||!custom||(!custom.avatar&&!custom.frame))return persona;
                    // Return a display-only copy. Steam's query/auth/storage data stays original.
                    var changed=Object.assign({},persona);
                    if(custom.avatar){
                        var still=custom.avatar.poster||custom.avatar.image;
                        changed.avatar_url=still;changed.avatar_url_medium=still;changed.avatar_url_full=still;
                        changed.animated_avatar=custom.avatar.image||still;
                    }
                    if(custom.frame)changed.avatar_frame=custom.frame.image||custom.frame.poster;
                    return changed;
                },[persona,custom]);
            };
        });
    }
    function profileAvatarStaticImage(base,require){
        var R=require(115);
        return facade(base,'Image',R.forwardRef(function(props,ref){
            return R.createElement(base.Image,Object.assign({},props,{ref:ref,autoplay:false}));
        }));
    }
""".trimIndent()
}
