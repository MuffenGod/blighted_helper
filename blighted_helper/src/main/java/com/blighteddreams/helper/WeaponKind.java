package com.blighteddreams.helper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.fml.ModList;

import java.util.Locale;

/**
 * What kind of weapon Emily holds. Drives her swing style and trail colour (client) and her combat goal (server).
 * Better Combat's category is used when it knows the item, so modded weapons get the right style; otherwise the item
 * class, then the item id.
 */
public enum WeaponKind {
    FIST(0xFFF4E8, 6, false),
    SWORD(0xFFF1C2, 10, false),
    DAGGER(0x7CFFB2, 7, false),
    AXE(0xFF9A3C, 13, false),
    HEAVY(0xC48CFF, 16, true),
    SPEAR(0x7FE8FF, 11, true),
    STAFF(0xB59CFF, 12, true),
    BOW(0xFFFFFF, 0, true),
    CROSSBOW(0xFFFFFF, 0, true),
    DUAL(0xFFD700, 9, false);

    /** Trail colour, RGB. */
    public final int color;
    /** Length of one swing animation, in ticks. */
    public final int swingTicks;
    public final boolean twoHanded;

    WeaponKind(int color, int swingTicks, boolean twoHanded) {
        this.color = color;
        this.swingTicks = swingTicks;
        this.twoHanded = twoHanded;
    }

    public boolean ranged() {
        return this == BOW || this == CROSSBOW;
    }

    public boolean isMelee() {
        return this != BOW && this != CROSSBOW && this != STAFF;
    }

    /** Classifying an item reads its id, tags and Better Combat data, and the model asks every frame: remember by item. */
    private static final java.util.Map<net.minecraft.world.item.Item, WeaponKind> KIND_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long cacheStampMs;

