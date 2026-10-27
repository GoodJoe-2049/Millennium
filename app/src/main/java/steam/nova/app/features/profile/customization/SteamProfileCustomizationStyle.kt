package steam.nova.app.features.profile.customization

internal object SteamProfileCustomizationStyle {
    fun source(): String = """
#sn-pc-sheet{--sn-pc-ease:cubic-bezier(.32,.72,0,1);position:fixed;z-index:2147483001;inset:auto 0 0;margin:0 auto;width:100%;max-width:680px;height:calc(100% - 12px);box-sizing:border-box;border:1px solid #3b4b60;border-bottom:0;border-radius:22px 22px 0 0;background:#172231;color:#dfe3e6;box-shadow:0 -8px 28px #0006;font:14px/1.4 Arial,sans-serif;transform:translateY(105%);transition:transform 260ms var(--sn-pc-ease),opacity 160ms ease;display:flex;flex-direction:column;text-align:left;color-scheme:dark;isolation:isolate}
#sn-pc-sheet *,#sn-pc-sheet *:before,#sn-pc-sheet *:after{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
#sn-pc-sheet [hidden]{display:none!important}
#sn-pc-sheet button,#sn-pc-sheet input{font:inherit;color:inherit;letter-spacing:normal}
#sn-pc-sheet button{appearance:none;border:0;background:transparent;cursor:pointer;touch-action:manipulation;-webkit-tap-highlight-color:transparent}
#sn-pc-sheet button:disabled{opacity:.45;cursor:default}
#sn-pc-sheet button:focus-visible,#sn-pc-sheet input:focus-visible{outline:2px solid #a6b2bf;outline-offset:2px}
#sn-pc-sheet .sn-pc-handle{touch-action:none;cursor:grab;min-height:70px;padding:10px 16px 12px;display:flex;align-items:center;gap:10px;flex-shrink:0;position:relative;user-select:none}
#sn-pc-sheet .sn-pc-handle:before{content:'';position:absolute;width:38px;height:4px;border-radius:3px;background:#647588;top:7px;left:calc(50% - 19px)}
#sn-pc-sheet h2{font-size:17px;font-weight:600;line-height:1.3;margin:12px 0 0;color:#fff;flex:1}
#sn-pc-sheet .sn-pc-icon{width:40px;height:40px;font-size:24px;flex-shrink:0;padding:0;margin-top:8px;display:grid;place-items:center;position:relative}
#sn-pc-sheet .sn-pc-motion{display:block;width:26px;height:26px;pointer-events:none;grid-area:1/1}
#sn-pc-sheet .sn-pc-motion svg{display:block;width:100%;height:100%;overflow:visible}
#sn-pc-sheet .sn-pc-motion svg path[fill]:not([fill="none"]){fill:currentColor}
#sn-pc-sheet .sn-pc-motion svg path[stroke]:not([stroke="none"]){stroke:currentColor}
#sn-pc-sheet .sn-pc-icon-fallback{grid-area:1/1;pointer-events:none}
#sn-pc-sheet .sn-pc-body{display:flex;flex-direction:column;min-height:0;overflow:hidden}
#sn-pc-sheet .sn-pc-tabs{display:grid;grid-template-columns:minmax(0,1.2fr) minmax(0,.7fr) minmax(0,1fr) minmax(0,1.05fr) minmax(0,1.05fr);gap:2px;overflow:visible;flex-shrink:0;padding:0 8px 8px}
#sn-pc-sheet .sn-pc-tabs button{min-width:0;min-height:40px;padding:6px 2px;font-size:12px;line-height:1.25;white-space:normal;overflow-wrap:normal;border-radius:9px;color:#adb9c6;text-align:center}
#sn-pc-sheet .sn-pc-tabs button[aria-selected=true]{background:#294459;color:#9ddfff}
#sn-pc-sheet .sn-pc-search-area{margin:0 12px 8px;flex-shrink:0;min-width:0}
#sn-pc-sheet .sn-pc-search{display:flex;align-items:center;gap:6px;min-width:0}
#sn-pc-sheet .sn-pc-search input,#sn-pc-sheet .sn-pc-filter-toggle{appearance:none;background:#0e1825;border:1px solid #3a4a5b;border-radius:9px;min-height:42px;padding:8px;box-shadow:none}
#sn-pc-sheet .sn-pc-search input{font-size:16px;min-width:0;width:0;flex:1;outline:none}
#sn-pc-sheet .sn-pc-search input:focus,#sn-pc-sheet .sn-pc-filter-toggle:focus{background:#0e1825;border-color:#697786;box-shadow:none}
#sn-pc-sheet .sn-pc-search input:focus-visible,#sn-pc-sheet .sn-pc-filter-toggle:focus-visible{outline:2px solid #a6b2bf;outline-offset:2px}
#sn-pc-sheet .sn-pc-search input::selection{background:#4b5867;color:#fff}
#sn-pc-sheet .sn-pc-search input::placeholder{color:#8495a7;opacity:1}
#sn-pc-sheet .sn-pc-search .sn-pc-icon{margin:0;width:38px;height:42px}
#sn-pc-sheet .sn-pc-filter-toggle{display:flex;align-items:center;justify-content:space-between;gap:4px;max-width:42%;font-size:12px;line-height:1.2;flex-shrink:0;text-align:left;position:relative}
#sn-pc-sheet .sn-pc-filter-toggle .sn-pc-motion{width:20px;height:20px;flex-shrink:0}
#sn-pc-sheet .sn-pc-filter-toggle .sn-pc-icon-fallback{position:absolute;right:10px}
#sn-pc-sheet .sn-pc-filter-options{height:0;opacity:0;overflow:hidden;transition:height 200ms var(--sn-pc-ease),opacity 160ms ease}
#sn-pc-sheet .sn-pc-filter-options.sn-pc-filter-open{opacity:1}
#sn-pc-sheet .sn-pc-filter-choices{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px;padding-top:8px}
#sn-pc-sheet .sn-pc-filter-choices button{min-height:40px;padding:7px;border:1px solid #3a4a5b;border-radius:9px;background:#111c29;color:#bcc6d0;font-size:13px}
#sn-pc-sheet .sn-pc-filter-choices button[aria-pressed=true]{background:#303b48;border-color:#788696;color:#f0f2f4}
#sn-pc-sheet .sn-pc-status{font-size:12px;color:#a6b9cb;padding:0 16px 8px;flex-shrink:0;min-height:24px}
#sn-pc-sheet .sn-pc-scroll{overflow:auto;overscroll-behavior:contain;flex:1;min-height:0;padding:0 14px 12px;scrollbar-width:thin;scrollbar-color:#4f6277 transparent}
#sn-pc-sheet .sn-pc-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}
#sn-pc-sheet .sn-pc-grid.sn-pc-square{grid-template-columns:repeat(3,minmax(0,1fr))}
#sn-pc-sheet .sn-pc-card{min-width:0;border:1px solid #354558;border-radius:11px;overflow:hidden;background:#101c2a;padding:0;text-align:left;position:relative}
#sn-pc-sheet .sn-pc-card[aria-pressed=true]{border-color:#67c9ff;box-shadow:inset 0 0 0 1px #67c9ff}
#sn-pc-sheet .sn-pc-card[aria-pressed=true]:after{content:'\2713';position:absolute;right:6px;top:6px;background:#1376a8;color:#fff;border-radius:50%;width:23px;height:23px;text-align:center;line-height:23px}
#sn-pc-sheet .sn-pc-card img{display:block;width:100%;aspect-ratio:16/9;object-fit:cover;background:#0a121e}
#sn-pc-sheet .sn-pc-square .sn-pc-card img{aspect-ratio:1;object-fit:contain;padding:10px}
#sn-pc-sheet .sn-pc-card span{display:block;padding:8px;font-size:12px;line-height:1.4;min-height:46px;overflow-wrap:anywhere}
#sn-pc-sheet .sn-pc-original{min-height:80px;display:flex;align-items:center;justify-content:center;color:#a9d5ef}
#sn-pc-sheet .sn-pc-more{display:block;width:100%;min-height:44px;background:#263a4e;border-radius:10px;margin-top:10px}
#sn-pc-sheet .sn-pc-games{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:10px}
#sn-pc-sheet .sn-pc-games button{background:#294459;border-radius:8px;padding:8px;min-height:38px}
#sn-pc-sheet .sn-pc-footer{display:flex;gap:10px;padding:12px 14px max(12px,env(safe-area-inset-bottom));background:#172231;border-top:1px solid #304255;flex-shrink:0}
#sn-pc-sheet .sn-pc-footer button{flex:1;border-radius:10px;min-height:44px;background:#2b3d50}
#sn-pc-sheet .sn-pc-footer button:last-child{background:#3f9ed1;color:#071724;font-weight:600}
#sn-pc-sheet .sn-pc-mini{height:125px;position:relative;overflow:hidden;border-radius:10px;background:#101923;margin-bottom:12px;display:flex;align-items:center;padding:16px;gap:14px}
#sn-pc-sheet .sn-pc-mini>video,#sn-pc-sheet .sn-pc-mini>.sn-pc-mini-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
#sn-pc-sheet .sn-pc-mini>picture{position:relative;z-index:1;display:block;width:64px;height:64px}
#sn-pc-sheet .sn-pc-mini>picture img{width:100%;height:100%;object-fit:contain}
#sn-pc-sheet .sn-pc-mini>picture.sn-pc-mini-frame{position:absolute;left:9px;width:78px;height:78px;pointer-events:none}
#sn-pc-sheet .sn-pc-mini>strong{position:relative;z-index:1;text-shadow:0 1px 4px #000;overflow:hidden;text-overflow:ellipsis}
@media(hover:hover) and (pointer:fine){#sn-pc-sheet button:not(:disabled):hover{filter:brightness(1.15)}}
@media(prefers-reduced-motion:reduce){#sn-pc-sheet{transition:opacity 160ms ease}#sn-pc-sheet .sn-pc-mini>video{display:none}#sn-pc-sheet .sn-pc-filter-options{transition:opacity 160ms ease}}
""".trimIndent()
}
