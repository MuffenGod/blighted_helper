package com.blighteddreams.helper;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Universal Relics, Curios, and Marbled's First Aid bridge for Emily.
 * Translates relic and curio effects (from relics, morerelics, artifacts, and catburger)
 * directly into actual active and passive companion bonuses.
 */
public final class HelperCurioCompat {
    private static final ResourceLocation FERAL_CLAWS_MODIFIER = ResourceLocation.fromNamespaceAndPath("blighted_helper", "feral_claws_streak");
    private static final ResourceLocation CRYSTAL_HEART_MODIFIER = ResourceLocation.fromNamespaceAndPath("blighted_helper", "crystal_heart_health");
    private static final ResourceLocation SPEED_RELIC_MODIFIER = ResourceLocation.fromNamespaceAndPath("blighted_helper", "speed_relic_bonus");
    private static final ResourceLocation STEADFAST_SPIKES_MODIFIER = ResourceLocation.fromNamespaceAndPath("blighted_helper", "steadfast_spikes_kb");
    private static final ResourceLocation ROOTED_BOOTS_MODIFIER = ResourceLocation.fromNamespaceAndPath("blighted_helper", "rooted_boots_kb");
    private static final ResourceLocation DIGGING_CLAWS_MODIFIER = ResourceLocation.fromNamespaceAndPath("blighted_helper", "digging_claws_haste");
    private static final ResourceLocation LEATHER_BELT_MODIFIER = ResourceLocation.fromNamespaceAndPath("blighted_helper", "leather_belt_armor");

    // Cooldown ticks
    private int catBurgerCooldown = 0;
    private int madeInHeavenCooldown = 0;
    private int gutsOrbCooldown = 0;
    private int chorusTotemCooldown = 0;
    private int ejectButtonCooldown = 0;
    private int scarabTalismanCooldown = 0;

    // Relic status trackers
    private int rageStacks = 0;
    private int lastRageAttackTick = 0;
    private float shieldweaveCharge = 8.0F;
    private int lastDamageTakenTick = 0;

    // Feral claws streak tracking
    private UUID feralTarget = null;
    private int feralStreak = 0;
    private int lastFeralAttackTick = 0;

    /** Retrieves all currently equipped Curios items across all slots on Emily. */
    public static List<ItemStack> getAllEquippedCurios(HelperEntity helper) {
        if (!ModList.get().isLoaded("curios") || helper.level().isClientSide) return List.of();
        return CuriosApi.getCuriosInventory(helper)
                .map(inv -> {
                    List<ItemStack> list = new ArrayList<>();
                    var equipped = inv.getEquippedCurios();
                    for (int i = 0; i < equipped.getSlots(); i++) {
                        ItemStack st = equipped.getStackInSlot(i);
                        if (!st.isEmpty()) list.add(st);
                    }
                    return list;
                })
                .orElse(List.of());
    }

    public static boolean hasCurio(HelperEntity helper, String itemId) {
        if (!ModList.get().isLoaded("curios")) return false;
        return CuriosApi.getCuriosInventory(helper)
                .map(inv -> inv.isEquipped(stack -> {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    return key.toString().equals(itemId);
                }))
                .orElse(false);
    }