    public static WeaponKind of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return FIST;
        long now = System.currentTimeMillis();
        if (now - cacheStampMs > 10_000L) { // a datapack or tag reload can change the answer
            KIND_CACHE.clear();
            cacheStampMs = now;
        }
        WeaponKind cached = KIND_CACHE.get(stack.getItem());
        if (cached != null) return cached;
        WeaponKind kind = classify(stack);
        if (kind != null) {
            KIND_CACHE.put(stack.getItem(), kind);
            return kind;
        }
        // depends on this stack's own modifiers (affixes), so it is never cached
        return hasAttackDamage(stack) ? SWORD : FIST;
    }

    /** The kind from the item alone, or null when only the stack's attack damage can decide. */
    private static WeaponKind classify(ItemStack stack) {
        if (stack.getItem() instanceof CrossbowItem) return CROSSBOW;
        if (stack.getItem() instanceof BowItem) return BOW;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        if (id.contains("crossbow")) return CROSSBOW;
        if (id.contains("longbow") || id.contains("shortbow") || id.endsWith("_bow") || (id.contains("bow") && !id.contains("bowl"))) return BOW;

        WeaponKind directStaff = checkStaff(stack, id);
        if (directStaff != null) return directStaff;

        if (ModList.get() != null && ModList.get().isLoaded("bettercombat")) {
            try {
                WeaponKind k = BetterCombatKinds.of(stack, id);
                if (k != null) return k;
            } catch (Throwable ignored) {
                // a Better Combat API change must never break her
            }
        }
        if (stack.getItem() instanceof TridentItem) return SPEAR;
        if (stack.getItem() instanceof MaceItem) return HEAVY;
        WeaponKind byName = byName(id);
        if (byName != null) return byName;
        if (stack.getItem() instanceof AxeItem) return AXE;
        if (stack.getItem() instanceof SwordItem) return SWORD;
        return null;
    }

    private static WeaponKind checkStaff(ItemStack stack, String id) {
        if (id.contains("staff") || id.contains("stave") || id.contains("wand") || id.contains("scepter")
                || id.contains("sceptre") || id.contains("cane") || id.contains("crook") || id.contains("caduceus")
                || id.contains("catalyst") || (id.contains("rod") && !id.contains("fishing"))) {
            return STAFF;
        }
        String className = stack.getItem().getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (className.contains("staff") || className.contains("wand") || className.contains("scepter") || className.contains("casting")) {
            return STAFF;
        }
        for (var tag : stack.getTags().toList()) {
            String tagStr = tag.location().toString().toLowerCase(Locale.ROOT);
            if (tagStr.contains("staff") || tagStr.contains("stave") || tagStr.contains("wand") || tagStr.contains("scepter")) {
                return STAFF;
            }
        }
        return null;
    }

    public static boolean isMeleeWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        WeaponKind k = of(stack);
        return k != FIST && k.isMelee();
    }

    public static WeaponKind of(ItemStack mainHand, ItemStack offHand) {
        WeaponKind mainKind = of(mainHand);
        if (!mainKind.twoHanded && mainKind != FIST && isMeleeWeapon(offHand) && !of(offHand).twoHanded) {
            return DUAL;
        }
        return mainKind;
    }

    static WeaponKind byName(String s) {
        if (s == null) return null;
        s = s.toLowerCase(Locale.ROOT);
        if (s.contains("staff") || s.contains("stave") || s.contains("wand") || s.contains("scepter")
                || s.contains("sceptre") || s.contains("cane") || s.contains("crook") || s.contains("caduceus")
                || s.contains("catalyst") || (s.contains("rod") && !s.contains("fishing"))) return STAFF;
        if (s.contains("dagger") || s.contains("knife") || s.contains("sickle") || s.contains("claw") || s.contains("sai") || s.contains("tanto")) return DAGGER;
        if (s.contains("claymore") || s.contains("greatsword") || s.contains("great_sword") || s.contains("hammer") || s.contains("mace")
                || s.contains("greataxe") || s.contains("battleaxe") || s.contains("halberd") || s.contains("scythe") || s.contains("anchor")
                || s.contains("colossus") || s.contains("zweihander") || s.contains("two_handed")) return HEAVY;
        if (s.contains("spear") || s.contains("lance") || s.contains("pike") || s.contains("trident") || s.contains("glaive") || s.contains("javelin") || s.contains("polearm")) return SPEAR;
        if (s.contains("axe") && !s.contains("pickaxe")) return AXE;
        if (s.contains("sword") || s.contains("blade") || s.contains("katana") || s.contains("rapier") || s.contains("saber") || s.contains("cutlass") || s.contains("broadsword")) return SWORD;
        if (s.contains("fist") || s.contains("gauntlet") || s.contains("knuckle") || s.contains("cestus")) return FIST;
        return null;
    }

    /** Anything that adds attack damage in the main hand counts as a weapon she will hold. */
    public static boolean hasAttackDamage(ItemStack stack) {
        ItemAttributeModifiers mods = stack.getAttributeModifiers();
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().is(Attributes.ATTACK_DAMAGE) && e.slot().test(EquipmentSlot.MAINHAND) && e.modifier().amount() > 0) return true;
        }
        return false;
    }

    /** Only touched when Better Combat is loaded. */
    private static final class BetterCombatKinds {
        static WeaponKind of(ItemStack stack, String id) {
            net.bettercombat.api.WeaponAttributes a = net.bettercombat.logic.WeaponRegistry.getAttributes(stack);
            if (a == null) return null;
            String cat = a.category();
            WeaponKind k = byName(cat);
            if (k != null) return k;
            if (cat != null) {
                k = switch (cat.toLowerCase(Locale.ROOT)) {
                    case "battlestaff", "staff", "wand", "scepter" -> STAFF;
                    case "double_axe", "heavy_axe" -> HEAVY;
                    default -> null;
                };
                if (k != null) return k;
            }
            WeaponKind byId = byName(id);
            if (byId != null) return byId;

            String className = stack.getItem().getClass().getSimpleName().toLowerCase(Locale.ROOT);
            WeaponKind byClass = byName(className);
            if (byClass != null) return byClass;

            if (a.isTwoHanded()) return HEAVY;
            return null;
        }
    }
}
