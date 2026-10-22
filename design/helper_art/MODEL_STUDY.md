# Model quality study (2026-10-16)

Goal: learn how high-quality GeckoLib / YSM-style models are built. STUDY ONLY: nothing was copied, no third-party model or texture is used in Emily.
Tools: `scratchpad/geo_stats.py` (survey of every geo model in `mods/`), `scratchpad/geo_tree.py` (bone tree of one model). Both read-only.

## YSM (Yes Steve Model), from its public wiki (ysm.cfpa.team)
- Built on **GeckoLib**; geometry Bedrock **1.12.0**, animations Bedrock **1.8.0**, plus animation controllers and audio since 2.3.0.
- Models ship as folder, zip or encrypted `.ysm`. Only the generated default Steve/Alex files are CC0. YSM itself is All Rights Reserved.
- Special **locator groups** (Head, LeftHandLocator, RightHandLocator, ElytraLocator, BackpackLocator, sword/gun locators); numbered duplicates allowed.
- Groups named `ysmGlow*` render emissive. Textures are PNG, PBR supported.
- The wiki says nothing about modelling quality: the quality comes from the authors, not the engine. Nothing in YSM needs decompiling to learn this.

## What the best installed GeckoLib models do (hazennstuff, crystal_chronicles, mebahel)
Surveyed 615 geo models. Top ones have 100-280 cubes, 120+ bones, 128-256 px textures.
1. **Flat cards, not boxes.** Hair, leaves, petals, vines, wing and cloak shapes are **zero-thickness planes** (cube size 0 on one axis, e.g. `[8,8,0]`, `[0,6,5]`) with an **alpha-cutout texture**. The silhouette lives in the PNG's transparency. About 40% of cubes are "thin". This is the main reason they look sculpted and not blocky.
2. **Rotated cubes everywhere.** ~40% of cubes carry a rotation, so surfaces taper and flare instead of staying axis-aligned.
3. **Segmented chains for anything soft:** hair is front / sides / back with 3-4 chained segments each tilted 15-17 degrees; skirts are per-leg `skirt -> front/middle/back -> segment A/B` chains flared 12-22 degrees outward, parented to the leg so they swing with it.
4. **A real eye rig:** each eye = brow, top eyelid, white, pupil, bottom eyelid as separate small cubes on their own bones, so animations can blink and change expression. The face is not just painted on.
5. **Locator bones** for items (`bipedHandRight`, `rightItem` with rotation -90 on x) and vanilla armor slots (`armorBipedHead`, ...).
6. **Glow mask texture** (`*_glowmask.png`) for emissive parts, a second texture of the same size.
7. **Palette:** painted textures with outlined shading, one hue family per material, detail in 128-256 px.

## What this means for Emily (what went wrong in v1)
- My wings, hair and skirt were stepped boxes. They should be alpha cards + chained rotated segments.
- The wings are probably invisible because they are thin boxes drawn face-on; planes with alpha are the proven approach here (the studied models render them fine).
- Her face is only painted. Add an eye rig (blink, half-lidded, heart/slit pupils) and a mouth bone.
- Skirt should be per-leg flared chains with a slit, not a static ring.
- Add a `*_glow` texture for the gem, eyes and gold inlay.

## YSM jar study (user-approved download 2026-10-16, study only, nothing reused)
File: `ysm-2.6.5-neoforge+mc1.21.1-release.jar` (63.5 MB) from Modrinth, SHA-512 verified. Kept in the session scratchpad (`ysm_study/jar`), never executed.
- **Code:** 910 classes, names deliberately obfuscated (`oOo0OO0O0o000OO0O000oo0o`), real engine is a native library (`ysm-core.dll`, ~59 MB). Not worth reverse engineering and I did not try to defeat the protection.
- **Data is plain:** the jar ships built-in example models as ordinary JSON/PNG: `default`, Alex, Steve, a boy, a controllers demo, and **`wine_fox/01_taisho_maid`**, a full anime maid. Those files are what I studied.

