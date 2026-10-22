package com.blighteddreams.helper.client.brain;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.BrainConfig;
import com.blighteddreams.helper.HelperConfig;
import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.VoiceConfig;
import com.blighteddreams.helper.client.voice.HelperVoice;
import com.blighteddreams.helper.client.voice.VoiceServerLauncher;
import com.blighteddreams.helper.network.HelperPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import java.util.regex.Matcher;

import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Conversational and in-game reactivity for Emily:
 *  - Player must address her by her chosen name ("Kaida, ...", "@kaida") OR look directly at her while chatting.
 *  - Intercepts natural orders ("stop attacking", "follow", "stay", "aggressive") and applies them immediately.
 *  - Proactive reactions are rare and brief (one occasional sentence, companion-like, not constant yapping).
 *  - Warm, loyal, grounded companion personality with lighthearted humor.
 */
@EventBusSubscriber(modid = BlightedHelper.MOD_ID, value = Dist.CLIENT)
public final class HelperAutoAi {
    private static final Random RNG = new Random();
    private static final Pattern BRACKET_PATTERN = Pattern.compile("\\[[^\\]]*\\]\\s*");
    private static final Pattern PAREN_PATTERN = Pattern.compile("\\([^)]*\\)\\s*");
    private static final Pattern ASTERISK_PATTERN = Pattern.compile("\\*[^*]*\\*\\s*");

    private static boolean greetedThisSession = false;
    private static boolean wasDowned = false;
    private static volatile String cachedCompanionName = null; // config is not loaded at class-init time; filled in on first use
    private static boolean wasPlayerDead = false;
    private static ResourceLocation lastDimension = null;
    private static int peakHostilesInFight = 0;

    // Minimum cooldown between ANY unprompted ambient quip: 4 minutes
    private static final long GLOBAL_PROACTIVE_COOLDOWN_MS = 240_000L;
    private static long lastProactiveMs = 0L;
    private static long lastLowHpWarnMs = 0L;
    private static long lastVictoryQuipMs = 0L;
    private static long lastClumsyQuipMs = 0L;
    private static long lastStareQuipMs = 0L;
    private static long lastDimensionQuipMs = 0L;

    private static long stareStartMs = 0L;
    private static float lastHealth = 20.0F;

    private static final String[][] SUMMON_LINES = {
            {"happy", "Hey! Ready to explore whenever you are."},
            {"smile", "Right behind you. Let's see what we find today!"},
            {"chuckle", "Here! Don't worry, got your back as always."},
            {"happy", "Ready when you are, partner. Lead the way."}
    };
    private static final String[][] DOWNED_LINES = {
            {"worried", "Oof... give me a second to catch my breath. Keep yourself safe!"},
            {"battle", "Ow, that one packed a punch. I'll be back up in a moment!"},
            {"chuckle", "Don't mind me, just taking a quick tactical breather. Hold on!"}
    };
    private static final String[][] STANDUP_LINES = {
            {"battle", "Alright, back on my feet! Let's get them."},
            {"happy", "I'm good! Thanks for covering me."},
            {"chuckle", "Okay, my turn. Let's finish this!"}
    };
    private static final String[][] LOW_HP_LINES = {
            {"worried", "Watch out, you're low on health! Back up and heal, I'll cover you!"},
            {"battle", "Careful, you're hurting! Step back, I've got this one!"}
    };
    private static final String[][] VICTORY_LINES = {
            {"happy", "Whew, nice teamwork! That was a close one."},
            {"chuckle", "That was chaotic, but we got them all!"},
            {"happy", "Another battle won! We make a pretty great team."}
    };
    private static final String[][] CLUMSY_LINES = {
            {"chuckle", "Ouch! You okay? That looked like a nasty drop."}
    };
    private static final String[][] RESPAWN_LINES = {
            {"worried", "Glad you're back in one piece! That was a rough one."},
            {"chuckle", "Welcome back! Let's go retrieve your stuff."}
    };
    private static final String[][] STARE_LINES = {
            {"happy", "Hey there! What's on your mind, partner?"},
            {"chuckle", "Everything alright, or just spacing out?"},
            {"happy", "Ready to move out whenever you are!"}
    };
    private static final String[][] DIMENSION_LINES = {
            {"battle", "Watch your step here... this place doesn't look friendly at all."},
            {"whisper", "Stay close. I've got your back in here."}
    };

