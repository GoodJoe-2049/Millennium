package steam.nova.app.features.profile.betterprofile

/** Scoped overrides: retain Steam's profile theme and its original showcase nodes. */
internal object SteamBetterProfileStyle {
    fun source() = """
html.sn-better-profile .profile_content,
html.sn-better-profile .profile_header_bg_texture,
html.sn-better-profile .profile_header {
    width:100%!important;max-width:676px!important;min-width:0!important;box-sizing:border-box;
    margin-left:auto!important;margin-right:auto!important;
}
html.sn-better-profile .profile_content {padding:12px!important;}
html.sn-better-profile .profile_leftcol {width:100%!important;float:none!important;padding:0!important;min-width:0;}
html.sn-better-profile .profile_header {padding:16px 12px!important;}
html.sn-better-profile .profile_header_bg,
html.sn-better-profile .profile_header_bg_texture {min-height:0!important;}
html.sn-better-profile .profile_header_content {
    /* Scale Steam's information column as one unit; the compact avatar sits beside the name. */
    display:grid!important;width:484px!important;min-width:484px!important;max-width:none!important;
    grid-template-columns:484px!important;grid-template-rows:auto!important;gap:0!important;
    box-sizing:content-box;padding:10px 0 0!important;margin:0!important;
    zoom:var(--sn-bp-header-scale,1);font-size:13px;line-height:normal;
    -webkit-text-size-adjust:none;text-size-adjust:none;
}
html.sn-better-profile .profile_header .playerAvatar.profile_header_size {
    width:168px!important;height:168px!important;box-sizing:border-box;
    max-width:none!important;grid-column:1;grid-row:1;align-self:start;
}
html.sn-better-profile .profile_header_centered_col {
    padding:0 18px 0 36px!important;min-width:0;grid-column:1;grid-row:1;overflow:visible!important;
}
html.sn-better-profile .profile_header .profile_header_centered_persona {
    position:relative!important;box-sizing:border-box;min-height:76px;padding:0 0 18px 70px!important;
}
html.sn-better-profile .profile_header .profile_header_centered_persona > .playerAvatar.profile_header_size {
    position:absolute!important;left:0!important;top:2px!important;right:auto!important;bottom:auto!important;
    margin:0!important;zoom:0.3333333333!important;
}
html.sn-better-profile .profile_header .persona_name {line-height:40px;}
html.sn-better-profile .profile_header .header_real_name {
    height:auto!important;line-height:normal!important;white-space:normal;overflow:visible;text-overflow:clip;
}
html.sn-better-profile .profile_header .header_location {display:inline!important;white-space:normal;overflow-wrap:normal;margin:0!important;}
html.sn-better-profile .profile_header .header_location::before {content:"\00a0"!important;}
html.sn-better-profile .profile_header_summary.desktop {display:block!important;overflow:visible!important;}
html.sn-better-profile .profile_header_summary.mobile,
html.sn-better-profile .profile_header .profile_summary_footer {display:none!important;}
html.sn-better-profile .profile_header .profile_summary {
    height:auto!important;max-height:none!important;overflow:visible!important;line-height:18px!important;
}
html.sn-better-profile .profile_leftcol .showcase_achievementscompletionist_slots {
    display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr))!important;gap:12px 7px;
}
html.sn-better-profile .showcase_achievementscompletionist_game {min-width:0;width:auto!important;}
html.sn-better-profile .showcase_achievementscompletionist_game img.game_capsule {width:100%!important;height:auto!important;}
html.sn-better-profile .showcase_gamecollector_games {display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:6px;}
html.sn-better-profile .showcase_gamecollector_games .showcase_gamecollector_game {
    float:none!important;width:100%!important;height:auto!important;margin:0!important;line-height:normal!important;min-width:0;
}
html.sn-better-profile .showcase_gamecollector_games img.game_capsule {width:100%!important;height:auto!important;}
html.sn-better-profile .favoritegame_showcase_game {padding-left:calc(36% + 12px);min-height:69px;height:auto;}
html.sn-better-profile .favoritegame_showcase_game .favorite_game_cap {width:36%;height:auto;}
html.sn-better-profile .favoritegame_showcase_game .favorite_game_cap img {width:100%;height:auto;}
html.sn-better-profile .profile_customization_block {min-width:0;}
html.sn-better-profile .replay_showcase_block .replay {
    grid-template-areas:"big sessions achievements new_games" "big games games games"!important;
    grid-template-columns:minmax(0,1.15fr) repeat(3,minmax(0,1fr))!important;
    grid-template-rows:auto auto!important;gap:8px 5px;height:auto;min-height:160px;padding:12px;
}
html.sn-better-profile .replay_showcase_block .games_played_ctn,
html.sn-better-profile .replay_showcase_block .stat_ctn {margin:0;min-width:0;text-align:left;}
html.sn-better-profile .replay_showcase_block .games_played_title {font-size:12px;line-height:1.4;text-align:left;}
html.sn-better-profile .replay_showcase_block .big_stat {font-size:clamp(32px,9vw,64px);line-height:1.15;}
html.sn-better-profile .replay_showcase_block .stat_ctn {font-size:11px;line-height:1.5;}
html.sn-better-profile .replay_showcase_block .mid_stat {font-size:clamp(16px,4.6vw,28px);line-height:1.2;}
html.sn-better-profile .replay_showcase_block .most_played_ctn {min-width:0;margin:0;}
html.sn-better-profile .replay_showcase_block .most_played_images_ctn {gap:4px;}
html.sn-better-profile .replay_showcase_block .most_played_app {flex:1;min-width:0;width:auto;}
html.sn-better-profile .replay_showcase_block .most_played_capsule {width:100%;height:auto;}
html.sn-better-profile .replay_showcase_block .most_played_percent {max-width:100%;font-size:10px;}
html.sn-better-profile .showcase_notes {overflow-wrap:anywhere;}
html.sn-better-profile.sn-bp-open,html.sn-bp-open body {overflow:hidden!important;overscroll-behavior:none;}
#sn-bp-drawer {
    position:fixed;inset:0;z-index:2147483000;visibility:hidden;pointer-events:none;
    transition:visibility 240ms;isolation:isolate;
}
#sn-bp-drawer[data-open="true"] {visibility:visible;pointer-events:auto;transition:none;}
#sn-bp-drawer .sn-bp-shade {position:absolute;inset:0;background:rgba(0,0,0,.55);opacity:0;transition:opacity 220ms ease;}
#sn-bp-drawer .sn-bp-panel {
    position:absolute;left:0;top:0;bottom:0;width:min(86vw,340px);box-sizing:border-box;
    display:flex;flex-direction:column;background:#1f2127;color:var(--sn-bp-text,#dfe3e6);
    background-image:linear-gradient(var(--gradient-background,transparent),var(--gradient-background,transparent));
    box-shadow:8px 0 30px rgba(0,0,0,.35);transform:translateX(-105%);
    transition:transform 240ms cubic-bezier(.2,.8,.2,1);overflow:hidden;
}
#sn-bp-drawer[data-open="true"] .sn-bp-shade {opacity:1;}
#sn-bp-drawer[data-open="true"] .sn-bp-panel {transform:translateX(0);}
#sn-bp-drawer .sn-bp-heading {display:flex;align-items:center;justify-content:space-between;padding:6px 8px 6px 16px;flex:none;gap:8px;}
#sn-bp-drawer .sn-bp-heading h2 {font:inherit;font-size:16px;font-weight:600;margin:0;color:inherit;}
#sn-bp-drawer .sn-bp-close {min-width:44px;min-height:44px;background:transparent;border:0;color:inherit;font-size:28px;cursor:pointer;}
#sn-bp-drawer .sn-bp-close:focus-visible {outline:2px solid currentColor;outline-offset:-4px;}
#sn-bp-drawer .sn-bp-scroll {overflow:auto;overscroll-behavior:contain;flex:1;min-height:0;padding:0 12px max(20px,env(safe-area-inset-bottom));}
#sn-bp-drawer .profile_rightcol {width:100%!important;float:none!important;padding:10px!important;box-sizing:border-box;}
#sn-bp-drawer .profile_header_badgeinfo {display:flex!important;flex-direction:column!important;width:100%;box-sizing:border-box;float:none;gap:12px;margin-bottom:12px;}
#sn-bp-drawer .profile_header_badgeinfo_badge_area {display:block!important;padding:10px!important;min-height:0!important;}
#sn-bp-drawer .profile_header_badge {margin:8px 0!important;}
#sn-bp-drawer .profile_header_actions {display:flex;flex-wrap:wrap;gap:8px;}
#sn-bp-drawer .profile_header_badge_instructions {display:block!important;}
#sn-bp-drawer .profile_count_link,
#sn-bp-drawer .profile_count_link_preview_ctn {display:block!important;float:none!important;width:100%!important;height:auto!important;box-sizing:border-box;margin:0 0 10px!important;}
#sn-bp-drawer .profile_count_link_preview_ctn .profile_count_link {margin-bottom:4px!important;}
#sn-bp-drawer .profile_friend_links {width:auto!important;float:none!important;}
#sn-bp-drawer .profile_count_link_preview {height:auto!important;line-height:normal!important;}
#sn-bp-drawer .profile_topfriends .friendBlock {display:block!important;float:none!important;width:100%!important;height:48px!important;margin:0 0 6px!important;box-sizing:border-box;}
#sn-bp-drawer .profile_topfriends .friendBlock > div {display:block!important;}
#sn-bp-drawer .profile_topfriends .friendBlock .playerAvatar {float:left!important;margin:4px 8px 4px 0!important;}
#sn-bp-drawer .profile_group {display:block!important;width:100%!important;min-height:48px;box-sizing:border-box;}
#sn-bp-drawer .profile_group > *:not(.profile_group_avatar) {display:block!important;}
@media(prefers-reduced-motion:reduce) {
    #sn-bp-drawer,#sn-bp-drawer .sn-bp-panel,#sn-bp-drawer .sn-bp-shade {transition:none!important;}
}
""".trimIndent()
}
