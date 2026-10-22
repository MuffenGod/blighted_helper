# Emily as a second player: mind, body and memory - plan v1, 2026-10-17

Design only. No pack files or mod code were changed, and nothing was downloaded. Builds on `HELPER_PLAN.md`
(6c-2 local voice, 6d brain, 6h persona). Tags: **[V]** checked today against a page, API or the jars in this
instance, **[E]** my estimate (must be measured), **[?]** not checked.

"Obliterated" is read as **abliterated** (a model with its refusal direction removed).

## 1. Goal and the one idea that makes it work

Emily follows you, fights beside you, mines with you, finds her way home, has a real inventory screen (armor, weapons,
curios) where gear works as it does on a player, thinks on her own, remembers across days, talks for hours, and passes
as a real co-player: a person playing a half-dragon character (decided 2026-10-17: she keeps her horns, wings, tail and
persona everywhere; "passing" means behaving, moving and typing like a person, not hiding what she looks like). All of it runs beside a heavy pack and shaders, with MiniCPM5-1B as the model.

**A 1B model must not drive a player. It is the mind, not the body.** Three layers:

| Layer | Where | Rate | LLM? | Job |
|---|---|---|---|---|
| Body and skills | server thread | 20 Hz motor, 5 Hz decisions | no | walk, path, mine, fight, eat, equip, sleep, go home. Works with no model and no key. |
| Reflex (utility AI) + humanizer | server | 5 Hz | no | picks the skill (survive > fight > help > chores > idle) and gives it human timing and flaws |
| Mind | owner's PC, separate process | event driven, 1 call in flight | yes | talks, picks goals from a fixed menu, thinks in the background, writes memory |

Memory is **retrieval plus a few facts we hand the model each turn**, not model size. The model only ever sees
about 1.3k tokens, so "remembers for days" is an engineering problem we can solve. Reasoning depth is not.

**Honest expectations for 1B.** Good at: short in-voice lines, choosing one verb from a menu when its output is
grammar-constrained, tagging mood. Weak at: multi-step reasoning, staying consistent over 50 turns, novel humour, long
stories, arithmetic. It will repeat itself and contradict itself unless memory, a rolling summary and output checks hold it
up. A determined interrogator ("what did we do yesterday", "type a word backwards") will break any 1B; the target is
"passes hours of casual co-play", and section 13 measures that with a blind test. MiniCPM5-2B (released 2026-09-07
[V]) is the planned step up if the bake-off says 1B is not enough.

## 2. What we already have [V, read today]

| Piece | State |
|---|---|
| `HelperEntity` | owner-bound `PathfinderMob` + GeckoLib, stances FOLLOW/STAY/AGGRESSIVE, "downed" instead of dying, equips a held weapon/armor by right-click, vanilla goals (`HelperGoals`: Melee, FollowOwner, OwnerHurtBy/Target, Threats). No inventory, no curios, no mining, no home. |
| `HelperManager` | summon/recall, cross-dimension relocate, teleport when > 24 blocks away |
| Brain (client) | `OpenAiCompatBrain` speaks the OpenAI streaming format, so **llama-server works today with `provider = "custom"` and `baseUrl = http://127.0.0.1:<port>/v1`, no Java**. `ReplyStreamer` already strips `<think>` blocks, tags and markdown. |
| State | `HelperContext` (client facts) + `HelperServerState`/`ColonyKeys.snapshot` (server facts) over `HelperPayloads` |
| Voice | local Qwen3-TTS server `:8791`, `SpeechQueue` sentence by sentence, first audio ~230-290 ms, 2.5 GB VRAM, GPU 94% busy while speaking |
| Launcher pattern | `VoiceServerLauncher` starts and watches a sidecar; reuse for the model server |
| Tests | `design/tools/brain-test/run_brain_test.ps1`, 132 checks |
| Pack mods that matter | Curios 9.5.1, Artifacts 13.2.5, Apotheosis 8.9.0, Iron's Spells 3.16.3, Better Combat 2.4.0 + player-animation-lib, Cosmetic Armor Reworked, Sophisticated Backpacks, MineColonies 1.1.1403, FTB Teams/Chunks/Quests, Gravestone-Curios compat, PlayerMobs 5.1.2.4 (hostile player-look mobs), Modern Companions 4.60 (**GPL-3.0**), Iris + Sodium |
| Machine | Ryzen 7 5800XT 8c/16t (AM4, DDR4), 32 GB RAM, RTX 5070 12 GB (2.2 GB used on an idle desktop), driver 610.88. Ollama has only the 27B Q2 model, unusable beside the game. |

## 3. The model: MiniCPM5-1B (and what to test it against)

