package com.blighteddreams.helper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/**
 * Commands for the Companion:
 *  /helper stop | hold | ceasefire - stops attacking immediately and clears combat target
 *  /helper follow - sets stance to follow and disengages
 *  /helper stay | wait - sets stance to stay and holds position
 *  /helper aggressive - sets stance to aggressive
 *  /helper stance <follow|stay|aggressive>
 *  /helper summon - summons or recalls your companion
 *  /helper inventory | inv - opens her inventory
 *  /helper status - displays health, stance, and coordinates
 *  /helper choose | starter - opens the starter selection screen
 *  /helper help - shows an easy guide for all commands and voice/chat interaction
 *
 * Also supports /emily and /blighted helper aliases.
 */
public final class HelperCommands {
    private HelperCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var helper = Commands.literal("helper")
                .then(build())
                .then(Commands.literal("stop").executes(HelperCommands::executeStop))
                .then(Commands.literal("hold").executes(HelperCommands::executeStop))
                .then(Commands.literal("ceasefire").executes(HelperCommands::executeStop))
                .then(Commands.literal("follow").executes(HelperCommands::executeFollow))
                .then(Commands.literal("stay").executes(HelperCommands::executeStay))
                .then(Commands.literal("wait").executes(HelperCommands::executeStay))
                .then(Commands.literal("aggressive").executes(HelperCommands::executeAggressive))
                .then(buildStanceArgument())
                .then(buildTacticArgument())
                .then(buildFocusArgument())
                .then(Commands.literal("summon").executes(HelperCommands::executeSummon))
                .then(Commands.literal("unsummon").executes(HelperCommands::executeUnsummon))
                .then(Commands.literal("dismiss").executes(HelperCommands::executeUnsummon))
                .then(Commands.literal("inventory").executes(HelperCommands::executeInventory))
                .then(Commands.literal("inv").executes(HelperCommands::executeInventory))
                .then(Commands.literal("status").executes(HelperCommands::executeStatus))
                .then(Commands.literal("choose").executes(HelperCommands::executeChoose))
                .then(Commands.literal("starter").executes(HelperCommands::executeChoose))
                .then(buildNameArgument())
                .then(buildXpArgument())
                .then(buildLevelArgument())
                .then(buildAnimArgument())
                .then(Commands.literal("help").executes(HelperCommands::executeHelp));
        addAgentCommands(helper);

        var emily = Commands.literal("emily")
                .then(build())
                .then(Commands.literal("stop").executes(HelperCommands::executeStop))
                .then(Commands.literal("hold").executes(HelperCommands::executeStop))
                .then(Commands.literal("ceasefire").executes(HelperCommands::executeStop))
                .then(Commands.literal("follow").executes(HelperCommands::executeFollow))
                .then(Commands.literal("stay").executes(HelperCommands::executeStay))
                .then(Commands.literal("wait").executes(HelperCommands::executeStay))
                .then(Commands.literal("aggressive").executes(HelperCommands::executeAggressive))
                .then(buildStanceArgument())
                .then(buildTacticArgument())
                .then(buildFocusArgument())
                .then(Commands.literal("summon").executes(HelperCommands::executeSummon))
                .then(Commands.literal("unsummon").executes(HelperCommands::executeUnsummon))
                .then(Commands.literal("dismiss").executes(HelperCommands::executeUnsummon))
                .then(Commands.literal("inventory").executes(HelperCommands::executeInventory))
                .then(Commands.literal("inv").executes(HelperCommands::executeInventory))
                .then(Commands.literal("status").executes(HelperCommands::executeStatus))
                .then(Commands.literal("choose").executes(HelperCommands::executeChoose))
                .then(Commands.literal("starter").executes(HelperCommands::executeChoose))
                .then(buildNameArgument())
                .then(buildXpArgument())
                .then(buildLevelArgument())
                .then(buildAnimArgument())
                .then(Commands.literal("help").executes(HelperCommands::executeHelp));
        addAgentCommands(emily);

