package com.blighteddreams.helper.agent;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;

/** Makes her use her gear like a player would: best weapon, best armour, shield, food and potions. */
public final class EmilyGear {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private EmilyGear() {
    }

    public static void autoEquip(HelperEntity helper) {
        equipBestWeapon(helper);
        equipBestArmor(helper);
        equipShield(helper);
    }

    public static double weaponScore(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        double score = tierBonus(stack);
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods != null) {
            score += mods.compute(0.0, EquipmentSlot.MAINHAND) * 4.0;
        }
        score += enchantScore(stack);
        if (stack.is(Items.MACE)) score += 10;
        if (stack.is(Items.TRIDENT)) score += 8;
        return score;
    }

    public static double armorScore(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 0;
        double score = tierBonus(stack) * 0.7 + enchantScore(stack) * 0.6;
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods != null) {
            score += mods.compute(0.0, slot) * 3.0;
        }
        if (hasCurioProtection(stack)) score += 2;
        return score;
    }

    private static double tierBonus(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (id.contains("netherite")) return 14;
        if (id.contains("diamond")) return 11;
        if (id.contains("iron")) return 8;
        if (id.contains("golden") || id.contains("gold")) return 4;
        if (id.contains("stone")) return 3;
        if (id.contains("wooden") || id.contains("wood")) return 2;
        if (id.contains("copper")) return 3;
        if (id.contains("emerald")) return 10;
        return 1;
    }

    private static double enchantScore(ItemStack stack) {
        try {
            double score = 0;
            for (var entry : stack.getEnchantments().entrySet()) {
                score += entry.getIntValue() * 0.9;
            }
            return score;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean hasCurioProtection(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return id.contains("shield") || id.contains("totem");
    }

    public static boolean equipBestWeapon(HelperEntity helper) {
        ItemStack current = helper.getMainHandItem();
        double bestScore = weaponScore(current);
        int bestSlot = -1;
        ItemStack best = ItemStack.EMPTY;
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack stack = bag.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!isWeaponCandidate(stack)) continue;
            double score = weaponScore(stack);
            if (score > bestScore + 0.5) {
                bestScore = score;
                bestSlot = i;
                best = stack;
            }
        }
        if (bestSlot < 0) return false;
        ItemStack taken = bag.extractItem(bestSlot, 1, false);
        if (taken.isEmpty()) return false;
        if (!current.isEmpty()) {
            ItemStack rest = current.copy();
            for (int i = 0; i < bag.getSlots() && !rest.isEmpty(); i++) {
                rest = bag.insertItem(i, rest, false);
            }
            if (!rest.isEmpty()) helper.spawnAtLocation(rest);
        }
        helper.setItemSlot(EquipmentSlot.MAINHAND, taken);
        helper.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.7F, 1.1F);
        return true;
    }

    private static boolean isWeaponCandidate(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.SHIELD) || stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.ARROW)
                || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (id.contains("pickaxe") || id.contains("axe") || id.contains("shovel") || id.contains("hoe")
                || id.contains("shears") || id.contains("flint_and_steel")) return false;
        return weaponScore(stack) > 1.0 || id.contains("sword") || id.contains("dagger") || id.contains("spear")
                || id.contains("staff") || id.contains("bow") || id.contains("crossbow") || id.contains("mace");
    }

    public static boolean equipBestArmor(HelperEntity helper) {
        boolean changed = false;
        var bag = helper.bag();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack current = helper.getItemBySlot(slot);
            double bestScore = armorScore(current, slot);
            int bestSlot = -1;
            for (int i = 0; i < bag.getSlots(); i++) {
                ItemStack stack = bag.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                EquipmentSlot target = helper.getEquipmentSlotForItem(stack);
                if (target != slot) continue;
                double score = armorScore(stack, slot);
                if (score > bestScore + 0.5) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
            if (bestSlot < 0) continue;
            ItemStack taken = bag.extractItem(bestSlot, 1, false);
            if (taken.isEmpty()) continue;
            helper.setItemSlot(slot, taken);
            if (!current.isEmpty()) {
                ItemStack rest = current.copy();
                for (int i = 0; i < bag.getSlots() && !rest.isEmpty(); i++) {
                    rest = bag.insertItem(i, rest, false);
                }
                if (!rest.isEmpty()) helper.spawnAtLocation(rest);
            }
            changed = true;
        }
        if (changed) helper.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.0F);
        return changed;
    }

    public static boolean equipShield(HelperEntity helper) {
        ItemStack off = helper.getOffhandItem();
        if (off.is(Items.SHIELD) || off.is(Items.TOTEM_OF_UNDYING)) return false;
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            if (!bag.getStackInSlot(i).is(Items.SHIELD)) continue;
            ItemStack shield = bag.extractItem(i, 1, false);
            if (shield.isEmpty()) return false;
            helper.setItemSlot(EquipmentSlot.OFFHAND, shield);
            if (!off.isEmpty()) {
                ItemStack rest = off.copy();
                for (int j = 0; j < bag.getSlots() && !rest.isEmpty(); j++) {
                    rest = bag.insertItem(j, rest, false);
                }
                if (!rest.isEmpty()) helper.spawnAtLocation(rest);
            }
            helper.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.7F, 1.05F);
            return true;
        }
        return false;
    }

    /** Drinks the most useful potion in her bag: healing when hurt, fire resistance in the nether, water breathing underwater. */
    public static boolean drinkPotion(HelperEntity helper, boolean inCombat) {
        var bag = helper.bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack stack = bag.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) continue;
            List<MobEffectInstance> effects = new ArrayList<>();
            for (MobEffectInstance effect : contents.getAllEffects()) {
                effects.add(effect);
            }
            if (effects.isEmpty()) continue;
            if (!worthDrinking(helper, effects, inCombat)) continue;
            ItemStack taken = bag.extractItem(i, 1, false);
            if (taken.isEmpty()) continue;
            for (MobEffectInstance effect : effects) {
                helper.addEffect(new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier(),
                        effect.isAmbient(), effect.isVisible(), effect.showIcon()));
            }
            helper.playSound(SoundEvents.GENERIC_DRINK, 0.9F, 0.95F);
            return true;
        }
        return false;
    }

    private static boolean worthDrinking(HelperEntity helper, List<MobEffectInstance> effects, boolean inCombat) {
        for (MobEffectInstance effect : effects) {
            String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).getPath();
            if ((id.contains("heal") || id.contains("regeneration") || id.contains("absorption"))
                    && helper.getHealth() < helper.getMaxHealth() * 0.7F) {
                return true;
            }
            if (id.contains("fire_resistance") && helper.level().dimension().location().getPath().contains("nether")) {
                return true;
            }
            if (id.contains("water_breathing") && helper.isUnderWater()) {
                return true;
            }
            if (inCombat && (id.contains("strength") || id.contains("speed") || id.contains("resistance")
                    || id.contains("invisibility") || id.contains("night_vision"))) {
                return true;
            }
        }
        return false;
    }

    public static boolean eat(HelperEntity helper) {
        ItemStack food = helper.consumeFood();
        if (food.isEmpty()) return false;
        var props = food.get(DataComponents.FOOD);
        float heal = props == null ? 2.0F : Math.max(2.0F, props.nutrition() + props.saturation() * 2.0F);
        helper.heal(heal);
        helper.playSound(SoundEvents.GENERIC_EAT, 0.9F, 0.95F);
        return true;
    }
}
