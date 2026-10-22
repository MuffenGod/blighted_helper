# Body spike (Phase 0b): fake player (A) against upgraded mob (B) - result, 2026-10-17

**Recommendation: NO-GO on body A as the default body. GO on body B (upgrade `HelperEntity`), with five fixes (section 5).**
You said mid-spike that you want player-like *effects* on an AI mob, not an actual player. The measurements agree with that
direction, so B is the main line. One more idea falls out of the work: use a hidden NeoForge `FakePlayer` only as the *acting
hand* for mining and placing (so claim and break events see a player), without joining the player list (section 6).

Everything was measured in the scratch game (`C:\Users\srafa\bd`, world `TEST_spike`, 315 mods, NeoForge 21.1.250) with the real
mod list. The real instance was never run. Tags: **PASS / FAIL / PARTIAL / NOT VERIFIED**. Evidence logs and screenshots are in
the session notes folder `side-session-notes\local_076e5078-...\body-spike\`.

## 1. What was built (throwaway)

All of it is inert unless the JVM has `-Dblighted.spike=true` (only `bd\launch_args_spike.txt` sets it).

| Piece | Where |
|---|---|
| Spike package (commands `/bdspike ...`, fake player, B helpers, client skin override and tab-list log) | `blighted_enhancements/src/main/java/.../helper/spike/` (10 files) |
| Body A: `SpikePlayer extends ServerPlayer`, placed with `PlayerList.placeNewPlayer` over an `EmbeddedChannel` connection whose last outbound stage swallows packets, NeoForge's `NetworkRegistry.configureMockConnection` so mod payloads pass `checkPacket`, and its own `tick()` calling `doTick()` (nobody else ticks a fake connection) | `FakeConnection.java`, `SpikePlayer.java`, `SpikeBodies.java` |
| Body B: the existing `HelperEntity` plus a Curios entity file, an attribute fix, a 36-slot inventory as a data attachment, and a tab-list "ghost" | `SpikeAttachments.java`, `data/blighted_enhancements/curios/entities/helper.json` |
| Hooks into real files (all small) | `BlightedEnhancements.java` (1 line), `build.gradle` (compileOnly: curios, bettercombat), `helper/client/CuriosLayerAdder.java` (new, public) |
| Launcher | `design/tools/spike_cycle.ps1` (scratch game only) |

Cleanup when done: delete the `spike` package, the one line in `BlightedEnhancements.java` and the two jar names in `build.gradle`.
Keep `helper.json` and `CuriosLayerAdder` (they are the B fix) and **move** the attribute listener and the inventory attachment out of the package into the real `helper` code first, because they live inside it and are gated by the flag. Note: someone copied a jar built from this tree into the real
`mods/` folder at 22:22; it contains the spike package, which does nothing without the flag.

## 2. The 8-point checklist

| # | Check | A: fake player | B: upgraded mob |
|---|---|---|---|
| 1 | Joins, tab list, skin, walk/sprint/swing/damage visible | **PASS.** Tab entry `Emily[emily_skin.png,slim,ping=0,mode=SURVIVAL]`, entity is a `RemotePlayer`, vanilla "Emily joined the game", nametag, walk and sprint animation with sprint particles (screenshot `shot_A_sprint.png`). Skin override works without a mixin (reflective write of `PlayerInfo.skinLookup`, keyed by her UUID). A second client was not run: the owner's client is the observer, it saw her as another player. | **PARTIAL, fixable.** No tab entry by default. A "ghost" entry works with no entity behind it: tab list `Emily[...,ping=12]`, "Emily joined the game", and `<Emily> anyone up for a cave run?` rendered like real chat; skin override applied to the ghost too. |
| 2 | 10 minutes without exceptions from other mods | **PASS with noise.** 10-min soak (22:33-22:44), no crash and no packet-registry error. But every login logs ~280 WARN stack traces from MineColonies (`DataPackSyncEventHandler.sendOnLogin` -> `Error serializing item stack`) and stalls the server: `placeNewPlayer` took 667, 2096, 886, 980 ms across four runs, one "Can't keep up, 2216 ms behind". The owner's own login does not log these. | **PASS.** Nothing logged. |
| 3 | FTB Teams / Quests / Chunks | **PASS, with litter.** She gets her own personal team `Emily#25b6c49f` (teams 2 -> 3) and the owner's party is unchanged (members=1). It also creates `ftbteams/player/<uuid>.snbt`, `ftbquests/<uuid>.snbt`, `ftbchunks/<uuid>.snbt`. Claims work: in the owner's claimed chunk `destroyBlock` returned `false` (4 tries), in unclaimed chunks `true`. Side effect: she cannot work inside the owner's claim unless she joins the team. | Not applicable (no player events, no data). To respect claims she must ask the FTB Chunks API herself. |
| 4 | MineColonies side effects | **FAIL.** Walking her into the colony broadcasts `[Muffen's Hamlet] Beware, Emily just entered the colony.` (the "you entered colony" side effect). Citizen count was unchanged (0/1) and nothing else broke. Plus the login sync spam from check 2. | **PASS.** No message, no log lines. |
| 5 | Worn Artifact and Apotheosis-affixed sword change attributes | **PARTIAL.** Apotheosis: PASS (epic sword with Violent: attack damage 6 base modifier + 5 from the affix, value 12, identical on the owner and on her). Artifacts: NOT VERIFIED on any body (see below). Extra: Reskillable gates her like a human: a diamond chestplate (needs defense:15) was kicked into her inventory. | **PASS after the attribute fix.** Same sword on B: value 14 (her base is 3). Her vanilla armor and a mod armor attribute (`legendarysurvivaloverhaul:chest_resistance`) applied. Without the fix B lacked 32 registered attributes (attack speed, block break speed, mining efficiency, interaction ranges, luck, Artifacts' two, many mod ones); one `EntityAttributeModificationEvent` listener (in the spike package, `SpikeAttachments.onAttributes`) adds all 62 missing ones to the helper type (B: 133 -> 165 of 165). |
| 6 | Iron's Spells cast | **PASS.** `/cast Emily irons_spellbooks:fireball 3` spawned a `fireball` entity (normal cast flow). | **PASS, cheaply.** The same command spawned one for B, through Iron's generic `LivingEntity` branch (instant, no cast time, no mana). A real spell skill should use `MagicData` and `AbstractSpell.onCast`, or implement `IMagicEntity`. |
| 7 | Save/quit/load keeps her inventory, no orphan data | **PASS, with litter.** Save and Quit with her online ran the vanilla shutdown path (`PlayerLoggedOutEvent` fired for her, no exceptions); after reload she kept position, health 15.8/18, her affixed sword and her curios. Orphan files stay if the mod is removed: `playerdata/<uuid>.dat` + `.cosarmor`, the three FTB files above, a `usercache.json` entry. None block a world. | **PASS.** Equipment, curios and the 36-slot attachment inventory (5 diamonds + 5 crystal hearts) all survived the same graceful reload. |
| 8 | Idle tick cost under 0.2 ms | **FAIL.** Her own `tick()` (including every mod's player-tick handlers): median 256-307 us, average 322-385 us while idle (10-minute windows), 330-500 us when following, p99 1.1-2.3 ms. 1.3-2x over budget. Packets sent to her: ~33 per second (mostly entity motion, plus per-player payloads from Create, WATUT, Placebo, NeoForge time). | **PASS.** `EntityTickEvent` Pre..Post: average 50-130 us, median 44-110 us, p99 0.2-0.5 ms. |

**Verdict on the 8 points:** A fails 4 and 8 and passes 2, 3, 7 only with noise. B passes 5-8 and gets 1 through the ghost.

### Artifacts: not verified, and why that matters little
Night Vision Goggles and Crystal Heart worn through the Curios API produced no effect on **the real owner either** (no effect, no
attribute modifier), so the test shows nothing about A versus B. The stacks carry a `relics:data` component from another mod.
Static read: Artifacts runs its abilities from `EntityTickEvent.Post` on any `LivingEntity` and reads wearers through
`SlotContext.entity()`, so B should work. **Open item (2 minutes by hand):** put Running Shoes on yourself through the normal
inventory, check the effect, then give the same pair to Emily.

## 3. Things that only showed up by trying A

| Finding | Evidence |
|---|---|
| Every mod's login handler runs for her: Enigmatic Legacy handed her a Cursed Ring, Legendary Survival Overhaul gave her temperature immunity, Artifacts and MineColonies synced data to her, and **our own** `OnboardingEvents.onLogin` runs `ColonyService.onLogin` for her (it would try to open the colony screen). Every handler of ours needs an `instanceof` guard. | spike log, run 1 |
| Death goes through the whole player pipeline. A Necromancer killed her 20 seconds after she walked off; Enigmatic Legacy spawned a storage crystal, Gravestone made a grave, vanilla broadcast the death. Vanilla then waits for a respawn request that never comes; her saved data is *dead*, so the next spawn loads her dead and removes her. A dead fake stays in `PlayerList` (we saw two players named Emily) until `connection.disconnect()` is called. Our "downed, never dies" rule needs `die()` intercepted. | `[SPIKE] SpikePlayer.die`, `/tp` hit 2 entities |
| She is a valid target for every hostile mob and for PlayerMobs (a hostile mob that looks like a player). B is only targeted through our own goals. | static (PlayerMobs config, `attackTwin=true`), run 1 death |
| Reskillable skill locks apply to her (chestplate gated). Good for "plays by the rules", bad for a helper that should just wear what you give her. | spike log |
| `placeNewPlayer` needs the fake connection to be configured exactly right (NeoForge's `checkPacket` throws for any modded payload otherwise). It works, but it is a deep hook into netcode that can break on a NeoForge update. | `FakeConnection.java` |

## 4. Other checks you asked for

| Check | Result |
|---|---|
| **PlayerMobs interference** | None seen. It takes names from its config list plus the server whitelist (not from online players), targets `Player` only, and a `jeb_` summoned beside both bodies did nothing for 20 s on Normal. Under A it would attack her like a player and (config) drop her head on death; under B it ignores her. |
| **How Curios renders worn items** | A: automatic, native player layer, hat and slippers drawn correctly (`shot_hats_A_B.png`). B: **not automatic.** `HelperAvatarRenderer` wraps the skin renderer and never registers it, so Curios never adds its layer. With one `CuriosLayer` added (`CuriosLayerAdder`, 3 lines) the worn cowboy hat and kitty slippers draw on the head and feet of the new Gaia model in 3 of 4 frames; one frame right after a reload showed the hat at chest height. Treat as promising, needs an anchor adapter and a proper test. |
| **Better Combat animations** | A: **plays.** Feeding `ServerNetwork.handleAttackAnimation` the packet a real client would send (`one_handed_slash_horizontal_right`) put her in the full slash stance (`shot_A_better_combat.png`). B: **no.** Better Combat animates players only; B needs its own swing animation. |
| **CPM check 1 (a custom model for a fake player)** | The mechanism exists: `ICommonAPI.setPlayerModel(Class<P>, P, String modelData, boolean forced, boolean save)` and `/cpm setskin -f <target> <skin>` store a model per player on the server and send it to trackers (`SetSkinS2C`), so a fake `ServerPlayer` could get one. **Not exercised:** the pack has no `.cpmmodel`, and authoring one needs CPM's in-game editor. CPM cannot dress a mob, so it does nothing for B. |
| **CPM check 2 (our own layers on the player renderer)** | Not built (moot once B wins). The horns/wings/tail layer already exists for B; for A it would be an `EntityRenderersEvent.AddLayers` hook on the player renderers keyed by her UUID, about 120 lines reusing the same model parts. |

## 5. What body B needs (Phase 3 work list, from this spike)

1. **Move the attribute fix into production** (`SpikeAttachments.onAttributes`, flag-gated today): add every registered attribute the helper type lacks. Without it, any item whose modifier targets a player-only attribute silently does nothing. New mods are picked up automatically.
2. **Curios**: keep `helper.json` (done: 11 slot types, extra sizes from the pack's slot config), add the render layer and an anchor adapter for the Gaia model, then test hands and necklace items.
3. **Inventory**: the attachment prototype persists (36 slots). Build the menu from plan 5.3 on top of it.
4. **Presence**: ghost tab entry plus system-message chat (spike code is reusable). Cost: one packet each way, no server-side player.
5. **Swing animation and attack feedback**: B's own, since Better Combat will not drive her. Check Apotheosis on-hit affixes (thunderstruck, executing) against B's `doHurtTarget`; attribute affixes are confirmed, event affixes are **NOT VERIFIED** (the zombie-damage test was inconclusive in this pack).
6. **Existing bug found on the way**: if you teleport far (we went ~770 blocks), B is left in an unloaded chunk and does not tick, so she cannot catch up; `/blighted helper summon` then creates a second one (the old one is discarded when it loads again). Give her a chunk ticket, or move her with you when the teleport is long.

## 6. Recommended shape (the Avatar interface idea, kept)

`Avatar` stays the seam. **`BodyMob` (B) is the only implementation to build now.** For skills that need a player in world
events (mining, placing, using blocks, claim checks), give the avatar an optional `actor()` that returns a cached
`FakePlayerFactory` player (the same pattern our `DevCommands` rank test already uses): no connection, no tab entry, no login
handlers, no player list, but break and place events and FTB Chunks see a player with her gear. Not tested in this spike
[E]; propose a small spike at the start of Phase 4 (does mining speed, Reskillable gating and FTB claim denial behave).

## 7. Not verified / limits

- No second human client; no dedicated server run (the owner client acted as the other observer).
- Artifacts effects (see section 2), event-type Apotheosis affixes on B, mining/placing as B.
- Block-damage numbers: the test zombie took 3 per hit from a 12-damage sword (some pack damage rule), so the damage log did not give a clean comparison. The attribute values above are the evidence.
- A's tick cost was measured with the game client in the same JVM, so absolute numbers are a little high; the ratio to B (about 4-6x) is what matters.
- Frame rate in the screenshots varied (10-300 fps); nothing here is a client performance claim.
