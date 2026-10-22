package com.blighteddreams.helper.client.brain;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The part of the companion's prompt the client can see for itself: the player, where they are, what they carry, what
 * is near, and her own condition. Short on purpose (free API plans limit tokens). Must be called on the game thread.
 */
public final class HelperContext {
    private static final double HOSTILE_RADIUS = 16.0;

    private HelperContext() {}

    /** Plain text lines, or an empty string if there is no world. */
    public static String build(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        BlockPos pos = p.blockPosition();

        lines.add("Player: " + p.getName().getString() + ", health " + Math.round(p.getHealth()) + "/" + Math.round(p.getMaxHealth())
                + ", food " + p.getFoodData().getFoodLevel() + "/20, armor " + p.getArmorValue() + ", experience level " + p.experienceLevel + ".");

        String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("unknown");
        lines.add("Where: " + level.dimension().location().getPath().replace('_', ' ') + ", " + biome + " biome, x " + pos.getX() + " y " + pos.getY()
                + " z " + pos.getZ() + ", " + (level.canSeeSky(pos) ? "outdoors" : "under a roof or underground")
                + ", light " + level.getMaxLocalRawBrightness(pos) + ", " + timeOfDay(level.getDayTime()) + ", " + weather(level) + ".");

        lines.add("Holding: " + describe(p.getMainHandItem()) + (p.getOffhandItem().isEmpty() ? "" : "; offhand " + describe(p.getOffhandItem())) + ".");
        List<String> worn = new ArrayList<>();
        for (ItemStack armor : p.getArmorSlots()) {
            if (!armor.isEmpty()) {
                worn.add(armor.getHoverName().getString() + durability(armor));
            }
        }
        lines.add("Wearing: " + (worn.isEmpty() ? "no armor" : String.join(", ", worn)) + ".");

        var effects = p.getActiveEffects();
        if (!effects.isEmpty()) {
            List<String> effectNames = new ArrayList<>();
            for (var eff : effects) {
                String name = eff.getEffect().value().getDescriptionId().replace("effect.minecraft.", "").replace("effect.", "").replace('_', ' ');
                effectNames.add(name + (eff.getAmplifier() > 0 ? " " + (eff.getAmplifier() + 1) : ""));
            }
            lines.add("Player status effects: " + String.join(", ", effectNames) + ".");
        }

        Map<String, Integer> hostile = new LinkedHashMap<>();
        for (Monster m : level.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(HOSTILE_RADIUS), Entity::isAlive)) {
            hostile.merge(m.getType().getDescription().getString(), 1, Integer::sum);
        }
        if (hostile.isEmpty()) {
            lines.add("Hostile mobs within " + (int) HOSTILE_RADIUS + " blocks: none.");
        } else {
            List<String> parts = new ArrayList<>();
            hostile.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(5)
                    .forEach(e -> parts.add(e.getValue() + " " + e.getKey()));
            lines.add("Hostile mobs within " + (int) HOSTILE_RADIUS + " blocks: " + String.join(", ", parts) + ".");
        }

        List<HelperEntity> mine = level.getEntitiesOfClass(HelperEntity.class, p.getBoundingBox().inflate(96.0), e -> e.isOwner(p));
        if (mine.isEmpty()) {
            lines.add("You (the companion) are not near the player right now.");
        } else {
            HelperEntity h = mine.get(0);
            boolean lookingAtEmily = mc.crosshairPickEntity == h;
            lines.add("You (the companion): " + (h.isDowned() ? "kneeling, downed" : h.getStance().name().toLowerCase(Locale.ROOT))
                    + ", health " + Math.round(h.getHealth()) + "/" + Math.round(h.getMaxHealth()) + ", " + Math.round(h.distanceTo(p)) + " blocks from the player"
                    + (h.getMainHandItem().isEmpty() ? "" : ", holding " + h.getMainHandItem().getHoverName().getString()) + "."
                    + (lookingAtEmily ? " (The player is looking directly at you)." : ""));
        }
        lines.add(HelperMemory.summary());
        return String.join("\n", lines);
    }

    private static String describe(ItemStack s) {
        if (s.isEmpty()) {
            return "nothing";
        }
        return (s.getCount() > 1 ? s.getCount() + " " : "") + s.getHoverName().getString() + durability(s);
    }

    /** " (23% durability left)" for damaged tools and armor, else "". */
    private static String durability(ItemStack s) {
        if (!s.isDamageableItem() || s.getDamageValue() == 0) {
            return "";
        }
        int left = 100 - Math.round(100f * s.getDamageValue() / s.getMaxDamage());
        return " (" + left + "% durability left)";
    }

    private static String timeOfDay(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        if (t < 1000) return "early morning";
        if (t < 6000) return "morning";
        if (t < 9000) return "midday";
        if (t < 12000) return "afternoon";
        if (t < 13000) return "dusk";
        if (t < 23000) return "night";
        return "dawn";
    }

    private static String weather(ClientLevel level) {
        return level.isThundering() ? "thunderstorm" : level.isRaining() ? "raining" : "clear weather";
    }
}