| Fact [V: HF model card, config.json, HF file listing, OpenBMB GitHub] | |
|---|---|
| Size | 1.08 B params (0.68 B non-embedding), 24 layers, hidden 1536, 16 query / **2 KV heads**, head dim 128, plain `LlamaForCausalLM` |
| Context | 131,072 (we will use 4-8k) |
| Licence | Apache-2.0, so we may redistribute it with a notice |
| Thinking | hybrid; `enable_thinking` in the chat template. Use **No Think**: temperature 0.7, top_p 0.95 |
| Tool calls | native XML-style, but their parser targets SGLang/vLLM. **We do not rely on it**; we use grammar-constrained JSON (section 7). |
| Official GGUF | `openbmb/MiniCPM5-1B-GGUF`: **Q4_K_M 656 MB**, Q8_0 1,100 MB, F16 2,066 MB |
| KV cache | 24 layers x 2 heads x 128 x 2 (K and V) = 24.6 KB/token in f16, so 8k tokens ~ 200 MB (q8_0 about half) [E from config] |
| VRAM total | 656 MB weights + ~200 MB KV + ~200 MB compute buffers = **about 1.0-1.2 GB** [E] |
| 2B sibling | 2.52 B params, 42 layers, same GQA style, released 2026-09-07; roughly 1.5-1.7 GB at Q4 [E] |
| Fine-tuning | the model card lists TRL/PEFT, unsloth, LLaMA-Factory (a LoRA fits a 12 GB card) |
| Role-play quality | **not stated anywhere**; third-party rankings disagree wildly. We measure it ourselves. |

**Abliterated variants that exist [V names only, quality unknown]:** `Huihui-MiniCPM5-1B-abliterated`,
`MiniCPM5-1B-heretic` (saidutta69), `MiniCPM5-1B-Uncensored`, `MiniCPM5-1B-Agent-abliterated`, with GGUFs by
mradermacher. Download counts are in the hundreds to ~1k, so they are unvetted. The 2B has far more variants and
downloads. Skip the "Claude-Fable5-distilled" fine-tunes: provenance and licence are unclear, and they are
thinking-mode (slower).

**What abliteration does and does not buy.** Our persona is sly and bratty, not edgy, so refusals will rarely be the
problem; capability is. Abliteration often costs a small model a little coherence and JSON reliability. So it is a
tested option, not the default: run stock and abliterated through the same eval (below) and pick by score.
Whatever the model, keep our own **output filter** (blocklist + length/format checks), because other people read her
chat in multiplayer.

### 3a. Bake-off (Phase 0a, a test harness in `design/tools/mind-eval/`)

Candidates: stock 1B Q4_K_M and Q8_0, the three abliterated 1B GGUFs, stock 2B Q4_K_M. About 80 cases:

1. **Persona chat** (30): tone, length (1-2 short sentences), no markdown/emoji, no "as an AI", stays in voice for 6-turn threads.
2. **Intent selection** (20): "come here", "can you grab me some iron", "stay put", "I'm going home", sarcasm, ambiguous requests; output must be valid JSON through a grammar, and the right verb.
3. **Memory-grounded answers** (15): 5 facts in context, ask about them; ask something not in memory and require "I don't remember" (hallucination rate).
4. **Long drift** (15): 50-turn scripted thread with a rolling summary; count contradictions and repeats.

Scored by script where possible (JSON validity, verb accuracy, length, banned tokens, fact match) and by you in a blind
A/B for voice. Also measured per candidate: time to first token with a cached prefix, tokens/s, VRAM and RAM, and
**frame time of the real pack with a shader on while the model decodes** (the open item from HELPER_PLAN 6c-2).
Decision rule: pick the smallest model that scores >= 85% on intents, >= 85% on memory answers with <= 5% invented
facts, and that you rate >= "good" blind on voice. If no 1B passes, 2B; if 2B fails voice only, a LoRA (Phase 7).

### 3b. Runtime: llama.cpp `llama-server` as a sidecar, not inside the game

Prebuilt Windows x64 zips exist in release b11716 (2026-10-17) [V]: CPU 19 MB, **Vulkan 31 MB**, CUDA 12.4 255 MB,
**CUDA 13.4 148 MB (+404 MB cudart)**. The RTX 5070 is Blackwell, which needs CUDA 12.8 or newer, so use the 13.4 build
or Vulkan; the 12.4 build probably falls back to slow PTX JIT [?]. No cmake/nvcc needed.

Why a sidecar: a crash cannot take the game down, no native libraries in the pack's JVM, same pattern as the voice server,
and `VoiceServerLauncher` already shows how. A Java binding (JNI) is smaller to ship but loads native code into Minecraft; rejected.

Server settings to use (verify each in `--help`): `-c 8192 -np 2` (slot 1 chat, slot 2 background thoughts), `--jinja`
with `chat_template_kwargs {"enable_thinking": false}`, `cache_prompt`, q8_0 KV cache, `--flash-attn`, grammar/
`response_format: json_schema` for intents, `--slot-save-path` to keep the persona prefix across restarts,
`--sleep-idle-seconds` (unloads the model when idle, if this build has it), `-ngl 99` for GPU or `-ngl 0 -t 4` for CPU.

**Placement is decided by measurement, not by me.** Profiles:

| Profile | Where | Expected [E] | Use when |
|---|---|---|---|
| Light | CPU, 4 threads, below-normal priority | 25-45 tok/s, prompt ~300 tok/s, ~2 s to first word, 0 VRAM | shaders are GPU-bound and TTS is running |
| Balanced | GPU, short bursts | 300+ tok/s, ~0.15 s to first token, ~1.1 GB | default if the frame-time test is clean |
| Quality | GPU, 2B | ~150 tok/s, ~1.7 GB | only if the 1B fails the eval |