### Taisho maid (the quality reference)
| | |
|---|---|
| Geometry | Bedrock 1.12.0, texture **256x256**, **195 bones, 490 cubes** (375 rotated, 217 inflated, only 1 thin, 0 flat planes, 2 per-face UV) |
| Panels | cloth/hair are **1-unit-thick** boxes, many per chain, not zero-thickness cards |
| Hierarchy | `GuiRoot > MRoot > Root > MAllbody > AllBody > MUpBody > UpBody > (Tail, MUpperBody > UpperBody > AllHead > MHead > Head, Arm, clothe)`; `DownBody > Leg > LowerLeg > Foot`. The **`M`-prefixed bones are wrappers driven by Molang**, so the keyframed bone underneath keeps its own animation |
| Face | head cube 7x7x7 (small), eyes built from `Eyelid > EyelidBase > EyePublic (pupil) + EyeDot (highlight)`, separate eyebrows, **7 `Expression_n` and 4 `Mouth_*` groups** that are shown or hidden to change expression |
| Hair | `BaseHair` 30 cubes, `bone5` 11, side hair 10 cubes each, `Bangs` 8, then `LongHair > LongHair2` chains (+ left/right), each chain wrapped in a Molang `F`/`M` parent |
| Skirt | `clothe` -> Left/Right/Back/Front groups (tilted +-7.5 / +-1.2 deg) -> **3 panels each -> 3 chained segments each** = ~36 chains of 1-unit-thick panels; widths 2-7, heights 3-7. Reads as a flowing round skirt |
| Tail | 7 chained segments, big (6.6 x 5.6 x 7.5), fluffy |
| Accessories | ribbon bow (10 bones), animal ears 11 cubes each, mask 55 cubes |
| Locators | hand, waist, sheath, blade, rifle, pistol, backpack, elytra, shoulder locators as empty bones |
| Texture | hand-painted pixel art, **5-step tonal ramps per hue** (swatches kept in a spare corner of the sheet), tiny faces per cube, outlined |

### Animation (Bedrock 1.8.0 + Molang + controllers)
- 49 animations: idle, walk, run, jump, fly, elytra, swim, sit, sneak, sleep, climb, ladder, vehicles, attack/use/swing, death, plus `extra` wheel animations (504 KB for this model!).
- Hand-keyed at **24 fps**; the walk cycle drives 28 bones including every skirt panel group with phase offsets.
- **Procedural Molang** on top: breathing `math.sin(q.anim_time*...)`, hair and skirt tilt from `query.ground_speed`, `ysm.head_yaw/head_pitch`, vertical speed on jump (`1-math.exp(-0.1*abs(speed))` easing), arm pose switches on `ysm.has_mainhand`.
- **Random idle director:** `idle` has a timeline event `v.next_idle=math.random_integer(0,4)`; an animation controller state machine (`default -> idle1..idle4`, blend 0.2 s) plays one of four idle variants then returns. This is what makes her feel alive.
- 10 `parallel` and 7 `pre_parallel` layers for expressions and layered extras.

### What we take from it (techniques, not assets)
1. Detail budget: 150-500 cubes is normal for a hero model; hair/skirt as many small chained 1-unit panels with inflate and rotation.
2. **Wrapper bones** (`M*`) so procedural sway and keyframes do not fight.
3. Eye rig + hide/show expression groups, driven by animation.
4. Idle variety through a random idle state machine.
5. Texture discipline: ramps, outline, per-cube island painting, 256x256.
6. Secondary motion tied to movement speed and head turn.

## YSM rendering pipeline (bytecode scan of the Minecraft/NeoForge API references, user asked for reverse engineering, nothing copied)
- Draws through **vanilla** `RenderType`s: `entityCutoutNoCull`, `armorCutoutNoCull`, and a custom translucent type (`entity_translucent_ysm`, based on `entityTranslucent`). No toon shader, no outline shader of its own, no special lighting (only `LightTexture` / packed light, `OverlayTexture` hit flash).
- Uses `PoseStack` + `VertexConsumer` + `MultiBufferSource`: it feeds baked vertices into Minecraft's normal pipeline. Own texture classes (`AbstractTexture`, `DynamicTexture`) for runtime-loaded skins.
- **Iris integration:** registers normal-map and specular-map textures through Iris' `PBRTextureLoader` (the `_n` / `_s` PBR files) and falls back gracefully when Iris is absent. Glow groups (`ysmGlow*`) are an emissive path.
- Mixins into `BufferSourceMixin` and `WorldRendererMixin` (render ordering and translucent batching, so many translucent cards sort correctly), plus `EntityRenderer` replacement hooks for the player model.
- Native core (`ysm-core.dll`) does parsing, Molang evaluation and animation blending. Not needed to understand the look.
**Conclusion: nothing about how YSM *draws* explains the quality gap.** It is the same pipeline GeckoLib uses. The gap is content: proportions, cube budget, painted textures, hair/cloth chains, eye rig, animation.

## YSM-style character look, from the TikTok by commonmuji (frames captured, 5 different characters)
- Head about 1.5x a Minecraft head on a normal-sized body; face on the lower half; very large painted eyes with bright highlights, tiny nose/mouth, blush.
- Hair as chunky tapered layered pieces with painted strands and shading; ears/ribbons/halo as thin cards (some with soft gradient alpha).
- Short layered clothing, painted lace/trim, strong colour palette per character.
- So: chibi-leaning proportions, content density, and translucent cards. We can match this with GeckoLib; we only need to override `getRenderType` to use `entityTranslucent` for soft-edged cards.

## Other sources (not used, not downloaded)
- Touhou Little Maid has many community Bedrock "maid" model packs. Licence is per pack author, so reference only.
- Most polished GeckoLib models in popular mods are All Rights Reserved. Reference only.