        var blighted = Commands.literal("blighted")
                .then(build())
                .then(Commands.literal("stop").executes(HelperCommands::executeStop))
                .then(Commands.literal("follow").executes(HelperCommands::executeFollow))
                .then(Commands.literal("stay").executes(HelperCommands::executeStay))
                .then(Commands.literal("aggressive").executes(HelperCommands::executeAggressive))
                .then(buildStanceArgument())
                .then(buildTacticArgument())
                .then(buildFocusArgument())
                .then(Commands.literal("summon").executes(HelperCommands::executeSummon))
                .then(Commands.literal("unsummon").executes(HelperCommands::executeUnsummon))
                .then(Commands.literal("dismiss").executes(HelperCommands::executeUnsummon))
                .then(Commands.literal("inventory").executes(HelperCommands::executeInventory))
                .then(Commands.literal("inv").executes(HelperCommands::executeInventory))
                .then(Commands.literal("status").executes(HelperCommands::executeStatus))
                .then(Commands.literal("choose").executes(HelperCommands::executeChoose))
                .then(Commands.literal("starter").executes(HelperCommands::executeChoose))
                .then(buildNameArgument())
                .then(buildXpArgument())
                .then(buildLevelArgument())
                .then(buildAnimArgument())
                .then(Commands.literal("help").executes(HelperCommands::executeHelp));
        addAgentCommands(blighted);