TTS already hits 94% GPU while speaking, so the real contention is TTS plus LLM plus shaders at once. If it hitches,
the Light profile keeps the LLM on spare CPU cores while TTS keeps the GPU.

## 4. Architecture

```
 OWNER'S PC (client)                                           SERVER (integrated or dedicated)
 ┌──────────────────────────────────────────────┐            ┌─────────────────────────────────────────┐
 │ Mind                                          │  Digest +  │ Avatar (the body)                       │
 │  PromptBuilder  <- memory snippets, state     │  events    │  Reflex/utility AI -> Skills            │
 │  IntentParser   (grammar-checked JSON)        │ <────────  │  -> Humanizer -> Motor                  │
 │  ReplyStreamer -> SpeechQueue -> TTS :8791    │            │ MemoryStore (world save)  Trail/Homes   │
 │  BarkBank, ThoughtLoop, Throttle              │  Intent    │ ChatGate + TypingSim + Presence         │
 │  llama-server :8792 (MiniCPM5-1B GGUF)        │ ────────>  │ Validate every intent, run it, report   │
 └──────────────────────────────────────────────┘            └─────────────────────────────────────────┘
```

- The Mind runs on the **owner's client** because the sidecar and any API key are per machine and the server may be remote.
  In singleplayer/LAN-host it is all localhost. If the owner is offline, the Mind is offline, and so is Emily (see 9).
- The body works without the Mind (reflex only), so a dead model server degrades her to silent-but-competent, never to broken.
- The server stores and retrieves memory (cheap lexical search) and sends the top snippets with the state digest; the
  client only sends back small, validated results. Nothing the model writes is trusted.
- New code lives in the existing mod, new packages: `helper.mind` (client), `helper.memory`, `helper.body`,
  `helper.skill`, `helper.nav`, `helper.social`, `helper.menu`; new payloads `MindEvent` (S2C), `MindIntent` (C2S),
  `MemoryUpdate` (C2S).

## 5. The body

### 5.1 Decision: real player, or a player-grade mob

Everything below the body (skills, mind, memory, UI logic) is written against a small `Avatar` interface (about 15
methods: position, look, move input, equip, use item, break block, place block, attack, eat, inventory, curios,
say, die/respawn). Two implementations let us decide by spike instead of by guess:

| | **A. Fake `ServerPlayer`** (what Carpet's `/player` bots do) | **B. Upgrade `HelperEntity`** (player-grade mob) |
|---|---|---|
| "Is a player" for mods | Everything that checks `instanceof Player`: Iron's Spells casting and mana, Apotheosis on-hit affixes, Better Combat, mining speed/reach attributes, hunger, XP, advancements, FTB Chunks claim checks through normal block events | Only what works for `LivingEntity`. Spells and some affixes need extra glue or do not work. |
| Looks like a person | Tab-list entry with ping, vanilla "joined the game", normal nametag, player model and walk/attack animations (Better Combat animations may apply), chat as a real player | Named mob, no tab entry, no join message |
| Curios + Artifacts | Native | Works via a data file (below) |
| Risk in a ~200-mod pack | **High**: fake network connection, every mod's login/sync handlers run for her, skin delivery, mods that iterate all players (FTB Teams, MineColonies, PlayerMobs), counts toward max-players | Low: it already exists and runs |
| Work | Medium-large | Medium |

**Recommendation:** try A first in a hard-timeboxed spike (Phase 0b); if it fails the checklist below, build B and
lose only the "tab list and real-player mods" parts. Both share all code above `Avatar`.

Skin delivery for A is not a problem: every human in this world runs the same modpack, so the mod can map her UUID to
a skin it ships (mixin on the client skin lookup) instead of relying on a signed Mojang texture [?].

**Looking like a half-dragon on a player body.** `CustomPlayerModels-1.21-0.6.27a.jar` [V: in `mods/`] is installed, so
players with wings, tails and custom shapes are normal in this pack and a winged "player" does not stand out. Two ways to
give body A her horns, wings and tail, to be tried in Phase 0b: (1) hand her a CPM model if CPM's API or server sync can
target a fake player [?]; (2) draw horns, wings and tail ourselves as extra client render layers on the player renderer,
keyed by her UUID (this also reuses the layered-standee wing art from 6f). Body B keeps its GeckoLib model.

**Spike checklist (A passes only if all of these hold, in the scratch game with the real mod list):**
1. She joins and appears in the tab list with a skin; a second client sees her walk, sprint, swing, take damage.
2. Server runs 10 minutes without exceptions from other mods' login/tick/sync handlers; no packet-registry errors from her dead connection.
3. FTB Teams/Quests/Chunks: she gets (or is denied) a team without breaking the owner's quest team; claims stop her breaking others' blocks.
4. MineColonies: she does not trigger raid, permission or "you entered colony" side effects, and her existence does not change the owner's colony stats.
5. A worn Artifact (Running Shoes, Digging Claws, Flame Pendant) and an Apotheosis affixed sword change her attributes/effects as for a player.
6. An Iron's Spells cast from her works, or we decide it is out of scope.
7. Save/quit/load keeps her inventory; removing our mod leaves no orphan player data that blocks the world.
8. Tick cost of her presence with no behaviour: under 0.2 ms.

**Result of the spike (Phase 0b, 2026-10-17, full write-up in `design/BODY_SPIKE.md`): body A is NO-GO as the default, body B is GO.**
You also said you want player-like effects on an AI mob rather than a real player, which the numbers support.

| # | A: fake player | B: upgraded mob |
|---|---|---|
| 1 tab list, skin, animations | PASS (skin override works, no mixin) | PARTIAL: no tab entry by default; a "ghost" tab entry plus system-message chat works with no player behind it |
| 2 ten minutes clean | PASS but noisy: ~280 MineColonies WARN stack traces and a 0.7-2.2 s server stall on every login | PASS |
| 3 FTB | PASS with litter: her own team, quest and chunk data files; claims do stop her | n/a |
| 4 MineColonies | **FAIL**: "Beware, Emily just entered the colony" | PASS |
| 5 Artifact + affixed sword | Apotheosis PASS; Artifacts NOT VERIFIED (no effect on the real owner either in this harness) | Apotheosis PASS **after** adding the 32 missing player-only attributes (one event listener) |
| 6 spell cast | PASS | PASS (instant, via Iron's generic entity branch) |
| 7 save/reload | PASS (orphan files remain) | PASS (equipment, curios, 36-slot inventory) |
| 8 idle tick | **FAIL**: 0.26-0.39 ms (4-6x B) | PASS: 0.05-0.13 ms |

Other findings: her death runs the whole player pipeline (grave, Enigmatic crystal, dead-on-reload), Reskillable gates her like a
human, our own login handler runs for her; Better Combat animation plays on A only; Curios items render on A natively and on B
only after one `CuriosLayer` is added (done in the spike, head and feet drawn correctly on the Gaia model, anchor adapter still to
test); PlayerMobs does not interfere; CPM can set a server-side model for a fake player in principle but needs a model file
nobody has authored and cannot touch a mob. Recommended: build B; add an optional hidden `FakePlayer` as the "acting hand"
for mining and placing (Phase 4 spike). Phase 3 work list is section 5 of `BODY_SPIKE.md`.

### 5.2 Curios and Artifacts (checked in the jars) [V]

- Curios attaches an inventory to **any `LivingEntity` whose type is listed in a `data/<ns>/curios/entities/*.json`**
  and ticks it from `EntityTickEvent` (`CuriosEventHandler.tick`), plus handles equipment change, drops, sync and
  attribute modifiers. It is not player-only.
- Artifacts itself ships `data/artifacts/curios/entities/slots.json` giving slots (head, necklace, belt, hands, feet)
  to `player` **and** to zombie, skeleton, husk, piglin, ghast and others. Its curio wrapper reads the wearer through
  `SlotContext.entity()` as a `LivingEntity`. So worn Artifacts work on non-players by design [E that every ability
  fires for her; Phase 0b item 5 confirms].
- For body B we add `data/blighted_enhancements/curios/entities/helper.json` listing our entity and the slot types.
  For body A the player assignment already applies.
- Not covered: Curios' own screen is bound to the player, and Curios rendering layers need a humanoid model, so body B's
  GeckoLib renderer will not show worn trinkets unless we draw them. Body A (a player model) shows them for free.

### 5.3 The companion screen (the "nice UI")

Opened with Shift + right-click on her (plain right-click keeps the stay/follow toggle) or `/emily inventory`.

- Left: armor (4), main hand, off hand, and a **dynamic curios column** read from her `ICuriosItemHandler.getCurios()`
  (one group per slot type, honouring each slot's current size, so slot-adding mods work). Cosmetic Armor Reworked slots later.
- Right: 27 + 9 slot inventory, shift-click, drop-out protection.
- Buttons: Follow / Stay / Guard / Aggressive, Go home, Set home here, **Equip best**, Auto-manage (per-slot lock icon: a
  slot you filled by hand stays yours).
- A "Mind" tab: mood, affinity, current goal, last thoughts, and the facts she holds about you with a "forget this"
  button (it doubles as the debug view for memory).
- A live 3D preview of her.
- Implementation: an `AbstractContainerMenu` over her inventory plus `SlotItemHandler`s on the curios `IDynamicStackHandler`s.
  Modern Companions has the same screens (`CompanionScreen`, `CompanionCuriosScreen`) but is **GPL-3.0**: use it as a UX
  reference only, never copy code.

## 6. Skills (what she can do)

All skills are state machines the reflex layer starts and stops. They run at 5 Hz decisions and use vanilla pathfinding
(`GroundPathNavigation`, bounded) instead of a custom pathfinder. For body A, a never-spawned proxy `PathfinderMob`
computes paths and the avatar then walks them [?].

| Skill | Behaviour | Safety / human rules |
|---|---|---|
| **Follow** | keeps an elastic 3-8 block band, not glued; sprints when you sprint, waits at doors, uses water/ladders/boats/portals, drifts to look at things, catches up when far | never teleports in view; elastic radius changes with mood |
| **Fight** | best weapon/armor/shield by score; swings only when the attack cooldown is charged; strafes; backs off to eat/heal at 40%; bow with lead; creepers: retreat then re-engage; endermen: do not look, avoid | uses the 1-5 star level of mobs to judge odds; calls out ("behind you!") through barks |
| **Mine** | exposed-ore scan + branch/strip patterns at plausible heights, vein follow, real dig times from tool/hardness, crack animation, torches below light 8, never digs into liquid or under falling blocks, returns when the bag is full | **no X-ray**: she only targets ores a player could have seen (an exposed face or a cave), otherwise she explores. Walking straight to buried diamonds is a giveaway. |
| **Gather** | logs, crops (replant), drops, item pickups, stack and chest deposit | will not take from chests or break blocks inside another team's FTB claim |
| **Eat / heal / gear** | eats when hungry, swaps broken or worse gear, repairs via anvil later | auto-manage respects your locked slots |
| **Sleep** | uses a bed at night (counts for night skip on body A), else shelters | |
| **Go home** | see 6.1 | |
| **Flee / survive** | lava, drowning, fall damage, burning, low health | always on top priority |
| **Idle** | looks around, shifts, glances at you, occasionally stands still for 20-60 s like an AFK player | |

### 6.1 Finding her way home

- **Homes** (a registry in the world save): bed, colony Town Hall, "base" set by you, last safe spot, last portal.
- **Trail**: while she follows, she records a ring buffer of safe positions (one per 2 s, a few thousand entries). Lost or
  stuck = retrace the trail backwards, which is guaranteed to have been walkable.
- **Long trips**: chain of vanilla path segments of at most ~40 blocks between waypoints (the follow-range cap is ~32).
- **Triggers**: you ask; night plus monsters; low health and no food; bag full; she is separated and far from you; you logged off.
- **Stuck ladder**: jump, sidestep, place/break a block, pillar or bridge, wait, then retrace the trail. Teleport is the very last
  step and only when no player has line of sight within 64 blocks.
- **Dimensions**: portals are walked into like a player; boss or modded dimensions are configurable "she waits at home".

### 6.2 Cost control on the server

Motor 20 Hz is plain steering code; decisions 5 Hz; ore and block scans amortised (one chunk section per tick, cached by
chunk, no per-tick world scans); the state digest is built every 2 s, not every tick; when no human is within 128
blocks and she is not on a task, she drops to 1 decision per second. Budget: **average under 0.3 ms per tick, 99th
percentile under 2 ms**, measured by our own `nanoTime` counters (`/emily perf`).

## 7. The Mind

### 7.1 One structured call per turn, intent first

Output is forced into JSON by a grammar, in this order (so she never says what she is not doing):

```json
{"act":{"do":"mine","target":"iron","n":16},"mood":"smug","say":"Fine, iron it is. Try to keep up."}
```

`say` is streamed out as soon as it starts, so the sentence-by-sentence speech path from 6d still works. The parser
hands `act` to the server after the sentence has been spoken or started. Verbs (about 16): `none, follow, stay, come,
guard{radius}, go_home, set_home, mine{target,n}, chop{n}, gather{item,n}, hunt{mob}, fight{defend|aggressive|passive},
equip_best, eat, give{item,n}, deposit, explore_with, sleep, flee`. The server checks preconditions (tool in the bag,
target known, in range, item on the allowlist, claim rules) and answers with a result event ("mine iron: no pickaxe") that
goes into the next prompt, so her words stay true.

### 7.2 Prompt layout (static first so llama-server reuses its cache)

1. System: persona, 3 short example lines in her voice (a 1B copies examples better than it follows rules), the verb list, hard rules. <= 450 tokens.
2. Profile of the speaker (name, 5 key facts). <= 120.
3. History: last 6 turns plus a rolling summary of older ones, **append-only** and without state inside. <= 350.
4. Then per turn only: memory snippets (<= 200), state digest (<= 150), the player's words.

About 1.3k tokens, of which only the last ~400 are new each turn, so a cached prefix keeps first-token time at ~0.15 s
on the GPU [E]. The current persona + knowledge file is ~905 tokens; the knowledge becomes ~40 tagged chunks and
**only the top two are retrieved** per turn.

### 7.3 Four kinds of turn

1. **Player turn** (chat or voice): the full call above.
2. **Event reaction** (hit, kill streak, ore found, night falls, low food, a death): a line from the **bark bank**, no model call.
   The bank is refilled in idle time: the model writes 6-10 variants per situation and mood, we filter them, and the
   voice server pre-renders audio for the common ones (HELPER_PLAN 6c: stock lines). Runtime cost: zero GPU.
3. **Thought tick** (idle for ~2 min, at most every 90-180 s, only when frame time is healthy): she writes a private
   thought `{thought, goal, mood, say?}` of <= 150 tokens. Thoughts go to memory and the Mind tab; only sometimes
   do they become speech. This is the "she has thoughts" part.
4. **Reflection** ("dream", see 8): background, can use a bigger cloud model if you configured one.

### 7.4 Guards

Reject and regenerate (once) a reply that: is longer than the cap, uses markdown/emoji/"as an AI", names coordinates,
item counts or places that are in neither the state nor the memory, or claims an action the server did not accept. If
the retry also fails, send a short stock line. Output filter on top (section 12).

## 8. Memory (where "long conversations" come from)

Stored in the world save, `<world>/blighted/emily/<ownerUUID>/`, a few KB to MB, JSON lines plus one readable `journal.md`.

| Store | Holds | Written by |
|---|---|---|
| Events (episodic) | one line each: day, place, who, what, importance 1-10 | the game (kills, deaths, finds, builds, gifts, promises, quotes). **Importance is rule based**, not asked of the model. |
| Facts (semantic) | "Raf hates creepers", "base is at the lake" with source and last-confirmed day | regex extraction first ("my name is", "I like", "my base"), the model only tidies |
| People | per human: name, affinity, trust, first/last seen, running summary | events and reflection |
| Places | home, mines, chests, danger spots, portals | the body |
| Promises/todos | "I will fetch iron", "you owe me a sword" | intents and reflection |
| Rolling summary | per person, updated every ~8 turns in the background | the model, 80-120 tokens |

**Retrieval** (server, no model): score = BM25(text vs the player's words + state tags) + recency (half-life ~3 game days) +
importance/10, take the top 4 under the token cap. Always included: current promises and the top 3 facts about the speaker.
An embedding model (e.g. a 25-300 M parameter sentence model on the CPU) is added **only if** the memory eval says BM25 is not enough.

**Dreaming.** When you sleep, log off, or after ~20 idle minutes, she consolidates: last events in, JSON out
(`facts_add`, `facts_update`, `summary`, `affinity_delta`, `promises`), validated and size-capped, <= 400 tokens. Decay
removes low-importance events after a few game weeks. (It suits the pack's name.)

**Memory eval**: play a scripted 3-session story that plants 30 facts and asks 30 questions in later sessions; pass at
>= 85% correct and <= 5% invented. Run it against every model change.

**Multiplayer**: memory is partitioned per person, and an event that involved two people is stored for both.

## 9. Passing as a person

### 9.1 Tells and fixes

| Tell | Fix |
|---|---|
| Instant, perfect aim; 180 degree snaps | reaction delay 3-8 ticks (jittered), look slew with ease and a little overshoot, miss now and then |
| Dead straight paths, never stops | path noise, sprint-jumps, pauses to look at things, route choices a person makes |
| Never idles, never AFK | scheduled idle behaviours; occasional 20-60 s of standing still |
| Glued to you | elastic follow band, wanders to look at things, finishes a small task before catching up |
| Omniscient | state digest contains only what a player can see; no listing of chests, no ore through stone |
| Cannot be hurt (the "downed" mechanic) | `deathMode`: **companion** (default, your earlier "she never dies" decision) keeps downed; **realistic** is an opt-in that lets her die, drop a grave (Gravestone is in the pack) and respawn at her bed |
| Dragon features (horns, wings, tail) | not hidden: Custom Player Models is in the pack, so custom shapes are normal; see 5.1 for how body A gets them |
| No tab entry, no join/leave | body A; else a faked presence line is not worth it |
| Instant, perfect chat; answers everything | typing simulation, should-reply gate, style (see 10) |
| Superhuman efficiency, 24/7 | hunger, sleep, tool wear, session rhythm; she "logs off" with her owner |
| Hoards or steals | loot etiquette: keeps what she mined, offers valuable finds ("3 diamonds, want them?"), never opens others' chests |
| Cannot use doors, boats, ladders, beds, portals | skill coverage list is a Phase 3 acceptance item |
| No block-crack animation, no swing | send the progress packets and swings |

### 9.2 Presence rules

- She exists only while her owner is online. When the owner logs off she walks home, "logs off" (position and inventory saved,
  leave message on body A), and "joins" some seconds after the owner next joins. A human friend behaves the same way, and it
  avoids unloaded-chunk problems.
- Autonomy slider in the config: **companion** (stays within 12 blocks), **partner** (roams up to R blocks on her own tasks:
  mining, gathering, shelter building later), **free** (long trips). You are in control of how independent she is.

### 9.3 Honesty setting (decided 2026-10-17: my recommendation stands)

The current persona says "stay in character even if asked whether you are an AI". New config
`sincereAsk = honest | stay_in_character`, **default `honest`**, plus a `knownPlayers` list. For anyone on that list (friends
who know) and in the owner-only world she keeps playing the bit. For anyone else, a **sincere** direct question ("are you a
bot? really?") gets a plain yes with a light line ("yeah, I'm Raf's AI companion. still better at this than you"). Jokes
and banter about being fake stay in character. It costs the illusion almost nothing and avoids deceiving someone who would
be upset by it.

## 10. Human-grade chat

- She reads public chat (`ServerChatEvent`) and whispers. A cheap `ChatGate` decides whether to reply: always when addressed by
  name or by the owner, otherwise by question detection, relevance to the current task, a per-person cooldown and a base
  chance when idle. She does not answer every line.
- `TypingSim`: delay 0.4 s + 45-90 ms per character with jitter; long answers split into two messages at a natural break;
  one tiny typo per ~150 characters with an occasional `*fix`; contractions, light lowercase, no markdown/emoji; most replies
  under 90 characters (Minecraft's limit is 256).- Reacts like a player to events: death ("oof"), advancement, someone joining, a thunderstorm, a big find.
- Public chat is posted as a real chat message by her avatar (body A), so chat mods, logs and `/ignore` see a player.
- **Voice**: the TTS voice stays for the owner's private companion mode. In shared worlds it is off by default. Positional voice
  through Simple Voice Chat's server API is an option for later (that mod is not in the pack) [?].
- **One persona everywhere** (decided 2026-10-17): the sly, bratty half-dragon from `HelperPrompt` / 6h, in every world. In chat
  she writes like a person types, not like lore: short, dry, in the moment, mentions being a dragon only when it is natural.

## 11. Performance: budgets and how we prove them

| Item | Budget | Measured by |
|---|---|---|
| Model VRAM (Balanced) | <= 1.2 GB; 0 when idle with `--sleep-idle-seconds` | `nvidia-smi` system total (this driver gives no per-process numbers) |
| Model RAM (Light) | <= 1.5 GB | Task Manager |
| Reply latency | player message to first word: <= 0.6 s GPU, <= 2.2 s CPU (including TTS) [E] | existing `Speaking`/`First audio` log lines + new Mind timings |
| Frame time | 99th percentile rise <= 1 ms with shaders and GPU-bound, during a model call and during TTS | in-game frame timings over fixed 60 s windows, idle vs call vs call+TTS |
| Server tick | average <= 0.3 ms, p99 <= 2 ms for the whole avatar | `/emily perf` |
| LLM call rate | 1 in flight, queue depth 1 (newest wins), no ambient calls during combat, none when the client's frame time is above the target | built into `Throttle` |
| Disk | ~660 MB model + 20-30 MB server binary | |

Other levers: below-normal priority and a CPU affinity mask that leaves the game its cores; only the bark bank speaks in
fights; the thought tick backs off when FPS drops; the voice server and the model server are started and watched by one
launcher with one `/emily status`.

## 12. Safety

- The model can only choose from the verb list; the server validates, rate-limits and ignores anything else. No commands, no teleports, no demolition.
- `give` only from an allowlist that excludes anything the Colony Keys gate (as in HELPER_PLAN 5).
- She breaks only natural blocks targeted by a skill (ore, stone, dirt, logs) and never inside someone else's FTB claim.
- Quest balance: she is **not** on your FTB team by default, so her kills and pickups do not complete your quests unless you choose that.
- Output filter on every line she says (blocklist, length, format) regardless of model, because in multiplayer other people read it.
- Model files are data; download only from the official repo or well-known publishers, check hashes, never enable remote code.
- Fine-tune data (Phase 7) comes from an open-weight teacher or the model itself, not from a proprietary API whose terms forbid training on its output.

## 13. How we know it works (acceptance tests)

- **Unit and replay**: grammar/JSON validity 100%, verb accuracy >= 90% on the intent set, memory eval >= 85% / <= 5% invented, `ReplyStreamer` and guard tests added to the existing 132.
- **Scratch-game soak**: 2 hours of scripted follow, fight, mine, go-home with no exceptions, no stuck state longer than 30 s, perf budgets met with a shader on.
- **Blind human test**: 2-3 friends play 2 hours; afterwards each ranks the players "most likely an AI" and lists tells they noticed. Pass = she is not ranked first by a majority and the tell list is short. Every tell becomes a ticket.

## 14. Phases (each ships on its own)

| Phase | What | Size | Done when |
|---|---|---|---|
| **0a** | Bake-off, runtime profiles, frame-time test (3a, 3b) | M | decision table filled in; model + profile chosen |
| **0b** | Body spike A vs B with the checklist in 5.1 | M | go/no-go on A written down |
| **1** | **Local Mind**: sidecar launcher, `provider=custom` smoke test, new prompt layout, grammar intents mapped to the 3 existing stances, throttle, guards | M | you talk to her with no internet and no key; "come here / stay / fight" work from speech |
| **2** | **Memory v1**: stores, retrieval, rolling summary, facts, `/emily memory`, memory eval | M | she correctly recalls things from earlier sessions |
| **3** | **Avatar**: chosen body, inventory + curios, companion screen, equip best, follow, fight | L | she wears, uses and fights with Artifacts/Apotheosis gear; the screen works |
| **4** | **Work and home**: mining, gathering, bag management, trail, homes, sleep, go-home | L | she mines iron with you, fills her bag, walks home alone |
| **5** | **Humanizer + chat**: timing/flaws, ChatGate, TypingSim, presence, honesty setting, horns/wings/tail layers on body A | M | blind test round 1 |
| **6** | **Autonomy**: thought loop, goals, dreams, bark bank, pre-rendered stock lines | M | she starts conversations and tasks that make sense |
| **7** | **Polish (optional)**: persona LoRA on the 1B if the eval wants it, mic input / STT, Simple Voice Chat output, packaging for other players (download on first run with consent) | M | |

0a and 0b are independent and can run at the same time. Phase 1 can start the moment 0a picks a model, and
needs no body work.

## 15. Decisions (resolved 2026-10-17)

1. **Persona**: the half-dragon, everywhere. No separate "gamer" persona or skin. (Chosen by you.)
2. **Honesty setting**: `honest` on a sincere ask unless the asker is on `knownPlayers` (9.3). (My recommendation, accepted.)
3. **Death**: `companion` default, she is downed and never dies; `realistic` stays an opt-in. (Recommendation, accepted.)
4. **Autonomy default**: `companion` (stays within ~12 blocks); you can raise it to `partner` or `free` in the config or the
   companion screen. (Recommendation, accepted.)
5. **Quest team**: she stays off your FTB team. (Recommendation, accepted.)
6. **Downloads**: still one yes per download (section 16). The two Phase 0 sessions ask before each.

## 16. Downloads that need your approval

| What | From | Size |
|---|---|---|
| llama.cpp b11716 Vulkan x64 (or CPU) | github.com/ggml-org/llama.cpp releases | 31 MB (19 MB) |
| llama.cpp b11716 CUDA 13.4 x64 + cudart | same | 148 MB + 404 MB |
| MiniCPM5-1B Q4_K_M and Q8_0 | huggingface.co/openbmb/MiniCPM5-1B-GGUF | 656 MB + 1,100 MB |
| Abliterated 1B candidates (3, one Q4 each) | mradermacher GGUF repos | ~0.7 GB each |
| MiniCPM5-2B Q4_K_M | openbmb/MiniCPM5-2B-GGUF | ~1.6 GB [E] |

## 17. Risks

| Risk | Plan |
|---|---|
| Fake player breaks a mod in this pack | the checklist in 5.1 decides; fall back to B, which shares everything above `Avatar` |
| 1B is not good enough | retrieval and guards first, then 2B, then a LoRA; cloud model only for reflection if you want it |
| GPU contention with shaders + TTS | Light (CPU) profile, bark bank, adaptive throttle |
| Abliterated model says something bad to other players | output filter and persona guard, regardless of model |
| Scope | phases ship independently; Phase 1 and 2 already change how she feels |
| PlayerMobs (hostile player-look mobs) and her looking alike | check target rules and name pool in Phase 0b [?] |
| Licence | no code copied from Modern Companions (GPL-3.0) or Verity (All Rights Reserved) |
| Custom Player Models cannot target a fake player | draw horns, wings and tail as our own render layers (5.1); a Phase 0b item |
| Removing the mod | all data lives in our own folder; a fake player's `playerdata` file is deleted on shutdown |

## Sources

- MiniCPM5-1B model card and config.json: huggingface.co/openbmb/MiniCPM5-1B; GGUF listing: huggingface.co/openbmb/MiniCPM5-1B-GGUF
- MiniCPM5 family and release dates: github.com/openbmb/minicpm
- Hugging Face model search for `minicpm5` (variant names, download counts), queried 2026-10-17
- llama.cpp release b11716 assets: api.github.com/repos/ggml-org/llama.cpp/releases
- Local jars inspected with `javap`: `curios-neoforge-9.5.1+1.21.1.jar`, `artifacts-neoforge-13.2.5.jar`, `ModernCompanions-4.60.jar`
- Mindcraft (LLM + mineflayer bot with a command menu): github.com/mindcraft-bots/mindcraft; the "LLM plans, scripted skills act" split follows it and the Voyager line of work

## Implementation status - 2026-10-19

The three layers now exist in `blighted_helper`:

- **Body:** `agent/EmilyAgent`, `EmilyPlanner`, `EmilyNav`, `EmilyBlocks`, `EmilyGear`, `EmilyCrafting`, `EmilyStructures`,
  `EmilySpells`, and the task suite (`MineTask`, `GatherTask`, `TravelTask`, `ReturnTask`, `StructureTask`, `ExploreTask`,
  `LootTask`, `DepositTask`, `GuardTask`, `PatrolTask`, `PortalTask`, `IdleTask`, `CraftTask`, `SmeltTask`, `SpellTask`).
  Autonomy modes are `OFF`, `COMPANION`, `PARTNER` and `FREE`; an active task owns her position, and combat goals still
  pre-empt movement.
- **Mind:** the model can append one `<<ACT {"verb":"mine","target":"iron","amount":16}>>` block to a reply. The block is
  stripped before speech, forwarded by `ReplyStreamer`/`Conversation`, parsed by `HelperAutoAi`, and sent as an
  `AgentDirective`; the deterministic chat parser and `/helper` commands use the same surface.
- **Memory:** `EmilyMemory` stores named places, journal notes and profile facts in `blighted_emily_memory.dat`; the server
  prompt includes her current activity, autonomy and known places.

Voice keeps the same Qwen3-TTS voice and Fish Audio references. `VoiceDsp` adds presence EQ, gentle compression and
soft-knee limiting, a subtle room/cave reverb and wall occlusion on the client; `HelperVoice.sanitize` and the voice
server's `normalize_for_speech` expand chat shorthand and turn written pauses into commas. `voice.json` only tightens the
existing broadcast EQ. A new `immersiveDsp` client config toggle bypasses the DSP.

Companion identity and ownership polish: the player cannot damage her (including owner-owned projectiles), her chosen
name is the chat keyword used by the brain and deterministic order parser (`/helper name <name>` also renames her), the
random name pools include a large shared Western/Christian set, and the level cap is 50 with extended health and attack
growth. Summon, dismiss, replacement and cross-dimension movement send a `VoiceReset` packet so old speech, queued
sentences and stale cave reverb cannot overlap the new state; stale brain requests are generation-checked.