    public static ItemStack getEquippedCurio(HelperEntity helper, String itemId) {
        if (!ModList.get().isLoaded("curios") || helper.level().isClientSide) return ItemStack.EMPTY;
        return CuriosApi.getCuriosInventory(helper)
                .map(inv -> {
                    var equipped = inv.getEquippedCurios();
                    for (int i = 0; i < equipped.getSlots(); i++) {
                        ItemStack st = equipped.getStackInSlot(i);
                        if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(itemId)) {
                            return st;
                        }
                    }
                    return ItemStack.EMPTY;
                })
                .orElse(ItemStack.EMPTY);
    }

    /**
     * Calculates the effective relic level for an equipped relic on Emily.
     * Checks both the level stored on Emily's equipped item stack, and if Emily's owner has
     * the same relic equipped with a higher level, Emily inherits the owner's higher relic level!
     */
    public static int getEffectiveRelicLevel(HelperEntity helper, ItemStack stack) {
        if (!(stack.getItem() instanceof it.hurts.sskirillss.relics.items.relics.base.IRelicItem relic)) return 0;
        int level = relic.getRelicLevel(stack);

        ServerPlayer owner = helper.getOwner();
        if (owner != null && ModList.get().isLoaded("curios")) {
            int ownerCurioLevel = CuriosApi.getCuriosInventory(owner)
                    .map(inv -> {
                        int maxLvl = 0;
                        var equipped = inv.getEquippedCurios();
                        for (int i = 0; i < equipped.getSlots(); i++) {
                            ItemStack st = equipped.getStackInSlot(i);
                            if (!st.isEmpty() && st.getItem() == stack.getItem() && st.getItem() instanceof it.hurts.sskirillss.relics.items.relics.base.IRelicItem r) {
                                maxLvl = Math.max(maxLvl, r.getRelicLevel(st));
                            }
                        }
                        return maxLvl;
                    })
                    .orElse(0);
            level = Math.max(level, ownerCurioLevel);
        }
        return level;
    }

    /**
     * Returns a scaling factor based on the relic's level.
     * Each level provides a +15% boost to the relic's effects.
     */
    public static float getRelicScale(HelperEntity helper, ItemStack stack) {
        int level = getEffectiveRelicLevel(helper, stack);
        return 1.0F + (level * 0.15F);
    }

    /**
     * Spreads relic experience to Emily's relic and also contributes experience to the owner's
     * matching relic if nearby, so Emily leveling up helps the player and vice versa!
     */
    public static void shareRelicExperience(HelperEntity helper, ItemStack stack, int amount) {
        if (!(stack.getItem() instanceof it.hurts.sskirillss.relics.items.relics.base.IRelicItem relic)) return;
        relic.spreadRelicExperience(helper, stack, amount);

        ServerPlayer owner = helper.getOwner();
        if (owner != null && helper.distanceToSqr(owner) <= 1024.0 && ModList.get().isLoaded("curios")) {
            CuriosApi.getCuriosInventory(owner).ifPresent(inv -> {
                var equipped = inv.getEquippedCurios();
                for (int i = 0; i < equipped.getSlots(); i++) {
                    ItemStack ownerStack = equipped.getStackInSlot(i);
                    if (!ownerStack.isEmpty() && ownerStack.getItem() == stack.getItem() && ownerStack.getItem() instanceof it.hurts.sskirillss.relics.items.relics.base.IRelicItem ownerRelic) {
                        ownerRelic.spreadRelicExperience(owner, ownerStack, amount);
                    }
                }
            });
        }
    }

    public static boolean hasMatchingItem(HelperEntity helper, java.util.function.Predicate<ItemStack> filter) {
        if (!ModList.get().isLoaded("curios") || helper.level().isClientSide) return false;
        List<ItemStack> curios = getAllEquippedCurios(helper);
        for (ItemStack curio : curios) {
            var itemHandler = curio.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM);
            if (itemHandler != null) {
                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    ItemStack st = itemHandler.getStackInSlot(i);
                    if (!st.isEmpty() && filter.test(st)) return true;
                }
            }
        }
        return false;
    }

    public static ItemStack extractMatchingItem(HelperEntity helper, java.util.function.Predicate<ItemStack> filter, int amount) {
        if (!ModList.get().isLoaded("curios") || helper.level().isClientSide) return ItemStack.EMPTY;
        List<ItemStack> curios = getAllEquippedCurios(helper);
        for (ItemStack curio : curios) {
            var itemHandler = curio.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM);
            if (itemHandler != null) {
                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    ItemStack st = itemHandler.getStackInSlot(i);
                    if (!st.isEmpty() && filter.test(st)) {
                        return itemHandler.extractItem(i, amount, false);
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- SERVER TICK ----------------------------------------------------------------------------------------

    public void serverTick(HelperEntity helper) {
        if (!ModList.get().isLoaded("curios") || helper.level().isClientSide) return;
        List<ItemStack> curios = getAllEquippedCurios(helper);
        if (curios.isEmpty()) return;

        boolean hurt = helper.getHealth() < helper.getMaxHealth() && !helper.isDowned();

        // 1. Shieldweave Cape: recharge shield if unhurt for 60 ticks (3s)
        float maxShield = 8.0F;
        ItemStack shieldCape = getEquippedCurio(helper, "morerelics:shieldweave_cape");
        if (!shieldCape.isEmpty()) {
            maxShield = 8.0F * getRelicScale(helper, shieldCape);
        }
        if (helper.tickCount - lastDamageTakenTick > 60 && shieldweaveCharge < maxShield) {
            shieldweaveCharge = Math.min(maxShield, shieldweaveCharge + 0.5F * (maxShield / 8.0F));
        }

        // 2. Feral Claws: streak decay if idle > 60 ticks
        if (feralStreak > 0 && helper.tickCount - lastFeralAttackTick > 60) {
            feralStreak = 0;
            feralTarget = null;
            updateFeralModifier(helper);
        }

        // 3. Rage Glove: rage decay if idle > 80 ticks
        if (rageStacks > 0 && helper.tickCount - lastRageAttackTick > 80) {
            rageStacks = 0;
        }

        boolean hasCrystalHeart = false;
        boolean hasSpeedBonus = false;
        boolean hasSteadfastSpikes = false;
        boolean hasRootedBoots = false;
        boolean hasDiggingClaws = false;
        boolean hasKittySlippers = false;
        boolean hasUniversalAttractor = false;
        boolean hasSlowFallRelic = false;
        boolean hasLeatherBelt = false;
        double leatherBeltArmor = 2.0;

        for (ItemStack stack : curios) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String id = key.toString();

            // Passive healing / nourishment
            switch (id) {
                case "catburger:cat_burger" -> {
                    if (hurt && helper.tickCount % 100 == 0) {
                        helper.heal(2.0F);
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.HEART, helper.getX(), helper.getY(0.7), helper.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
                        }
                        helper.playSound(SoundEvents.GENERIC_EAT, 0.4F, 1.2F);
                    }
                }
                case "relics:holy_locket" -> {
                    if (hurt && helper.tickCount % 80 == 0) {
                        helper.heal(1.0F * getRelicScale(helper, stack));
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, helper.getX(), helper.getY(0.7), helper.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                }
                case "relics:infinity_ham" -> {
                    if (hurt && helper.tickCount % 60 == 0) {
                        helper.heal(1.0F * getRelicScale(helper, stack));
                    }
                }
                case "artifacts:everlasting_beef", "artifacts:eternal_steak" -> {
                    if (hurt && helper.tickCount % 60 == 0) {
                        helper.heal(1.0F);
                    }
                }
                case "relics:leafy_ring" -> {
                    if (hurt && helper.level().isDay() && helper.level().canSeeSky(helper.blockPosition()) && helper.tickCount % 60 == 0) {
                        helper.heal(1.0F * getRelicScale(helper, stack));
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, helper.getX(), helper.getY(0.7), helper.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
                        }
                    }
                }
                case "relics:fragrant_flower" -> {
                    if (hurt && helper.level().getBlockState(helper.blockPosition().below()).is(net.minecraft.tags.BlockTags.DIRT) && helper.tickCount % 60 == 0) {
                        helper.heal(1.0F * getRelicScale(helper, stack));
                    }
                }
                case "morerelics:axolotl_cream" -> {
                    if (hurt && (helper.isInWater() || helper.getHealth() <= helper.getMaxHealth() * 0.5F) && helper.tickCount % 60 == 0) {
                        helper.heal(2.0F * getRelicScale(helper, stack));
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.HEART, helper.getX(), helper.getY(0.7), helper.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
                        }
                    }
                }
                case "morerelics:moodworm" -> {
                    if (hurt && helper.tickCount % 80 == 0) {
                        helper.heal(1.0F * getRelicScale(helper, stack));
                    }
                }
                case "morerelics:epoch_apple" -> {
                    if (hurt && helper.tickCount % 40 == 0) {
                        helper.heal(2.0F * getRelicScale(helper, stack));
                    }
                }
                case "morerelics:slumbering_amulet" -> {
                    if (hurt && helper.getTarget() == null && helper.tickCount % 40 == 0) {
                        helper.heal(2.0F * getRelicScale(helper, stack));
                    }
                }
                case "relics:blazing_flask" -> {
                    if (helper.isOnFire()) helper.clearFire();
                }
                case "artifacts:antidote_vessel" -> {
                    helper.removeEffect(MobEffects.POISON);
                    helper.removeEffect(MobEffects.WITHER);
                    helper.removeEffect(MobEffects.WEAKNESS);
                    helper.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                }
                case "artifacts:withered_bracelet" -> helper.removeEffect(MobEffects.WITHER);
                case "relics:spider_necklace" -> helper.removeEffect(MobEffects.POISON);
                case "relics:amphibian_boot", "relics:aqua_walker" -> {
                    if (helper.isInWater()) {
                        helper.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 40, 0, false, false));
                        helper.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, false, false));
                    }
                }
                case "artifacts:snorkel" -> {
                    if (helper.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
                        helper.setAirSupply(helper.getMaxAirSupply());
                        helper.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, false, false));
                    }
                }
                case "artifacts:aqua_dashers", "artifacts:flippers" -> {
                    if (helper.isInWater()) {
                        helper.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, false, false));
                    }
                }
                case "artifacts:night_vision_goggles", "morerelics:bionic_eye" -> {
                    helper.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false));
                }
                case "artifacts:lucky_scarf", "relics:lucky_horseshoe" -> {
                    helper.addEffect(new MobEffectInstance(MobEffects.LUCK, 60, 0, false, false));
                }
                case "relics:magma_walker" -> {
                    if (helper.isInLava() || helper.isOnFire()) {
                        helper.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
                    }
                }
                case "artifacts:strider_shoes" -> {
                    if (helper.isInLava() || helper.level().getBlockState(helper.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
                        helper.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
                        helper.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));
                    }
                }
                case "artifacts:snowshoes" -> {
                    helper.setTicksFrozen(0);
                }
                case "artifacts:scarf_of_invisibility" -> {
                    if (!hurt && helper.getTarget() == null) {
                        helper.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
                    }
                }
                case "artifacts:umbrella", "artifacts:helium_flamingo", "relics:phantom_boot" -> {
                    hasSlowFallRelic = true;
                }
                case "artifacts:rooted_boots" -> {
                    if (helper.level().getBlockState(helper.blockPosition().below()).is(net.minecraft.tags.BlockTags.DIRT)) {
                        hasRootedBoots = true;
                        if (hurt && helper.tickCount % 60 == 0) {
                            helper.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, false, false));
                        }
                    }
                }
                case "artifacts:steadfast_spikes", "morerelics:vertebrax" -> hasSteadfastSpikes = true;
                case "artifacts:digging_claws" -> hasDiggingClaws = true;
                case "artifacts:kitty_slippers" -> hasKittySlippers = true;
                case "artifacts:universal_attractor" -> hasUniversalAttractor = true;
                case "artifacts:crystal_heart" -> hasCrystalHeart = true;
                case "relics:leather_belt" -> {
                    hasLeatherBelt = true;
                    leatherBeltArmor = 2.0 * getRelicScale(helper, stack);
                }
                case "artifacts:running_shoes", "artifacts:bunny_hoppers", "relics:roller_skates", "artifacts:cowboy_hat" -> hasSpeedBonus = true;
                case "relics:ice_skates" -> {
                    if (helper.level().getBlockState(helper.blockPosition().below()).is(net.minecraft.tags.BlockTags.ICE)) {
                        hasSpeedBonus = true;
                    }
                }
                case "morerelics:biojoint" -> {
                    hasSpeedBonus = true;
                    int amp = getEffectiveRelicLevel(helper, stack) >= 5 ? 2 : 1;
                    helper.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, amp, false, false));
                }
            }
        }

        // Gliding / slow fall when falling
        if (hasSlowFallRelic && helper.getDeltaMovement().y < -0.15 && !helper.onGround() && !helper.isInWaterRainOrBubble()) {
            helper.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20, 0, false, false));
        }

        // Apply attribute-based passive relics cleanly without recreating modifiers each tick
        AttributeInstance maxHealthAttr = helper.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            boolean has = maxHealthAttr.hasModifier(CRYSTAL_HEART_MODIFIER);
            if (hasCrystalHeart && !has) {
                maxHealthAttr.addTransientModifier(new AttributeModifier(CRYSTAL_HEART_MODIFIER, 20.0, AttributeModifier.Operation.ADD_VALUE));
            } else if (!hasCrystalHeart && has) {
                maxHealthAttr.removeModifier(CRYSTAL_HEART_MODIFIER);
            }
        }
        AttributeInstance speedAttr = helper.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            boolean has = speedAttr.hasModifier(SPEED_RELIC_MODIFIER);
            if (hasSpeedBonus && !has) {
                speedAttr.addTransientModifier(new AttributeModifier(SPEED_RELIC_MODIFIER, 0.05, AttributeModifier.Operation.ADD_VALUE));
            } else if (!hasSpeedBonus && has) {
                speedAttr.removeModifier(SPEED_RELIC_MODIFIER);
            }
        }
        AttributeInstance kbAttr = helper.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) {
            boolean hasSpikes = kbAttr.hasModifier(STEADFAST_SPIKES_MODIFIER);
            if (hasSteadfastSpikes && !hasSpikes) {
                kbAttr.addTransientModifier(new AttributeModifier(STEADFAST_SPIKES_MODIFIER, 1.0, AttributeModifier.Operation.ADD_VALUE));
            } else if (!hasSteadfastSpikes && hasSpikes) {
                kbAttr.removeModifier(STEADFAST_SPIKES_MODIFIER);
            }
            boolean hasBoots = kbAttr.hasModifier(ROOTED_BOOTS_MODIFIER);
            if (hasRootedBoots && !hasBoots) {
                kbAttr.addTransientModifier(new AttributeModifier(ROOTED_BOOTS_MODIFIER, 0.5, AttributeModifier.Operation.ADD_VALUE));
            } else if (!hasRootedBoots && hasBoots) {
                kbAttr.removeModifier(ROOTED_BOOTS_MODIFIER);
            }
        }
        AttributeInstance atkSpeedAttr = helper.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpeedAttr != null) {
            boolean has = atkSpeedAttr.hasModifier(DIGGING_CLAWS_MODIFIER);
            if (hasDiggingClaws && !has) {
                atkSpeedAttr.addTransientModifier(new AttributeModifier(DIGGING_CLAWS_MODIFIER, 0.6, AttributeModifier.Operation.ADD_VALUE));
            } else if (!hasDiggingClaws && has) {
                atkSpeedAttr.removeModifier(DIGGING_CLAWS_MODIFIER);
            }
        }
        AttributeInstance armorAttr = helper.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            boolean has = armorAttr.hasModifier(LEATHER_BELT_MODIFIER);
            if (hasLeatherBelt && !has) {
                armorAttr.addTransientModifier(new AttributeModifier(LEATHER_BELT_MODIFIER, leatherBeltArmor, AttributeModifier.Operation.ADD_VALUE));
            } else if (!hasLeatherBelt && has) {
                armorAttr.removeModifier(LEATHER_BELT_MODIFIER);
            }
        }

        // Kitty Slippers: repels creepers
        if (hasKittySlippers) {
            List<net.minecraft.world.entity.monster.Creeper> creepers = helper.level().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Creeper.class, helper.getBoundingBox().inflate(10.0));
            for (var creeper : creepers) {
                if (creeper.getTarget() == helper) {
                    creeper.setTarget(null);
                }
            }
        }

        // Universal Attractor: draws ground items towards Emily
        if (hasUniversalAttractor) {
            List<net.minecraft.world.entity.item.ItemEntity> items = helper.level().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, helper.getBoundingBox().inflate(6.0), net.minecraft.world.entity.item.ItemEntity::isAlive);
            for (net.minecraft.world.entity.item.ItemEntity item : items) {
                net.minecraft.world.phys.Vec3 diff = helper.position().add(0, 0.5, 0).subtract(item.position());
                if (diff.lengthSqr() > 0.1) {
                    item.setDeltaMovement(item.getDeltaMovement().scale(0.8).add(diff.normalize().scale(0.15)));
                }
            }
        }
    }

    // ---- INCOMING DAMAGE MODIFICATION -----------------------------------------------------------------------

    public float modifyIncomingDamage(HelperEntity helper, DamageSource source, float amount) {
        if (!ModList.get().isLoaded("curios") || helper.level().isClientSide) return amount;
        List<ItemStack> curios = getAllEquippedCurios(helper);
        if (curios.isEmpty()) return amount;

        for (ItemStack stack : curios) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String id = key.toString();

            switch (id) {
                // Made In Heaven: 25% passive damage reduction (scales with level)
                case "morerelics:made_in_heaven" -> {
                    float reduction = Math.max(0.50F, 0.75F - (getEffectiveRelicLevel(helper, stack) * 0.02F));
                    amount *= reduction;
                }
                // Bastion Ring: 30% damage reduction + absorption (scales with level)
                case "relics:bastion_ring" -> {
                    float reduction = Math.max(0.50F, 0.70F - (getEffectiveRelicLevel(helper, stack) * 0.02F));
                    amount *= reduction;
                    int amp = getEffectiveRelicLevel(helper, stack) >= 5 ? 1 : 0;
                    helper.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, amp));
                }
                // Midnight Robe: 25% damage reduction (40% in darkness, scales with level)
                case "relics:midnight_robe" -> {
                    int light = helper.level().getMaxLocalRawBrightness(helper.blockPosition());
                    float baseRed = (light < 7 ? 0.60F : 0.75F);
                    float reduction = Math.max(0.40F, baseRed - (getEffectiveRelicLevel(helper, stack) * 0.02F));
                    amount *= reduction;
                }
                // Drowned Belt: 35% damage reduction in water/rain (scales with level)
                case "relics:drowned_belt" -> {
                    if (helper.isInWaterRainOrBubble()) {
                        float reduction = Math.max(0.45F, 0.65F - (getEffectiveRelicLevel(helper, stack) * 0.02F));
                        amount *= reduction;
                    }
                }
                // Shieldweave Cape: absorbs up to shield charge
                case "morerelics:shieldweave_cape" -> {
                    if (shieldweaveCharge > 0) {
                        float absorbed = Math.min(shieldweaveCharge, amount * 0.7F);
                        shieldweaveCharge -= absorbed;
                        amount -= absorbed;
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, helper.getX(), helper.getY(0.5), helper.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
                        }
                    }
                }
                // Thermoseismic Heart: immune to fire and explosion damage
                case "morerelics:thermoseismic_heart" -> {
                    if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
                        if (helper.isOnFire()) helper.clearFire();
                        return 0.0F;
                    }
                }
                // Runic Plate: 50% reduction against magic and indirect magic damage (scales with level)
                case "morerelics:runic_plate" -> {
                    if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
                        float reduction = Math.max(0.30F, 0.50F - (getEffectiveRelicLevel(helper, stack) * 0.02F));
                        amount *= reduction;
                    }
                }
                // Opal Necklace: 40% reduction vs magic, fire, or indirect damage (scales with level)
                case "morerelics:opal_necklace" -> {
                    if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
                        float reduction = Math.max(0.40F, 0.60F - (getEffectiveRelicLevel(helper, stack) * 0.02F));
                        amount *= reduction;
                    }
                }
                // King Crimson: 20% chance to completely erase time / dodge (scales with level)
                case "morerelics:king_crimson" -> {
                    float chance = Math.min(0.35F, 0.20F + (getEffectiveRelicLevel(helper, stack) * 0.015F));
                    if (helper.getRandom().nextFloat() < chance) {
                        helper.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.5F);
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.PORTAL, helper.getX(), helper.getY(0.5), helper.getZ(), 16, 0.4, 0.4, 0.4, 0.1);
                        }
                        return 0.0F; // completely negated!
                    }
                }
                // Whispering Amulet & Ghost Skin Talisman: 15% dodge chance (scales with level)
                case "morerelics:whispering_amulet", "relics:ghost_skin_talisman" -> {
                    float chance = Math.min(0.30F, 0.15F + (getEffectiveRelicLevel(helper, stack) * 0.015F));
                    if (helper.getRandom().nextFloat() < chance) {
                        helper.playSound(SoundEvents.PHANTOM_SWOOP, 0.8F, 1.6F);
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.POOF, helper.getX(), helper.getY(0.5), helper.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                        }
                        return 0.0F;
                    }
                }
                // Fall damage immunity: Bunny Hoppers, Cloud in a Bottle, Umbrella, Helium Flamingo,
                // Lucky Horseshoe, Slime Heart, Springy Boot, Phantom Boot, Gravitum Strider
                case "artifacts:bunny_hoppers", "artifacts:cloud_in_a_bottle", "artifacts:umbrella",
                     "artifacts:helium_flamingo", "relics:lucky_horseshoe", "relics:slime_heart",
                     "relics:springy_boot", "relics:phantom_boot", "morerelics:gravitum_strider" -> {
                    if (source.is(DamageTypeTags.IS_FALL)) {
                        if ("relics:slime_heart".equals(id)) {
                            helper.playSound(SoundEvents.SLIME_BLOCK_FALL, 0.8F, 1.2F);
                        } else if ("relics:springy_boot".equals(id)) {
                            helper.playSound(SoundEvents.SLIME_SQUISH, 0.8F, 1.4F);
                        }
                        return 0.0F;
                    }
                }
                // Obsidian Skull: 40% fire damage reduction
                case "artifacts:obsidian_skull" -> {
                    if (source.is(DamageTypeTags.IS_FIRE)) amount *= 0.60F;
                }
            }
        }

        return amount;
    }

    // ---- DAMAGE REACTION / RETALIATION ----------------------------------------------------------------------

    public void onDamageTaken(HelperEntity helper, DamageSource source, float amount) {
        if (!ModList.get().isLoaded("curios") || helper.level().isClientSide) return;
        lastDamageTakenTick = helper.tickCount;
        List<ItemStack> curios = getAllEquippedCurios(helper);
        if (curios.isEmpty()) return;

        Entity attacker = source.getEntity();

        for (ItemStack stack : curios) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String id = key.toString();

            // Grant relic experience on taking damage and share with owner if nearby
            shareRelicExperience(helper, stack, 1);

            switch (id) {
                // Cross Necklace: extended invulnerability frame (applied here because super.hurt resets invulnerableTime to 20)
                case "artifacts:cross_necklace" -> {
                    helper.invulnerableTime = 35;
                    helper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1));
                }
                // Whoopee Cushion: funny fart sound on hit
                case "artifacts:whoopee_cushion" -> {
                    helper.playSound(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath("artifacts", "item.whoopee_cushion.fart")),
                            1.0F, 0.9F + helper.getRandom().nextFloat() * 0.2F);
                }
                // Jellyfish Necklace: shocks attacker when in water or rain (scales with level)
                case "relics:jellyfish_necklace" -> {
                    if (helper.isInWaterRainOrBubble() && attacker instanceof LivingEntity living && living.isAlive()) {
                        living.hurt(helper.damageSources().magic(), 4.0F * getRelicScale(helper, stack));
                        helper.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7F, 1.6F);
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, living.getX(), living.getY(0.5), living.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                        }
                    }
                }
                // Reflection Necklace: reflects damage back to attacker (scales with level)
                case "relics:reflection_necklace" -> {
                    if (attacker instanceof LivingEntity living && living.isAlive()) {
                        float reflectRatio = Math.min(0.85F, 0.50F + (getEffectiveRelicLevel(helper, stack) * 0.035F));
                        living.hurt(helper.damageSources().thorns(helper), amount * reflectRatio);
                        helper.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.6F);
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, living.getX(), living.getY(0.5), living.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                        }
                    }
                }
                // Scarab Talisman: emergency resistance & regen when HP drops low
                case "relics:scarab_talisman" -> {
                    if (helper.getHealth() <= helper.getMaxHealth() * 0.35F && helper.tickCount >= scarabTalismanCooldown) {
                        scarabTalismanCooldown = helper.tickCount + 800; // 40s cooldown
                        int amp = getEffectiveRelicLevel(helper, stack) >= 5 ? 2 : 1;
                        helper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, amp));
                        helper.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, amp));
                        helper.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.2F);
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, helper.getX(), helper.getY(0.7), helper.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
                        }
                    }
                }
                // Eject Button: emergency warp to safety when critical
                case "morerelics:eject_button" -> {
                    if (helper.getHealth() <= helper.getMaxHealth() * 0.25F && helper.tickCount >= ejectButtonCooldown) {
                        ejectButtonCooldown = helper.tickCount + 1200; // 60s cooldown
                        helper.randomTeleport(helper.getX() + (helper.getRandom().nextDouble() - 0.5) * 16.0, helper.getY(), helper.getZ() + (helper.getRandom().nextDouble() - 0.5) * 16.0, true);
                        int amp = getEffectiveRelicLevel(helper, stack) >= 5 ? 2 : 1;
                        helper.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, amp));
                        helper.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, amp));
                        helper.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.2F);
                        if (helper.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.POOF, helper.getX(), helper.getY(0.5), helper.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
                        }
                    }
                }
                // Wonder of U: karmic calamity strike (scales with level)
                case "morerelics:wonder_of_u" -> {
                    if (attacker instanceof LivingEntity living && living.isAlive()) {
                        living.hurt(helper.damageSources().magic(), 8.0F * getRelicScale(helper, stack));
                        living.igniteForSeconds(3.0F + getEffectiveRelicLevel(helper, stack) * 0.5F);
                        helper.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7F, 1.5F);
                    }
                }
                // Shock Pendant: strikes attacker with lightning
                case "artifacts:shock_pendant" -> {
                    if (attacker instanceof LivingEntity living && living.isAlive() && helper.level() instanceof ServerLevel sl) {
                        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
                        if (bolt != null) {
                            bolt.moveTo(living.position());
                            bolt.setVisualOnly(true);
                            sl.addFreshEntity(bolt);
                        }
                        living.hurt(helper.damageSources().lightningBolt(), 5.0F);
                    }
                }
                // Thorn Pendant: thorns damage
                case "artifacts:thorn_pendant" -> {
                    if (attacker instanceof LivingEntity living && living.isAlive()) {
                        living.hurt(helper.damageSources().thorns(helper), 4.0F);
                    }
                }
                // Flame Pendant: ignites attacker
                case "artifacts:flame_pendant" -> {
                    if (attacker != null) attacker.igniteForSeconds(6.0F);
                }
                // Panic Necklace: speed on hit
                case "artifacts:panic_necklace" -> helper.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 1));
                // Obsidian Skull: fire resistance on fire hit
                case "artifacts:obsidian_skull" -> {
                    if (source.is(DamageTypeTags.IS_FIRE)) {
                        helper.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0));
                    }
                }
            }
        }
    }

    // ---- ATTACKING ENEMIES ----------------------------------------------------------------------------------

    public void onAttack(HelperEntity helper, Entity target) {
        if (!ModList.get().isLoaded("curios") || !(helper.level() instanceof ServerLevel sl)) return;
        List<ItemStack> curios = getAllEquippedCurios(helper);
        if (curios.isEmpty()) return;

        boolean isUndead = target instanceof LivingEntity livingTarget && livingTarget.isInvertedHealAndHarm();
        LivingEntity living = target instanceof LivingEntity l ? l : null;

        for (ItemStack stack : curios) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String id = key.toString();

            // Grant relic experience on successful hit and share with owner if nearby
            shareRelicExperience(helper, stack, 1);
            float scale = getRelicScale(helper, stack);

            switch (id) {
                // 1. Rage Glove: accumulates rage stacks and deals proportional damage
                case "relics:rage_glove" -> {
                    if (helper.tickCount - lastRageAttackTick <= 80) {
                        rageStacks = Math.min(10, rageStacks + 1);
                    } else {
                        rageStacks = 1;
                    }
                    lastRageAttackTick = helper.tickCount;
                    if (living != null && rageStacks > 1) {
                        float bonus = (rageStacks * 1.5F) * scale;
                        living.hurt(helper.damageSources().mobAttack(helper), bonus);
                        sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
                        helper.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 1.0F + rageStacks * 0.05F);
                    }
                }
                // 2. Holy Locket: heals Emily and smites undead
                case "relics:holy_locket" -> {
                    helper.heal(2.0F * scale);
                    if (isUndead && living != null) {
                        living.hurt(helper.damageSources().magic(), 6.0F * scale);
                        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.3, 0.3, 0.1);
                        helper.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.9F, 1.4F);
                    }
                }
                // 3. Shadow Glaive: shadow strike
                case "relics:shadow_glaive" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().magic(), 5.0F * scale);
                        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
                        sl.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.5), target.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                    }
                }
                // 4. Hunter Belt: bonus damage to monsters
                case "relics:hunter_belt" -> {
                    if (target instanceof Enemy && living != null) {
                        living.hurt(helper.damageSources().mobAttack(helper), 4.0F * scale);
                        sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
                    }
                }
                // 5. Blazing Flask & Fire Gauntlet: fire strike
                case "relics:blazing_flask" -> {
                    target.igniteForSeconds(5.0F + getEffectiveRelicLevel(helper, stack) * 0.5F);
                    if (living != null) living.hurt(helper.damageSources().inFire(), 3.0F * scale);
                }
                case "artifacts:fire_gauntlet" -> {
                    target.igniteForSeconds(5.0F);
                    if (living != null) living.hurt(helper.damageSources().inFire(), 3.0F);
                }
                // 6. Spore Sack: poison spore cloud
                case "relics:spore_sack" -> {
                    if (living != null) {
                        int amp = getEffectiveRelicLevel(helper, stack) >= 5 ? 2 : 1;
                        living.addEffect(new MobEffectInstance(MobEffects.POISON, 80, amp));
                        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, target.getX(), target.getY(0.5), target.getZ(), 16, 0.4, 0.4, 0.4, 0.1);
                    }
                }
                // 7. Ice Breaker & Wool Mitten: frost strike
                case "relics:ice_breaker", "relics:wool_mitten" -> {
                    target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 100, target.getTicksFrozen() + 60));
                    if (living != null) {
                        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                        living.hurt(helper.damageSources().freeze(), 4.0F * scale);
                        sl.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                    }
                }
                // 8. Chorus Inhibitor: anti-teleport & extra ender damage
                case "relics:chorus_inhibitor" -> {
                    if (living != null && (target.getType().toShortString().contains("ender") || target instanceof net.minecraft.world.entity.monster.EnderMan)) {
                        living.hurt(helper.damageSources().magic(), 6.0F * scale);
                        sl.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY(0.5), target.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                    }
                }
                // 9. Made In Heaven: Holy Strike & Divine Fire
                case "morerelics:made_in_heaven" -> {
                    target.igniteForSeconds(4.0F);
                    if (living != null) living.hurt(helper.damageSources().magic(), 4.0F * scale);
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                    sl.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.7), target.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
                    helper.playSound(SoundEvents.BLAZE_SHOOT, 0.8F, 1.4F);
                }
                // 10. Twin Fangs: Bleed / corrosion damage
                case "morerelics:twin_fangs" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().generic(), 3.0F * scale);
                        living.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                    }
                }
                // 11. Sentient Rust: armor corrosion
                case "morerelics:sentient_rust" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().generic(), 4.0F * scale);
                        living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                    }
                }
                // 12. Tyrant Mask: Lifesteal & Execute
                case "morerelics:tyrant_mask" -> {
                    helper.heal(2.5F * scale);
                    if (living != null && living.getHealth() <= living.getMaxHealth() * 0.5F) {
                        living.hurt(helper.damageSources().magic(), 5.0F * scale);
                        sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.5), target.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
                    }
                }
                // 13. Mass Gauntlet: heavy kinetic shockwave
                case "morerelics:mass_gauntlet" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().mobAttack(helper), 4.0F * scale);
                        double dx = target.getX() - helper.getX();
                        double dz = target.getZ() - helper.getZ();
                        double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                        living.knockback(0.8, -dx / d, -dz / d);
                        sl.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
                    }
                }
                // 14. Swiftedge: flurry strikes
                case "morerelics:swiftedge" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().mobAttack(helper), 3.0F * scale);
                        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
                    }
                }
                // 15. Gravitum Glove: gravity crush
                case "morerelics:gravitum_glove" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().magic(), 4.0F * scale);
                        living.setDeltaMovement(living.getDeltaMovement().add(0, -0.4, 0));
                    }
                }
                // 16. Feral Claws: consecutive hit stacking
                case "artifacts:feral_claws" -> {
                    if (target.getUUID().equals(feralTarget) && helper.tickCount - lastFeralAttackTick <= 60) {
                        feralStreak = Math.min(10, feralStreak + 1);
                    } else {
                        feralStreak = 1;
                        feralTarget = target.getUUID();
                    }
                    lastFeralAttackTick = helper.tickCount;
                    updateFeralModifier(helper);

                    if (feralStreak >= 3) {
                        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
                    }
                }
                // 17. Power Glove: extra damage & knockback
                case "artifacts:power_glove" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().mobAttack(helper), 2.0F);
                        double dx = target.getX() - helper.getX();
                        double dz = target.getZ() - helper.getZ();
                        double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                        living.knockback(0.5, -dx / d, -dz / d);
                    }
                }
                // 18. Vampiric Glove: heals Emily
                case "artifacts:vampiric_glove" -> {
                    helper.heal(2.0F);
                    sl.sendParticles(ParticleTypes.HEART, helper.getX(), helper.getY(0.7), helper.getZ(), 1, 0.2, 0.2, 0.2, 0.05);
                }
                // 19. Pocket Piston: heavy knockback
                case "artifacts:pocket_piston" -> {
                    if (living != null) {
                        double dx = target.getX() - helper.getX();
                        double dz = target.getZ() - helper.getZ();
                        double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                        living.knockback(0.8, -dx / d, -dz / d);
                    }
                }
                // 20. Superstitious Hat: 35% chance for +5.0F magic crit
                case "artifacts:superstitious_hat" -> {
                    if (living != null && helper.getRandom().nextFloat() < 0.35F) {
                        living.hurt(helper.damageSources().magic(), 5.0F);
                        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                        helper.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.4F);
                    }
                }
                // 21. Golden Hook: +4.0F execute bonus against low HP targets
                case "artifacts:golden_hook" -> {
                    if (living != null && living.getHealth() <= living.getMaxHealth() * 0.5F) {
                        living.hurt(helper.damageSources().mobAttack(helper), 4.0F);
                        sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 8, 0.2, 0.2, 0.2, 0.15);
                        helper.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 1.2F);
                    }
                }
                // 22. Arrow Quiver: +3.0F bonus damage (scales with level)
                case "relics:arrow_quiver" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().mobAttack(helper), 3.0F * scale);
                    }
                }
                // 23. Soul Devourer: Lifesteal & Wither (scales with level)
                case "relics:soul_devourer" -> {
                    helper.heal(1.5F * scale);
                    if (living != null) {
                        living.hurt(helper.damageSources().wither(), 2.0F * scale);
                        sl.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(0.5), target.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                    }
                }
                // 24. Crown of the Legend: +3.0F physical damage (scales with level)
                case "morerelics:crown_of_the_legend" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().mobAttack(helper), 3.0F * scale);
                        sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
                    }
                }
                // 25. Weaver's Spool: Binds and slows target
                case "morerelics:weavers_spool" -> {
                    if (living != null) {
                        int amp = getEffectiveRelicLevel(helper, stack) >= 5 ? 2 : 1;
                        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, amp));
                        sl.sendParticles(ParticleTypes.WHITE_SMOKE, target.getX(), target.getY(0.5), target.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                    }
                }
                // 26. Stellar Catalyst: Cosmic strike (scales with level)
                case "relics:stellar_catalyst" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().magic(), 3.0F * scale);
                        sl.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                    }
                }
                // 27. Space Dissector: Spatial tear (scales with level)
                case "relics:space_dissector" -> {
                    if (living != null) {
                        living.hurt(helper.damageSources().magic(), 4.0F * scale);
                        sl.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                    }
                }
            }
        }
    }

    private void updateFeralModifier(HelperEntity helper) {
        AttributeInstance attr = helper.getAttribute(Attributes.ATTACK_SPEED);
        if (attr == null) return;
        attr.removeModifier(FERAL_CLAWS_MODIFIER);
        if (feralStreak > 0) {
            attr.addTransientModifier(new AttributeModifier(
                    FERAL_CLAWS_MODIFIER,
                    feralStreak * 0.25,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    // ---- FATAL DAMAGE / DOWNED PREVENTION --------------------------------------------------------------------

    public boolean checkFatalSave(HelperEntity helper) {
        if (!ModList.get().isLoaded("curios") || !(helper.level() instanceof ServerLevel sl)) return false;
        List<ItemStack> curios = getAllEquippedCurios(helper);
        if (curios.isEmpty()) return false;

        for (ItemStack stack : curios) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String id = key.toString();

            // 1. Made In Heaven: Zenith Revive
            if ("morerelics:made_in_heaven".equals(id)) {
                if (helper.tickCount >= madeInHeavenCooldown) {
                    madeInHeavenCooldown = helper.tickCount + 2400; // 120s cooldown

                    helper.setHealth(helper.getMaxHealth());
                    helper.playSound(SoundEvents.BEACON_ACTIVATE, 1.5F, 1.2F);
                    helper.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.0F);

                    sl.sendParticles(ParticleTypes.FLASH, helper.getX(), helper.getY(0.7), helper.getZ(), 1, 0, 0, 0, 0);
                    sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, helper.getX(), helper.getY(0.5), helper.getZ(), 1, 0, 0, 0, 0);
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, helper.getX(), helper.getY(0.5), helper.getZ(), 40, 1.5, 0.8, 1.5, 0.1);

                    List<LivingEntity> nearby = sl.getEntitiesOfClass(LivingEntity.class, helper.getBoundingBox().inflate(6.0),
                            e -> e instanceof Enemy && e.isAlive());
                    for (LivingEntity e : nearby) {
                        e.hurt(helper.damageSources().magic(), 15.0F);
                        e.igniteForSeconds(5.0F);
                        double dx = e.getX() - helper.getX();
                        double dz = e.getZ() - helper.getZ();
                        double dist = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
                        e.knockback(1.5, -dx / dist, -dz / dist);
                    }

                    ServerPlayer owner = helper.getOwner();
                    if (owner != null) {
                        owner.displayClientMessage(Component.translatable("message.blighted_helper.helper_zenith_revive", helper.getDisplayName()), true);
                    }
                    return true;
                }
            }

            // 2. Guts Orb: Sheer determination / 1 HP survive
            if ("morerelics:guts_orb".equals(id)) {
                if (helper.tickCount >= gutsOrbCooldown) {
                    gutsOrbCooldown = helper.tickCount + 1800; // 90s cooldown

                    helper.setHealth(1.0F);
                    helper.playSound(SoundEvents.RAVAGER_ROAR, 1.0F, 1.2F);
                    helper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4)); // invulnerable for 5s
                    helper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 1));
                    sl.sendParticles(ParticleTypes.FLAME, helper.getX(), helper.getY(0.7), helper.getZ(), 25, 0.4, 0.4, 0.4, 0.08);

                    ServerPlayer owner = helper.getOwner();
                    if (owner != null) {
                        owner.displayClientMessage(Component.literal("§c" + helper.getDisplayName().getString() + " refuses to fall through sheer determination! (Guts Orb)"), true);
                    }
                    return true;
                }
            }

            // 3. Burger Cat: Totem of Undying Save
            if ("catburger:cat_burger".equals(id)) {
                if (helper.tickCount >= catBurgerCooldown) {
                    catBurgerCooldown = helper.tickCount + 6000; // 5 min cooldown (300s)

                    helper.setHealth(helper.getMaxHealth() * 0.5F);
                    helper.playSound(SoundEvents.TOTEM_USE, 1.0F, 1.0F);

                    sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, helper.getX(), helper.getY(0.7), helper.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
                    helper.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
                    helper.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));

                    ServerPlayer owner = helper.getOwner();
                    if (owner != null) {
                        owner.displayClientMessage(Component.translatable("message.blighted_helper.helper_burger_saved", helper.getDisplayName()), true);
                    }
                    return true;
                }
            }

            // 4. Chorus Totem: Warp to safety
            if ("artifacts:chorus_totem".equals(id)) {
                if (helper.tickCount >= chorusTotemCooldown) {
                    chorusTotemCooldown = helper.tickCount + 1800; // 90s cooldown

                    helper.setHealth(helper.getMaxHealth() * 0.5F);
                    helper.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
                    helper.randomTeleport(helper.getX() + (helper.getRandom().nextDouble() - 0.5) * 12.0, helper.getY(), helper.getZ() + (helper.getRandom().nextDouble() - 0.5) * 12.0, true);

                    sl.sendParticles(ParticleTypes.PORTAL, helper.getX(), helper.getY(0.5), helper.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
                    helper.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));

                    ServerPlayer owner = helper.getOwner();
                    if (owner != null) {
                        owner.displayClientMessage(Component.literal("§d" + helper.getDisplayName().getString() + " was warped to safety by the Chorus Totem!"), true);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    // ---- MARBLED'S FIRST AID --------------------------------------------------------------------------------

    public static boolean isFirstAidItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if ("marbledsfirstaid".equals(key.getNamespace())) return true;
        return stack.is(ItemTags.create(ResourceLocation.fromNamespaceAndPath("marbledsfirstaid", "firstaiditems")));
    }

    public static int getFirstAidCooldown(ItemStack held, String path) {
        try {
            java.lang.reflect.Field field = held.getItem().getClass().getDeclaredField("itemCooldown");
            field.setAccessible(true);
            int cd = field.getInt(held.getItem());
            if (cd > 0) return cd;
        } catch (Throwable ignored) {}

        return switch (path) {
            case "bandaid" -> 60;
            case "bandaid_box" -> 80;
            case "pain_pills", "curative_pills", "panacea_pills" -> 60;
            case "bandages" -> 100;
            case "stimpack", "tonic", "adrenaline_syringe" -> 80;
            case "morphine", "morphine_syringe" -> 120;
            case "elixir" -> 140;
            case "medkit" -> 400;
            case "first_aid_kit", "medical_bag" -> 400;
            default -> 100;
        };
    }

    public static float getFirstAidHealAmount(ItemStack held, String path) {
        try {
            java.lang.reflect.Field hField = held.getItem().getClass().getDeclaredField("healAmount");
            hField.setAccessible(true);
            int heal = hField.getInt(held.getItem());
            if (heal > 0) return (float) heal;
        } catch (Throwable ignored) {}

        return switch (path) {
            case "bandaid" -> 4.0F;
            case "bandaid_box", "pain_pills", "curative_pills" -> 8.0F;
            case "bandages", "stimpack", "tonic", "adrenaline_syringe" -> 12.0F;
            case "morphine", "morphine_syringe", "panacea_pills" -> 16.0F;
            case "elixir" -> 20.0F;
            case "medkit" -> 20.0F;
            case "first_aid_kit", "medical_bag" -> 30.0F;
            default -> 12.0F;
        };
    }

    public static boolean applyFirstAid(HelperEntity helper, Player player, ItemStack held, InteractionHand hand) {
        if (!isFirstAidItem(held)) return false;
        if (player.getCooldowns().isOnCooldown(held.getItem())) return false;

        boolean wasDowned = helper.isDowned();
        boolean hurt = helper.getHealth() < helper.getMaxHealth();

        if (!wasDowned && !hurt) {
            // Already at full health - consume interaction silently without sending any message
            return false;
        }

        ResourceLocation key = BuiltInRegistries.ITEM.getKey(held.getItem());
        String path = key.getPath();

        float healAmount = getFirstAidHealAmount(held, path);
        int cooldown = getFirstAidCooldown(held, path);

        if (!helper.level().isClientSide) {
            if (wasDowned) {
                helper.standUp();
                helper.heal(healAmount);
                player.displayClientMessage(Component.translatable("message.blighted_helper.helper_revived", helper.getDisplayName()), true);
                helper.triggerCastAnim(com.blighteddreams.helper.client.HelperCasts.STYLE_OVERHEAD, 36, 0, com.blighteddreams.helper.client.SpellSchool.HOLY.id);
            } else {
                helper.heal(healAmount);
                player.displayClientMessage(Component.translatable("message.blighted_helper.helper_healed", helper.getDisplayName(), (int) healAmount), true);
                helper.triggerCastAnim(com.blighteddreams.helper.client.HelperCasts.STYLE_OVERHEAD, 26, 2, com.blighteddreams.helper.client.SpellSchool.HOLY.id);
            }

            if (path.contains("pills") || path.contains("medkit") || path.contains("kit") || path.contains("antidote") || path.contains("panacea")) {
                helper.removeEffect(MobEffects.POISON);
                helper.removeEffect(MobEffects.WITHER);
                helper.removeEffect(MobEffects.WEAKNESS);
                helper.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
            if (path.contains("morphine")) {
                helper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1));
            } else if (path.contains("adrenaline") || path.contains("stimpack")) {
                helper.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
                helper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 0));
            } else if (path.contains("elixir")) {
                helper.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 1));
            }

            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            player.getCooldowns().addCooldown(held.getItem(), cooldown);
            player.swing(hand, true);

            helper.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.9F, 1.1F);
            helper.playSound(SoundEvents.PLAYER_LEVELUP, 0.6F, 1.4F);
            if (helper.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HEART, helper.getX(), helper.getY(0.7), helper.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, helper.getX(), helper.getY(0.5), helper.getZ(), 10, 0.4, 0.4, 0.4, 0.05);
            }
        }
        return true;
    }

    // ---- SAVE / LOAD ----------------------------------------------------------------------------------------

    public void save(CompoundTag tag) {
        tag.putInt("HelperCatBurgerCd", catBurgerCooldown);
        tag.putInt("HelperHeavenCd", madeInHeavenCooldown);
        tag.putInt("HelperGutsCd", gutsOrbCooldown);
        tag.putInt("HelperChorusCd", chorusTotemCooldown);
        tag.putInt("HelperEjectCd", ejectButtonCooldown);
        tag.putInt("HelperScarabCd", scarabTalismanCooldown);
        tag.putFloat("HelperShieldCharge", shieldweaveCharge);
    }

    public void load(CompoundTag tag) {
        this.catBurgerCooldown = tag.getInt("HelperCatBurgerCd");
        this.madeInHeavenCooldown = tag.getInt("HelperHeavenCd");
        this.gutsOrbCooldown = tag.getInt("HelperGutsCd");
        this.chorusTotemCooldown = tag.getInt("HelperChorusCd");
        if (tag.contains("HelperEjectCd")) this.ejectButtonCooldown = tag.getInt("HelperEjectCd");
        if (tag.contains("HelperScarabCd")) this.scarabTalismanCooldown = tag.getInt("HelperScarabCd");
        if (tag.contains("HelperShieldCharge")) {
            this.shieldweaveCharge = tag.getFloat("HelperShieldCharge");
        }
    }
}