        event.getDispatcher().register(helper);
        event.getDispatcher().register(emily);
        event.getDispatcher().register(blighted);
    }

    private static void addAgentCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("mine")
                .executes(ctx -> executeAgent(ctx, "mine", "any ore", 16, ""))
                .then(Commands.argument("target", StringArgumentType.greedyString())
                        .executes(ctx -> executeAgent(ctx, "mine", StringArgumentType.getString(ctx, "target"), 16, ""))));
        root.then(Commands.literal("gather")
                .executes(ctx -> executeAgent(ctx, "gather", "mature crops", 16, ""))
                .then(Commands.argument("target", StringArgumentType.greedyString())
                        .executes(ctx -> executeAgent(ctx, "gather", StringArgumentType.getString(ctx, "target"), 16, ""))));
        root.then(Commands.literal("chop")
                .executes(ctx -> executeAgent(ctx, "chop", "logs", 32, ""))
                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 256))
                        .executes(ctx -> executeAgent(ctx, "chop", "logs",
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"), ""))));
        root.then(Commands.literal("goto")
                .then(Commands.argument("place", StringArgumentType.greedyString())
                        .executes(ctx -> executeAgent(ctx, "goto", StringArgumentType.getString(ctx, "place"), 0, ""))));
        root.then(Commands.literal("go")
                .then(Commands.argument("place", StringArgumentType.greedyString())
                        .executes(ctx -> executeAgent(ctx, "goto", StringArgumentType.getString(ctx, "place"), 0, ""))));
        root.then(Commands.literal("come").executes(ctx -> executeAgent(ctx, "come", "", 0, "")));
        root.then(Commands.literal("remember")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> executeAgent(ctx, "remember", StringArgumentType.getString(ctx, "name"), 0, ""))));
        root.then(Commands.literal("teleport")
                .then(Commands.argument("place", StringArgumentType.greedyString())
                        .executes(ctx -> executeAgent(ctx, "teleport", StringArgumentType.getString(ctx, "place"), 0, ""))));
        root.then(Commands.literal("find")
                .then(Commands.argument("structure", StringArgumentType.greedyString())
                        .executes(ctx -> executeAgent(ctx, "find", StringArgumentType.getString(ctx, "structure"), 0, ""))));
        root.then(Commands.literal("explore")
                .executes(ctx -> executeAgent(ctx, "explore", "", 96, ""))
                .then(Commands.argument("distance", com.mojang.brigadier.arguments.IntegerArgumentType.integer(32, 512))
                        .executes(ctx -> executeAgent(ctx, "explore", "", 
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "distance"), ""))));
        root.then(Commands.literal("loot").executes(ctx -> executeAgent(ctx, "loot", "", 24, "")));
        root.then(Commands.literal("deposit").executes(ctx -> executeAgent(ctx, "deposit", "", 32, "")));
        root.then(Commands.literal("portal").executes(ctx -> executeAgent(ctx, "portal", "", 0, "")));
        root.then(Commands.literal("craft")
                .then(Commands.argument("item", StringArgumentType.greedyString())
                        .executes(ctx -> executeAgent(ctx, "craft", StringArgumentType.getString(ctx, "item"), 1, ""))));
        root.then(Commands.literal("smelt")
                .then(Commands.argument("item", StringArgumentType.greedyString())
                        .executes(ctx -> executeAgent(ctx, "smelt", StringArgumentType.getString(ctx, "item"), 8, ""))));
        root.then(Commands.literal("guard")
                .executes(ctx -> executeAgent(ctx, "guard", "", 300, ""))
                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(10, 1800))
                        .executes(ctx -> executeAgent(ctx, "guard", "",
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds"), ""))));
        root.then(Commands.literal("patrol")
                .executes(ctx -> executeAgent(ctx, "patrol", "", 300, ""))
                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(10, 1800))
                        .executes(ctx -> executeAgent(ctx, "patrol", "",
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds"), ""))));
        root.then(Commands.literal("autonomy")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"off", "companion", "partner", "free"}, b))
                        .executes(ctx -> executeAgent(ctx, "autonomy", StringArgumentType.getString(ctx, "mode"), 0, ""))));
        root.then(Commands.literal("spell")
                .then(Commands.argument("spell", StringArgumentType.greedyString())
                        .executes(ctx -> executeAgent(ctx, "spell", StringArgumentType.getString(ctx, "spell"), 0, ""))));
        root.then(Commands.literal("eat").executes(ctx -> executeAgent(ctx, "eat", "", 0, "")));
        root.then(Commands.literal("equip").executes(ctx -> executeAgent(ctx, "equip", "", 0, "")));
        root.then(Commands.literal("report").executes(ctx -> executeAgent(ctx, "status", "", 0, "")));
    }

    private static int executeAgent(CommandContext<CommandSourceStack> ctx, String verb, String target, int amount, String extra)
            throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.literal("You have no companion yet. Use /helper summon."));
            return 0;
        }
        h.agent().assign(new com.blighteddreams.helper.agent.EmilyDirective(verb, target, amount, extra));
        ctx.getSource().sendSuccess(() -> Component.literal("§7[Emily] §f" + verb
                + (target == null || target.isBlank() ? "" : " " + target) + ".")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildStanceArgument() {
        return Commands.literal("stance")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"follow", "stay", "aggressive"}, b))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            HelperEntity h = HelperManager.find(p);
                            if (h == null) {
                                ctx.getSource().sendFailure(Component.literal("You have no companion yet. Use /helper summon."));
                                return 0;
                            }
                            HelperEntity.Stance s;
                            try {
                                s = HelperEntity.Stance.valueOf(StringArgumentType.getString(ctx, "mode").toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException e) {
                                ctx.getSource().sendFailure(Component.literal("Stance must be follow, stay or aggressive."));
                                return 0;
                            }
                            h.agent().abortTask(true);
                            h.setStance(s);
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "message.blighted_helper.helper_stance." + s.name().toLowerCase(Locale.ROOT), h.getDisplayName()), false);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTacticArgument() {
        return Commands.literal("tactic")
                .executes(HelperCommands::executeGetTactic)
                .then(Commands.argument("tactic", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(HelperEntity.CombatTactic.values())
                                        .map(t -> t.name().toLowerCase(Locale.ROOT)), b))
                        .executes(ctx -> executeSetTactic(ctx, StringArgumentType.getString(ctx, "tactic"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildFocusArgument() {
        return Commands.literal("focus")
                .executes(HelperCommands::executeGetTactic)
                .then(Commands.argument("tactic", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(HelperEntity.CombatTactic.values())
                                        .map(t -> t.name().toLowerCase(Locale.ROOT)), b))
                        .executes(ctx -> executeSetTactic(ctx, StringArgumentType.getString(ctx, "tactic"))));
    }

    private static int executeGetTactic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.literal("You have no companion yet. Use /helper summon."));
            return 0;
        }
        WeaponKind k = WeaponKind.of(h.getMainHandItem(), h.getOffhandItem());
        ctx.getSource().sendSuccess(() -> Component.literal("⚔ " + h.getName().getString() + "'s combat tactic is set to: §6"
                + h.getCombatTactic().name() + "§r (Equipped weapon: §b" + k.name() + "§r)"), false);
        return 1;
    }

    private static int executeSetTactic(CommandContext<CommandSourceStack> ctx, String tacticStr) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.literal("You have no companion yet. Use /helper summon."));
            return 0;
        }
        HelperEntity.CombatTactic tactic = HelperEntity.CombatTactic.fromString(tacticStr);
        h.setCombatTactic(tactic);
        String desc = switch (tactic) {
            case MELEE -> "100% Melee Focus! Will aggressively rush enemies and strike with equipped weapon combos.";
            case MAGIC -> "Spellcaster Focus! Will maintain distance, kite enemies, and cast spells.";
            case HYBRID -> "Battlemage Focus! Will weave ranged spells while advancing and slash in melee.";
            case AUTO -> "Adaptive Focus! Uses melee combos when holding melee weapons, magic when holding a staff/unarmed, or ranged when holding a bow.";
        };
        ctx.getSource().sendSuccess(() -> Component.literal("⚔ Set " + h.getName().getString() + "'s combat tactic to §6" + tactic.name() + "§r!\n§7" + desc), false);
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("helper")
                .then(Commands.literal("summon").executes(HelperCommands::executeSummon))
                .then(Commands.literal("unsummon").executes(HelperCommands::executeUnsummon))
                .then(Commands.literal("dismiss").executes(HelperCommands::executeUnsummon))
                .then(Commands.literal("stop").executes(HelperCommands::executeStop))
                .then(Commands.literal("follow").executes(HelperCommands::executeFollow))
                .then(Commands.literal("stay").executes(HelperCommands::executeStay))
                .then(Commands.literal("aggressive").executes(HelperCommands::executeAggressive))
                .then(buildStanceArgument())
                .then(buildTacticArgument())
                .then(buildFocusArgument())
                .then(Commands.literal("inventory").executes(HelperCommands::executeInventory))
                .then(Commands.literal("status").executes(HelperCommands::executeStatus))
                .then(Commands.literal("choose").executes(HelperCommands::executeChoose))
                .then(Commands.literal("starter").executes(HelperCommands::executeChoose))
                .then(buildNameArgument())
                .then(Commands.literal("help").executes(HelperCommands::executeHelp));
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.literal("You have no companion yet. Use /helper summon."));
            return 0;
        }
        h.stopAttacking();
        h.agent().abortTask(false);
        if (h.getStance() == HelperEntity.Stance.AGGRESSIVE) {
            h.setStance(HelperEntity.Stance.FOLLOW);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(h.getName().getString() + " stopped attacking and is holding fire.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int executeFollow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.literal("You have no companion yet. Use /helper summon."));
            return 0;
        }
        h.setStance(HelperEntity.Stance.FOLLOW);
        h.stopAttacking();
        h.agent().abortTask(true);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "message.blighted_helper.helper_stance.follow", h.getDisplayName()), false);
        return 1;
    }

    private static int executeStay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.literal("You have no companion yet. Use /helper summon."));
            return 0;
        }
        h.setStance(HelperEntity.Stance.STAY);
        h.stopAttacking();
        h.agent().abortTask(true);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "message.blighted_helper.helper_stance.stay", h.getDisplayName()), false);
        return 1;
    }

    private static int executeAggressive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.literal("You have no companion yet. Use /helper summon."));
            return 0;
        }
        h.setStance(HelperEntity.Stance.AGGRESSIVE);
        h.agent().abortTask(true);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "message.blighted_helper.helper_stance.aggressive", h.getDisplayName()), false);
        return 1;
    }

    private static int executeSummon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!HelperConfig.enabled()) {
            ctx.getSource().sendFailure(Component.literal("The companion is switched off in config."));
            return 0;
        }
        String key = HelperManager.summonOrRecall(p);
        HelperEntity summoned = HelperManager.find(p);
        Component summonedName = Component.literal(summoned != null ? summoned.getName().getString() : HelperConfig.name());
        ctx.getSource().sendSuccess(() -> Component.translatable(key, summonedName), false);
        return 1;
    }

    private static int executeInventory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null || h.distanceToSqr(p) > 100.0) {
            ctx.getSource().sendFailure(Component.literal("Your companion must be within 10 blocks."));
            return 0;
        }
        com.blighteddreams.helper.menu.HelperMenu.open(p, h);
        return 1;
    }

    private static int executeChoose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!HelperConfig.enabled()) {
            ctx.getSource().sendFailure(Component.literal("The companion is switched off in config."));
            return 0;
        }
        HelperManager.openStarterScreenForPlayer(p);
        return 1;
    }

    private static int executeUnsummon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!HelperConfig.enabled()) {
            ctx.getSource().sendFailure(Component.literal("The companion is switched off in config."));
            return 0;
        }
        boolean ok = HelperManager.unsummon(p);
        return ok ? 1 : 0;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("You have no companion yet."), false);
            return 1;
        }
        float hp = h.getHealth();
        float maxHp = h.getMaxHealth();
        float atk = (float) h.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        float scale = h.getScale();
        int lvl = h.getLevel();
        int xp = h.getXp();
        int nextXp = h.getXpForNextLevel();

        String statusStr = String.format(Locale.ROOT,
                "§6%s§r: Level §e%d§r (%s), ❤ §c%.0f/%.0f HP§r, ⚔ §6%.1f ATK§r, Size §b%.2fx§r, stance §a%s§r%s, tactic §6%s§r, at %s\n§7Activity: §f%s§7 | Autonomy: §f%s",
                h.getName().getString(),
                lvl,
                lvl >= HelperLeveling.MAX_LEVEL ? "MAX" : xp + "/" + nextXp + " XP",
                hp, maxHp, atk, scale,
                h.getStance().name().toLowerCase(Locale.ROOT),
                h.isDowned() ? " (§4kneeling§r)" : "",
                h.getCombatTactic().name().toLowerCase(Locale.ROOT),
                h.blockPosition().toShortString(),
                h.getTaskLabel(),
                h.agent().autonomy().name().toLowerCase(Locale.ROOT));

        ctx.getSource().sendSuccess(() -> Component.literal(statusStr), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildXpArgument() {
        return Commands.literal("xp")
                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100000))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            HelperEntity h = HelperManager.find(p);
                            if (h == null) {
                                ctx.getSource().sendFailure(Component.literal("Your companion must be summoned first."));
                                return 0;
                            }
                            int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
                            HelperLeveling.addExperience(h, amount, false);
                            ctx.getSource().sendSuccess(() -> Component.literal("✦ Granted §e" + amount + " XP§r to " + h.getName().getString() + " (Level " + h.getLevel() + ")!"), false);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildNameArgument() {
        return Commands.literal("name")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            HelperEntity h = HelperManager.find(p);
                            if (h == null) {
                                ctx.getSource().sendFailure(Component.literal("Your companion must be summoned first."));
                                return 0;
                            }
                            String name = StringArgumentType.getString(ctx, "name").trim();
                            if (name.isEmpty() || name.length() > 32) {
                                ctx.getSource().sendFailure(Component.literal("Choose a name between 1 and 32 characters."));
                                return 0;
                            }
                            h.setCustomName(Component.literal(name));
                            HelperManager.saveHelperState(p, h);
                            ctx.getSource().sendSuccess(() -> Component.literal("✦ Her name is now §e" + name
                                    + "§r. Say \"" + name + ", mine iron\" or \"" + name + ", follow me\" to give orders."), false);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildLevelArgument() {
        return Commands.literal("level")
                .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, HelperLeveling.MAX_LEVEL))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            HelperEntity h = HelperManager.find(p);
                            if (h == null) {
                                ctx.getSource().sendFailure(Component.literal("Your companion must be summoned first."));
                                return 0;
                            }
                            int targetLvl = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level");
                            int oldLvl = h.getLevel();
                            h.setLevel(targetLvl);
                            h.setXp(0);
                            HelperLeveling.onLevelUp(h, oldLvl, targetLvl, targetLvl % 5 == 0);
                            ctx.getSource().sendSuccess(() -> Component.literal("✦ Set " + h.getName().getString() + " to §eLevel " + targetLvl + "§r!"), false);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAnimArgument() {
        return Commands.literal("anim")
                .then(Commands.literal("swing")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(WeaponKind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)), b))
                                .executes(ctx -> executeAnimSwing(ctx, StringArgumentType.getString(ctx, "kind"), -1))
                                .then(Commands.argument("variant", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 10))
                                        .executes(ctx -> executeAnimSwing(ctx, StringArgumentType.getString(ctx, "kind"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "variant"))))))
                .then(Commands.literal("cast")
                        .then(Commands.argument("style", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        new String[]{"point", "charge", "overhead", "slam", "channel", "shield"}, b))
                                .executes(ctx -> executeAnimCast(ctx, StringArgumentType.getString(ctx, "style"), 20, 0, ""))
                                .then(Commands.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(6, 120))
                                        .executes(ctx -> executeAnimCast(ctx, StringArgumentType.getString(ctx, "style"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks"), 0, ""))
                                        .then(Commands.argument("variant", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 5))
                                                .executes(ctx -> executeAnimCast(ctx, StringArgumentType.getString(ctx, "style"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "variant"), ""))
                                                .then(Commands.argument("school", StringArgumentType.word())
                                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                                java.util.Arrays.stream(com.blighteddreams.helper.client.SpellSchool.values())
                                                                        .filter(sc -> sc != com.blighteddreams.helper.client.SpellSchool.NONE)
                                                                        .map(sc -> sc.name), b))
                                                        .executes(ctx -> executeAnimCast(ctx, StringArgumentType.getString(ctx, "style"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "variant"),
                                                                StringArgumentType.getString(ctx, "school"))))))))
                .then(Commands.literal("idle")
                        .executes(ctx -> executeAnimIdle(ctx, -1))
                        .then(Commands.argument("variant", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 7))
                                .executes(ctx -> executeAnimIdle(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "variant")))));
    }

    private static int executeAnimSwing(CommandContext<CommandSourceStack> ctx, String kindStr, int variant) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.literal("Your companion must be summoned first."));
            return 0;
        }
        WeaponKind kind;
        try {
            kind = WeaponKind.valueOf(kindStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            kind = WeaponKind.SWORD;
        }
        h.triggerCustomSwingAnim(kind, variant);
        final WeaponKind fk = kind;
        final int fv = variant;
        ctx.getSource().sendSuccess(() -> Component.literal("⚔ Triggered swing: " + fk.name() + " (variant " + (fv >= 0 ? fv : "auto") + ")"), false);
        return 1;
    }

    private static int executeAnimCast(CommandContext<CommandSourceStack> ctx, String styleStr, int ticks, int variant, String schoolStr) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.literal("Your companion must be summoned first."));
            return 0;
        }
        int style = switch (styleStr.toLowerCase(Locale.ROOT)) {
            case "point" -> com.blighteddreams.helper.client.HelperCasts.STYLE_POINT;
            case "charge" -> com.blighteddreams.helper.client.HelperCasts.STYLE_CHARGE;
            case "overhead" -> com.blighteddreams.helper.client.HelperCasts.STYLE_OVERHEAD;
            case "slam" -> com.blighteddreams.helper.client.HelperCasts.STYLE_SLAM;
            case "channel" -> com.blighteddreams.helper.client.HelperCasts.STYLE_CHANNEL;
            case "shield" -> com.blighteddreams.helper.client.HelperCasts.STYLE_SHIELD;
            default -> com.blighteddreams.helper.client.HelperCasts.STYLE_CHARGE;
        };
        com.blighteddreams.helper.client.SpellSchool sc = com.blighteddreams.helper.client.SpellSchool.fromString(schoolStr);
        h.triggerCastAnim(style, ticks, variant, sc.id);
        ctx.getSource().sendSuccess(() -> Component.literal("✨ Triggered cast: " + styleStr + " (" + ticks + " ticks, variant " + variant + (sc != com.blighteddreams.helper.client.SpellSchool.NONE ? ", school: " + sc.name : "") + ")"), false);
        return 1;
    }

    private static int executeAnimIdle(CommandContext<CommandSourceStack> ctx, int variant) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        HelperEntity h = HelperManager.find(p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.literal("Your companion must be summoned first."));
            return 0;
        }
        h.animIdleVariant = variant >= 0 ? variant : (int) (h.level().getGameTime() % 5);
        h.animIdleStartTick = (int) h.level().getGameTime();
        final int fv = h.animIdleVariant;
        ctx.getSource().sendSuccess(() -> Component.literal("🕊 Triggered idle flourish variant: " + fv), false);
        return 1;
    }

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("=== Companion Commands & Instructions ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/helper choose").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Open the companion sanctuary (choose, rename, swap)").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/helper name <name>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Rename her; her new name becomes her chat keyword").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/helper stop").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Stop attacking immediately & hold fire").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/helper follow").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Follow you and disengage current combat").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/helper stay").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Hold position (stops following and attacking)").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/helper aggressive").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Attack any hostile monsters in sight").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/helper tactic <melee|magic|hybrid|auto>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Set combat focus: pure melee weapon combos, spells, or hybrid").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/helper summon").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Recall your companion directly to your side").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/helper inventory").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Open her gear, curios, and backpack").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/helper status").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Check her health, stance, and position").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("In-game Interaction:").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("• Right-Click (empty hand): ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("Toggle Stay / Follow & stop attacking.").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("• Shift + Right-Click: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("Open inventory (gear, Curios, and backpack).").withStyle(ChatFormatting.GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("• Chat & Orders: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("Address her by name or look at her while chatting. You can say 'stop attacking', 'follow', 'stay', or ask questions!").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }
}