    private HelperAutoAi() {}

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        greetedThisSession = false;
        wasDowned = false;
        wasPlayerDead = false;
        lastDimension = null;
        peakHostilesInFight = 0;
        stareStartMs = 0L;
        lastHealth = 20.0F;
        cachedCompanionName = HelperConfig.name();
        lastProactiveMs = System.currentTimeMillis(); // Don't yap immediately on login
        VoiceServerLauncher.ensureRunningAsync();
        MindServerLauncher.ensureRunningAsync();
        HelperMemory.ensureLoaded();
    }

    /**
     * Checks if the player is looking directly at Emily.
     */
    private static boolean isLookingAtEmily(Minecraft mc) {
        if (mc.player == null || mc.level == null) return false;
        if (mc.crosshairPickEntity instanceof HelperEntity he && he.isOwner(mc.player)) {
            return true;
        }
        List<HelperEntity> helpers = mc.level.getEntitiesOfClass(HelperEntity.class, mc.player.getBoundingBox().inflate(6.0D), e -> e.isOwner(mc.player));
        if (!helpers.isEmpty()) {
            HelperEntity h = helpers.get(0);
            Vec3 look = mc.player.getViewVector(1.0F).normalize();
            Vec3 toHelper = h.getEyePosition().subtract(mc.player.getEyePosition()).normalize();
            return look.dot(toHelper) > 0.88;
        }
        return false;
    }

    /** The companion currently loaded near the player, if any. */
    private static HelperEntity findCompanion(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        List<HelperEntity> helpers = mc.level.getEntitiesOfClass(
                HelperEntity.class,
                mc.player.getBoundingBox().inflate(256.0D),
                e -> e.isOwner(mc.player)
        );
        return helpers.isEmpty() ? null : helpers.get(0);
    }

    /** Uses her chosen name (custom or config default) and caches it for non-game threads such as the model prompt. */
    private static String refreshCompanionName(Minecraft mc) {
        HelperEntity helper = findCompanion(mc);
        if (helper != null) {
            String name = helper.getName().getString();
            if (name != null && !name.isBlank()) cachedCompanionName = name;
        }
        if (cachedCompanionName == null || cachedCompanionName.isBlank()) {
            cachedCompanionName = HelperConfig.name();
        }
        return cachedCompanionName;
    }

    /** Her current name. Safe to call from any thread. */
    public static String companionName() {
        String name = cachedCompanionName;
        return name == null || name.isBlank() ? "Emily" : name;
    }

    /** All names that can address her: the chosen name and the config default as a fallback. */
    private static List<String> companionAliases(Minecraft mc) {
        List<String> aliases = new ArrayList<>();
        for (String candidate : new String[]{refreshCompanionName(mc), HelperConfig.name()}) {
            if (candidate == null || candidate.isBlank()) continue;
            boolean duplicate = false;
            for (String existing : aliases) {
                if (existing.equalsIgnoreCase(candidate)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) aliases.add(candidate);
        }
        return aliases;
    }

    /** Called when the companion is summoned, dismissed or replaced, so stale speech never overlaps the new state. */
    public static void onCompanionChanged() {
        greetedThisSession = false;
        wasDowned = false;
        cachedCompanionName = HelperConfig.name();
        HelperVoice.reset();
    }

    /**
     * Chat hook:
     * - Only triggers if addressed by her chosen name ("Kaida, ...", "@kaida") OR looking at her while speaking.
     * - Directly executes action orders like "stop attacking", "follow", "stay", "aggressive".
     */
    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        if (!VoiceConfig.proactiveAi()) {
            return;
        }
        String raw = event.getMessage();
        if (raw == null) {
            return;
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("/")) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        boolean lookingAtHer = isLookingAtEmily(mc);

        String name = refreshCompanionName(mc);
        String lower = trimmed.toLowerCase(Locale.ROOT);

        boolean addressed = false;
        String question = null;

        // Accept her chosen name first, then the config name as a familiar fallback.
        for (String alias : companionAliases(mc)) {
            String aliasLower = alias.toLowerCase(Locale.ROOT);
            if (lower.startsWith("@" + aliasLower) || lower.startsWith("!" + aliasLower)) {
                addressed = true;
                question = trimmed.substring(alias.length() + 1).replaceFirst("^[,:\\s]+", "");
                break;
            } else if (lower.startsWith(aliasLower + ",") || lower.startsWith(aliasLower + ":")) {
                addressed = true;
                question = trimmed.substring(alias.length() + 1).strip();
                break;
            } else if (lower.startsWith(aliasLower + " ") && trimmed.length() > alias.length() + 2) {
                addressed = true;
                question = trimmed.substring(alias.length() + 1).strip();
                break;
            } else if (BrainConfig.nameAnywhere()
                    && Pattern.compile("\\b" + Pattern.quote(aliasLower) + "\\b").matcher(lower).find()) {
                addressed = true;
                question = trimmed;
                break;
            }
        }

        if (!addressed && lookingAtHer) {
            // Player is looking directly at her while speaking
            addressed = true;
            question = trimmed;
        }

        // If neither addressed nor looking at her: DO NOT intercept the chat!
        if (!addressed || question == null || question.isEmpty()) {
            return;
        }

        // Cancel the public chat message so it doesn't clutter public chat
        event.setCanceled(true);
        greetedThisSession = true;

        String playerName = mc.player != null ? mc.player.getName().getString() : "You";
        HelperVoice.chat(Component.literal("<" + playerName + " -> " + name + "> ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(question).withStyle(ChatFormatting.WHITE)));

        // Check for direct combat/stance orders:
        String order = question.toLowerCase(Locale.ROOT).replaceAll("[!.,?;:]+", "").trim();

        if (order.matches(".*\\b(stop attacking|stop attack|stop fighting|stop that|stop|hold fire|hold your fire|cease fire|stand down|cancel attack|disengage|break off)\\b.*")) {
            PacketDistributor.sendToServer(new HelperPayloads.ActionRequest("stop", ""));
            String[] lines = {
                    "Got it, stopping!",
                    "Standing down.",
                    "Alright, holding fire.",
                    "Disengaging, right beside you."
            };
            speakDirect(name, lines[RNG.nextInt(lines.length)], "calm");
            return;
        }

        if (order.matches(".*\\b(follow me|follow|come with me|come here|with me|heel|let's go|lets go)\\b.*")) {
            PacketDistributor.sendToServer(new HelperPayloads.ActionRequest("stance", "FOLLOW"));
            String[] lines = {
                    "Right behind you!",
                    "Lead the way.",
                    "Following you!",
                    "Right beside you, let's go."
            };
            speakDirect(name, lines[RNG.nextInt(lines.length)], "happy");
            return;
        }

        if (order.matches(".*\\b(stay here|stay put|stay|wait here|wait for me|wait|hold position|hold ground|sit)\\b.*")) {
            PacketDistributor.sendToServer(new HelperPayloads.ActionRequest("stance", "STAY"));
            String[] lines = {
                    "Holding position here. Stay safe!",
                    "Got it, I'll wait right here.",
                    "Staying put. Call me when you need me.",
                    "Holding the line here."
            };
            speakDirect(name, lines[RNG.nextInt(lines.length)], "calm");
            return;
        }

        if (order.matches(".*\\b(aggressive|attack all|attack everything|get them|charge|slay them)\\b.*")) {
            PacketDistributor.sendToServer(new HelperPayloads.ActionRequest("stance", "AGGRESSIVE"));
            String[] lines = {
                    "Going on the offensive! Let's clear them out.",
                    "Time to fight. Watching for threats!",
                    "Aggressive stance it is. Let's get them!"
            };
            speakDirect(name, lines[RNG.nextInt(lines.length)], "battle");
            return;
        }

        if (order.matches(".*\\b(open inventory|inventory|open bag|check bag|bag)\\b.*")) {
            PacketDistributor.sendToServer(new HelperPayloads.ActionRequest("inventory", ""));
            return;
        }

        // 1. Targeted Combat Command: e.g. "Emily kill that skeleton over there", "kill that creeper", "attack that"
        Matcher attackMatcher = Pattern.compile("^\\b(?:kill|attack|slay|take down|take out|destroy|get|smite)\\b(?:\\s+(?:that|the|this|a|an))?(?:\\s+(?<target>[a-z_ ]+?))?(?:\\s+(?:over there|in front of (?:me|us)|near (?:me|us)|for me|right now))?$", Pattern.CASE_INSENSITIVE).matcher(order);
        if (attackMatcher.matches()) {
            String targetQuery = attackMatcher.group("target");
            LivingEntity targetEntity = findTargetEntity(mc, targetQuery);
            if (targetEntity != null) {
                PacketDistributor.sendToServer(new HelperPayloads.ActionRequest("target", String.valueOf(targetEntity.getId())));
                String mobName = targetEntity.getName().getString();
                String[] attackLines = {
                    "On it! That " + mobName + " is done for.",
                    "Target acquired! Watch this.",
                    "Consider it handled! Charging in!",
                    "You got it, taking down that " + mobName + "!"
                };
                speakDirect(name, attackLines[RNG.nextInt(attackLines.length)], "battle");
                return;
            } else {
                String display = (targetQuery != null && !targetQuery.isBlank() && !targetQuery.equals("it") && !targetQuery.equals("him") && !targetQuery.equals("that"))
                        ? targetQuery : "enemies";
                speakDirect(name, "I don't see any " + display + " around here! Point me in the right direction.", "worried");
                return;
            }
        }

        // 2. Navigation / Home Coordinates Command: "Emily how do I get home", "where is home", "cords to home"
        if (order.matches(".*\\b(how do (?:i|we) get home|where is (?:home|my bed|our bed|our base)|where's (?:home|my bed|our bed|our base)|which way is home|cords (?:to )?home|coordinates (?:to )?home|directions? (?:to )?home|take (?:me|us) home)\\b.*")) {
            final String queryForBrain = question;
            ServerStateCache.fetch(600, serverFacts -> {
                String navReply = parseNavReply(serverFacts);
                if (navReply != null && !navReply.isBlank()) {
                    speakDirect(name, navReply, "calm");
                } else {
                    HelperBrain.ask(queryForBrain);
                }
            });
            return;
        }

        // Regular conversation query: pass to LLM mind
        if (tryAgentOrder(question)) {
            return;
        }
        HelperBrain.ask(question);
    }

    /**
     * Deterministic order parser for the body. It only fires on imperative, recognizable orders; everything else falls
     * through to the model so Emily still talks like herself. The server confirms and the voice speaks the result.
     */
    private static boolean tryAgentOrder(String question) {
        String q = question.toLowerCase(Locale.ROOT).trim();
        q = q.replaceAll("[!?.,;:]+$", "").trim();
        q = q.replaceFirst("^(please|hey|okay|ok)\\s+", "");
        q = q.replaceFirst("^(can|could|would|will)\\s+you\\s+", "");
        if (q.isEmpty()) return false;

        // autonomy
        Matcher aut = Pattern.compile("^(?:set\\s+)?(?:your\\s+)?(?:autonomy|mode)\\s+(?:to\\s+)?(off|none|manual|companion|close|partner|free|wild|independent)$").matcher(q);
        if (!aut.matches()) {
            aut = Pattern.compile("^(off|manual|companion|partner|free|independent)\\s+mode$").matcher(q);
        }
        if (aut.matches()) {
            sendAgent("autonomy", aut.group(1), 0, "");
            return true;
        }

        if (q.matches("^(stop|cancel|halt|stand down|stop what you'?re doing|stop working)$")) {
            sendAgent("stop", "", 0, "");
            return true;
        }
        if (q.matches("^(status|report|what are you doing|what'?s your status|what are you up to|what are you doing right now)$")) {
            sendAgent("status", "", 0, "");
            return true;
        }
        if (q.matches("^(come|come back|return|return to me|get back here)$")) {
            sendAgent("come", "", 0, "");
            return true;
        }

        // mine / dig
        Matcher m = Pattern.compile("^(?:mine|dig|excavate)\\b\\s*(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            int amount = amountOf(rest);
            String target = cleanTarget(removeAmount(rest));
            if (target.contains("wood") || target.contains("tree") || target.contains("log")) {
                sendAgent("chop", "logs", amount <= 0 ? 32 : amount, "");
            } else {
                sendAgent("mine", target.isBlank() ? "any ore" : target, amount, "");
            }
            return true;
        }

        // gather / chop
        m = Pattern.compile("^(?:gather|collect|harvest|chop|get)\\b\\s*(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            int amount = amountOf(rest);
            String target = cleanTarget(removeAmount(rest));
            if (target.contains("wood") || target.contains("log") || target.contains("tree") || target.contains("timber")) {
                sendAgent("chop", "logs", amount <= 0 ? 32 : amount, "");
            } else if (target.contains("food") || target.contains("crop") || target.contains("wheat") || target.contains("farm")) {
                sendAgent("gather", "mature crops", amount <= 0 ? 16 : amount, "");
            } else {
                sendAgent("gather", target.isBlank() ? "mature crops" : target, amount, "");
            }
            return true;
        }

        // loot / deposit
        if (q.matches("^(?:loot|open)\\b.*$") || q.matches("^(?:search|raid)\\s+(?:for\\s+)?(?:chests?|containers?|loot)$")) {
            sendAgent("loot", "", amountOf(q), "");
            return true;
        }
        if (q.matches("^(?:deposit|store|unload|put away)\\b.*$")) {
            sendAgent("deposit", "", amountOf(q), "");
            return true;
        }

        // explore
        m = Pattern.compile("^(?:explore|scout|look around|venture)\\b\\s*(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            int distance = amountOf(rest);
            String direction = "";
            for (String d : new String[]{"north", "south", "east", "west"}) {
                if (rest.contains(d)) {
                    direction = d;
                    break;
                }
            }
            sendAgent("explore", "", distance, direction);
            return true;
        }

        // find a structure
        m = Pattern.compile("^(?:find|locate|look for|search for)\\b\\s*(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            if (rest.contains("ore") || rest.contains("diamond") || rest.contains("iron") || rest.contains("gold")
                    || rest.contains("coal") || rest.contains("redstone") || rest.contains("lapis") || rest.contains("copper")) {
                sendAgent("mine", cleanTarget(removeAmount(rest)), amountOf(rest), "");
                return true;
            }
            if (rest.contains("wood") || rest.contains("tree") || rest.contains("log")) {
                sendAgent("chop", "logs", amountOf(rest), "");
                return true;
            }
            if (rest.contains("chest") || rest.contains("container") || rest.contains("loot")) {
                sendAgent("loot", "", amountOf(rest), "");
                return true;
            }
            sendAgent("find", cleanTarget(removeAmount(rest)), 0, "");
            return true;
        }

        // portal before generic crafting
        if (q.matches("^(?:build|make|create|construct)\\s+(?:a\\s+|the\\s+)?(?:nether\\s+)?portal$")
                || q.matches("^(?:nether\\s+)?portal$")) {
            sendAgent("portal", "", 0, "");
            return true;
        }

        // crafting / smelting
        m = Pattern.compile("^(?:craft|make|build)\\b\\s*(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            int amount = amountOf(rest);
            String target = cleanTarget(removeAmount(rest));
            if (target.contains("portal")) {
                sendAgent("portal", "", 0, "");
            } else if (target.isBlank()) {
                return false;
            } else {
                sendAgent("craft", target, amount <= 0 ? 1 : amount, "");
            }
            return true;
        }
        m = Pattern.compile("^(?:smelt|cook|melt)\\b\\s*(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            int amount = amountOf(rest);
            String target = cleanTarget(removeAmount(rest));
            if (target.isBlank()) return false;
            sendAgent("smelt", target, amount <= 0 ? 8 : amount, "");
            return true;
        }

        // remember a place
        m = Pattern.compile("^(?:remember|memorize|mark|save)\\b\\s*(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            rest = rest.replaceFirst("^(?:this|the)\\s+(?:place|spot|location|area)\\s+", "");
            if (rest.startsWith("as ")) rest = rest.substring(3).trim();
            if (rest.contains(" as ")) {
                String[] split = rest.split("\\s+as\\s+", 2);
                if (split[0].contains("here") || split[0].contains("this")) rest = split[1];
                else rest = split[0];
            }
            rest = rest.replaceFirst("\\s+(?:here|right here)$", "").trim();
            if (rest.isBlank() || rest.equals("this") || rest.equals("here")) rest = "here";
            sendAgent("remember", rest, 0, "");
            return true;
        }

        // teleport / go
        m = Pattern.compile("^(?:teleport|tp|warp)\\s+(?:to\\s+)?(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            String coords = coordsOf(rest);
            if (!coords.isEmpty()) sendAgent("teleport", coords, 0, coords);
            else sendAgent("teleport", cleanPlace(rest), 0, "");
            return true;
        }
        m = Pattern.compile("^(?:go|walk|travel|head|move)\\b(?:\\s+to)?\\s*(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            if (rest.isBlank()) return false;
            String coords = coordsOf(rest);
            if (!coords.isEmpty()) {
                sendAgent("goto", coords, 0, coords);
            } else {
                String place = cleanPlace(rest);
                sendAgent("goto", place, 0, "");
            }
            return true;
        }

        // guard / patrol
        m = Pattern.compile("^(?:guard|protect|defend|watch|watch over)\\b\\s*(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            int seconds = secondsOf(rest, 300);
            sendAgent("guard", "", seconds, "");
            return true;
        }
        m = Pattern.compile("^(?:patrol|walk the perimeter|check the area)\\b\\s*(.*)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            int seconds = secondsOf(rest, 300);
            sendAgent("patrol", "", seconds, "");
            return true;
        }

        // spells
        m = Pattern.compile("^(?:cast|use)\\s+(?:a\\s+)?(?:spell\\s+)?(.+)$").matcher(q);
        if (m.matches()) {
            String rest = m.group(1).trim();
            if (rest.equals("spell") || rest.isBlank()) return false;
            sendAgent("spell", rest, 0, "");
            return true;
        }

        if (q.matches("^(?:eat|have a snack|eat something)$")) {
            sendAgent("eat", "", 0, "");
            return true;
        }
        if (q.matches("^(?:equip|gear up|check your gear|put on your gear)$")) {
            sendAgent("equip", "", 0, "");
            return true;
        }
        return false;
    }

    /**
     * Executes a structured action the model appended to a reply: {@code <<ACT {"verb":"mine","target":"iron","amount":16}>>}.
     * This is the bridge between her imagination and her body. Unknown or malformed actions are ignored; the spoken
     * answer is unaffected.
     */
    public static void handleAction(String body) {
        if (body == null || body.isBlank()) return;
        String json = body.trim();
        String verb = "";
        String target = "";
        String extra = "";
        int amount = 0;
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
            if (parsed.isJsonObject()) {
                com.google.gson.JsonObject obj = parsed.getAsJsonObject();
                verb = jsonString(obj, "verb");
                if (verb.isBlank()) verb = jsonString(obj, "action");
                if (verb.isBlank()) verb = jsonString(obj, "act");
                target = jsonString(obj, "target");
                if (target.isBlank()) target = jsonString(obj, "what");
                extra = jsonString(obj, "extra");
                if (extra.isBlank()) extra = jsonString(obj, "direction");
                if (obj.has("amount") && obj.get("amount").isJsonPrimitive()) {
                    try {
                        amount = obj.get("amount").getAsInt();
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Not JSON: allow the same shorthand the chat parser understands.
        }
        if (verb.isBlank()) {
            Minecraft.getInstance().execute(() -> tryAgentOrder(json));
            return;
        }
        String v = verb.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        v = switch (v) {
            case "dig", "excavate", "mining" -> "mine";
            case "chop wood", "wood", "gather wood", "lumber" -> "chop";
            case "harvest", "collect", "pick" -> "gather";
            case "travel", "walk", "go", "head", "move" -> "goto";
            case "tp", "warp", "teleport to" -> "teleport";
            case "return", "come back", "regroup" -> "come";
            case "search", "raid", "open chests" -> "loot";
            case "store", "unload", "put away" -> "deposit";
            case "make", "build", "construct" -> "craft";
            case "cook", "melt" -> "smelt";
            case "protect", "defend", "watch" -> "guard";
            case "scout", "look around" -> "explore";
            case "mark", "save", "memorize" -> "remember";
            case "cast", "use spell" -> "spell";
            case "nether portal", "build portal", "make portal" -> "portal";
            case "status", "report", "what are you doing" -> "status";
            default -> v;
        };
        if (v.equals("goto") && extra.isBlank() && target.matches("-?\\d+\\s+-?\\d+\\s+-?\\d+")) {
            extra = target;
        }
        final String fv = v;
        final String ft = target == null ? "" : target.trim();
        final String fe = extra == null ? "" : extra.trim();
        final int fa = Math.max(0, Math.min(256, amount));
        Minecraft.getInstance().execute(() -> sendAgent(fv, ft, fa, fe));
    }

    private static void sendAgent(String verb, String target, int amount, String extra) {
        PacketDistributor.sendToServer(new HelperPayloads.AgentDirective(verb, target == null ? "" : target,
                amount, extra == null ? "" : extra));
    }

    private static String jsonString(com.google.gson.JsonObject obj, String key) {
        try {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) return obj.get(key).getAsString();
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    private static String cleanTarget(String text) {
        String s = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        s = s.replaceFirst("^(?:for|of|some|a|an|the)\\s+", "");
        s = s.replaceFirst("\\s+(?:please|for me|right now|now)$", "");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private static String cleanPlace(String text) {
        String s = cleanTarget(text);
        s = s.replaceFirst("^(?:the|our|my)\\s+", "");
        return s;
    }

    private static int amountOf(String text) {
        if (text == null) return 0;
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher stack = Pattern.compile("\\b(\\d+)\\s+stacks?\\b").matcher(lower);
        if (stack.find()) return safeInt(stack.group(1)) * 64;
        if (lower.contains("a stack") || lower.contains("one stack")) return 64;
        Matcher m = Pattern.compile("\\b(\\d+)\\b").matcher(lower);
        if (m.find()) return safeInt(m.group(1));
        return 0;
    }

    private static String removeAmount(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)\\b\\d+\\s+stacks?\\b", "")
                .replaceAll("(?i)\\b(?:a|one)\\s+stack\\b", "")
                .replaceAll("\\b\\d+\\b", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static int secondsOf(String text, int fallback) {
        int n = amountOf(text);
        if (n <= 0) return fallback;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("minute") || lower.contains("min")) return Math.min(1800, n * 60);
        return Math.min(1800, Math.max(10, n));
    }

    private static String coordsOf(String text) {
        Matcher m = Pattern.compile("(-?\\d+)\\s*[, ]\\s*(-?\\d+)\\s*[, ]\\s*(-?\\d+)").matcher(text);
        return m.find() ? m.group(1) + " " + m.group(2) + " " + m.group(3) : "";
    }

    private static int safeInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static LivingEntity findTargetEntity(Minecraft mc, String targetQuery) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return null;

        String query = targetQuery == null ? "" : targetQuery.trim().toLowerCase(Locale.ROOT);
        boolean generic = query.isEmpty() || query.equals("it") || query.equals("him") || query.equals("that")
                || query.equals("guy") || query.equals("thing") || query.equals("mob") || query.equals("monster")
                || query.equals("over there");

        // 1. Crosshair direct entity pick
        if (mc.crosshairPickEntity instanceof LivingEntity le && le.isAlive() && le != player && !(le instanceof HelperEntity)) {
            if (generic || matchesTarget(le, query)) {
                return le;
            }
        }

        // 2. Line of sight / raycast check (up to 32 blocks in view vector)
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 reach = eye.add(look.scale(32.0));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(32.0)).inflate(2.0);

        LivingEntity bestRayHit = null;
        double bestDistSq = Double.MAX_VALUE;

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, searchBox,
                ent -> ent.isAlive() && ent != player && !(ent instanceof HelperEntity))) {
            if (!generic && !matchesTarget(e, query)) continue;
            AABB hitBox = e.getBoundingBox().inflate(0.5);
            var optHit = hitBox.clip(eye, reach);
            if (optHit.isPresent()) {
                double d = eye.distanceToSqr(optHit.get());
                if (d < bestDistSq) {
                    bestDistSq = d;
                    bestRayHit = e;
                }
            }
        }
        if (bestRayHit != null) {
            return bestRayHit;
        }

        // 3. Proximity search within 32 blocks, prioritizing entities in front of the player
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32.0),
                ent -> ent.isAlive() && ent != player && !(ent instanceof HelperEntity));

        LivingEntity bestNearby = null;
        double bestScore = -Double.MAX_VALUE;

        for (LivingEntity e : nearby) {
            if (!generic && !matchesTarget(e, query)) continue;
            if (generic && !(e instanceof net.minecraft.world.entity.monster.Enemy)) continue;

            Vec3 toEnt = e.position().subtract(player.position()).normalize();
            double dot = look.dot(toEnt);
            double dist = player.distanceTo(e);

            double score = (dot * 25.0) - dist;
            if (score > bestScore) {
                bestScore = score;
                bestNearby = e;
            }
        }

        return bestNearby;
    }

    private static boolean matchesTarget(LivingEntity e, String query) {
        if (query.isEmpty()) return true;
        String typeName = e.getType().getDescription().getString().toLowerCase(Locale.ROOT);
        String regKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().toLowerCase(Locale.ROOT);
        if (typeName.contains(query) || regKey.contains(query)) return true;

        if (query.contains("skeleton") && (typeName.contains("skeleton") || regKey.contains("stray") || regKey.contains("wither_skeleton"))) return true;
        if (query.contains("zombie") && (typeName.contains("zombie") || regKey.contains("husk") || regKey.contains("drowned"))) return true;
        if (query.contains("spider") && (typeName.contains("spider") || regKey.contains("cave_spider"))) return true;
        if (query.contains("creeper") && typeName.contains("creeper")) return true;
        if (query.contains("enderman") && typeName.contains("enderman")) return true;
        if (query.contains("witch") && typeName.contains("witch")) return true;
        if (query.contains("phantom") && typeName.contains("phantom")) return true;

        return false;
    }

    private static String parseNavReply(String serverFacts) {
        if (serverFacts == null || serverFacts.isBlank()) return null;
        // Search for NAV_DATA: bed=1 x=... y=... z=... dist=... dir=... sameDim=1
        Pattern p = Pattern.compile("NAV_DATA:\\s+bed=(?<bed>[01])\\s+x=(?<x>-?\\d+)\\s+y=(?<y>-?\\d+)\\s+z=(?<z>-?\\d+)\\s+dist=(?<dist>\\d+)\\s+dir=(?<dir>[A-Za-z-]+)\\s+sameDim=(?<sameDim>[01])");
        Matcher m = p.matcher(serverFacts);
        if (m.find()) {
            boolean bed = "1".equals(m.group("bed"));
            String x = m.group("x");
            String y = m.group("y");
            String z = m.group("z");
            String dist = m.group("dist");
            String dir = m.group("dir");
            boolean sameDim = "1".equals(m.group("sameDim"));

            if (bed) {
                if (sameDim) {
                    return "Home is at X: " + x + ", Z: " + z + "! That's about " + dist + " blocks to the " + dir + ". Head that way!";
                } else {
                    return "Our bed is at X: " + x + ", Z: " + z + ", but it's in the Overworld. We need to find a portal first!";
                }
            } else {
                return "You haven't slept in a bed yet, but world spawn is at X: " + x + ", Z: " + z + ", about " + dist + " blocks to the " + dir + "!";
            }
        }
        return null;
    }

    private static void speakDirect(String name, String line, String mood) {
        String clean = cleanSubtitles(line);
        if (BrainConfig.subtitles()) {
            HelperVoice.chat(Component.literal("<" + name + "> ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(clean).withStyle(ChatFormatting.WHITE)));
        }
        HelperVoice.speak(clean, mood);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!VoiceConfig.proactiveAi()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || (player.tickCount % 20) != 0) {
            return;
        }

        refreshCompanionName(mc);
        long now = System.currentTimeMillis();

        // 1. Check player death / respawn
        boolean isDeadNow = player.isDeadOrDying() || player.getHealth() <= 0.0F;
        if (isDeadNow && !wasPlayerDead) {
            wasPlayerDead = true;
            HelperMemory.recordDeath();
            return;
        } else if (!isDeadNow && wasPlayerDead) {
            wasPlayerDead = false;
            if (now - lastProactiveMs >= GLOBAL_PROACTIVE_COOLDOWN_MS) {
                lastProactiveMs = now;
                triggerProactive("The player just respawned beside you after dying. Welcome them back warmly in 1 short sentence.", RESPAWN_LINES);
                return;
            }
        }

        // 2. Dimension shift
        ResourceLocation currentDim = level.dimension().location();
        if (lastDimension == null) {
            lastDimension = currentDim;
        } else if (!lastDimension.equals(currentDim)) {
            ResourceLocation prevDim = lastDimension;
            lastDimension = currentDim;
            if (now - lastDimensionQuipMs >= 300_000L && now - lastProactiveMs >= GLOBAL_PROACTIVE_COOLDOWN_MS) {
                lastDimensionQuipMs = now;
                lastProactiveMs = now;
                String dimName = currentDim.getPath().replace('_', ' ');
                triggerProactive("You and the player entered " + dimName + " (from " + prevDim.getPath().replace('_', ' ') + "). Give 1 brief companion reaction.", DIMENSION_LINES);
                return;
            }
        }

        List<HelperEntity> helpers = level.getEntitiesOfClass(
                HelperEntity.class,
                player.getBoundingBox().inflate(36.0D),
                e -> e.isOwner(player)
        );
        if (helpers.isEmpty()) {
            return;
        }
        HelperEntity emily = helpers.get(0);

        // 3. First greeting on summon
        if (!greetedThisSession && emily.distanceTo(player) <= 16.0F) {
            greetedThisSession = true;
            lastProactiveMs = now;
            lastHealth = player.getHealth();
            triggerProactive("You have just been summoned beside the player. Greet your companion warmly in 1 short sentence.", SUMMON_LINES);
            wasDowned = emily.isDowned();
            return;
        }

        // 4. Downed / Stand up
        boolean downedNow = emily.isDowned();
        if (downedNow && !wasDowned) {
            wasDowned = true;
            if (now - lastProactiveMs >= 30_000L) {
                lastProactiveMs = now;
                triggerProactive("You took heavy damage and took a knee to catch your breath. Tell the player to stay safe in 1 short sentence.", DOWNED_LINES);
            }
            return;
        } else if (!downedNow && wasDowned) {
            wasDowned = false;
            if (now - lastProactiveMs >= 30_000L) {
                lastProactiveMs = now;
                triggerProactive("You recovered and stood back up ready to fight. Deliver 1 short battle-ready sentence.", STANDUP_LINES);
            }
            return;
        }

        // 5. Looking into her eyes (staring for 4 seconds)
        boolean isLookingAtEmily = mc.crosshairPickEntity == emily && emily.distanceTo(player) <= 5.0F;
        if (isLookingAtEmily) {
            if (stareStartMs == 0L) {
                stareStartMs = now;
            } else if (now - stareStartMs >= 4_000L) {
                stareStartMs = 0L;
                if (now - lastStareQuipMs >= 240_000L && now - lastProactiveMs >= GLOBAL_PROACTIVE_COOLDOWN_MS) {
                    lastStareQuipMs = now;
                    lastProactiveMs = now;
                    triggerProactive("The player has been looking directly at you. Give a friendly, lighthearted greeting asking what's on their mind in 1 sentence.", STARE_LINES);
                    return;
                }
            }
        } else {
            stareStartMs = 0L;
        }

        // 6. Track hostiles for combat reactions
        int hostiles = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(14.0D), Entity::isAlive).size();
        if (hostiles > 0) {
            peakHostilesInFight = Math.max(peakHostilesInFight, hostiles);
            if (player.getHealth() <= 6.0F && (now - lastLowHpWarnMs) >= 180_000L && (now - lastProactiveMs) >= GLOBAL_PROACTIVE_COOLDOWN_MS) {
                lastLowHpWarnMs = now;
                lastProactiveMs = now;
                triggerProactive("The player is critically low on health in combat! Tell them to step back and heal in 1 short sentence.", LOW_HP_LINES);
            }
        } else if (peakHostilesInFight >= 4) {
            peakHostilesInFight = 0;
            if ((now - lastVictoryQuipMs) >= 240_000L && (now - lastProactiveMs) >= GLOBAL_PROACTIVE_COOLDOWN_MS) {
                lastVictoryQuipMs = now;
                lastProactiveMs = now;
                triggerProactive("You and the player defeated a mob pack. Give 1 short, cheerful victory remark.", VICTORY_LINES);
            }
        } else {
            peakHostilesInFight = 0;

            // 7. Huge clumsy fall (> 12 damage / 6 hearts)
            float currentHealth = player.getHealth();
            if (lastHealth - currentHealth >= 12.0F && currentHealth > 0.0F) {
                if ((now - lastClumsyQuipMs) >= 300_000L && (now - lastProactiveMs) >= GLOBAL_PROACTIVE_COOLDOWN_MS) {
                    lastClumsyQuipMs = now;
                    lastProactiveMs = now;
                    HelperMemory.recordFall();
                    triggerProactive("The player just took a huge fall with no enemies around. Ask if they're okay with light humor in 1 sentence.", CLUMSY_LINES);
                }
            }
        }

        lastHealth = player.getHealth();
    }

    private static void triggerProactive(String eventCue, String[][] fallbackPool) {
        if (HelperBrain.isReadyForProactive()) {
            HelperBrain.react(eventCue);
        } else {
            String[] pick = fallbackPool[RNG.nextInt(fallbackPool.length)];
            String mood = pick[0];
            String line = pick[1];
            speakDirect(companionName(), line, mood);
        }
    }

    private static String cleanSubtitles(String text) {
        if (text == null) return "";
        String clean = ASTERISK_PATTERN.matcher(text).replaceAll("");
        clean = BRACKET_PATTERN.matcher(clean).replaceAll("");
        clean = PAREN_PATTERN.matcher(clean).replaceAll("");
        clean = clean.replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? text : clean;
    }
}
