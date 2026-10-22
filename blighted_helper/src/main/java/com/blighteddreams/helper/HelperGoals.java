package com.blighteddreams.helper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BedBlock;

/** Vanilla-style AI goals for the companion. Nothing here waits on the network. */
final class HelperGoals {
    private HelperGoals() {}

    /** Mobs she will not pick fights with on her own (they explode or teleport away). She still defends herself. */
    private static boolean avoid(LivingEntity e) {
        return e instanceof Creeper || e instanceof EnderMan;
    }

    /** Training dummies, armor stands, or dummy mobs that shouldn't be targeted endlessly. */
    public static boolean isDummy(LivingEntity e) {
        if (e == null) return false;
        if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) return true;
        String typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().toLowerCase(java.util.Locale.ROOT);
        String className = e.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return typeId.contains("dummy") || className.contains("dummy") || typeId.contains("target") || className.contains("target");
    }

    static class Melee extends MeleeAttackGoal {
        private final HelperEntity helper;
        private long lastCheck;

        Melee(HelperEntity helper) {
            super(helper, 1.25, true);
            this.helper = helper;
        }

        private boolean shouldMelee() {
            if (helper.isDowned()) return false;
            if (helper.getCombatTactic() == HelperEntity.CombatTactic.MAGIC) return false;
            WeaponKind k = WeaponKind.of(helper.getMainHandItem(), helper.getOffhandItem());
            if (k.ranged()) return false;
            if (helper.getCombatTactic() == HelperEntity.CombatTactic.AUTO && (k == WeaponKind.STAFF || !k.isMelee())) return false;
            return true;
        }

        @Override
        public boolean canUse() {
            if (!shouldMelee()) return false;
            LivingEntity t = helper.getTarget();
            if (t == null || !t.isAlive() || helper.isTargetIgnored(t)) return false;
            if (helper.getStance() == HelperEntity.Stance.STAY && helper.distanceToSqr(t) > 25.0) return false;
            long now = helper.level().getGameTime();
            if (now - lastCheck < 5L) return false;
            lastCheck = now;
            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            if (!shouldMelee()) return false;
            LivingEntity t = helper.getTarget();
            if (t == null || !t.isAlive() || helper.isTargetIgnored(t)) return false;
            if (helper.getStance() == HelperEntity.Stance.STAY && helper.distanceToSqr(t) > 25.0) return false;
            return super.canContinueToUse();
        }

        @Override
        public void tick() {
            LivingEntity target = helper.getTarget();
            if (target != null) {
                double distSq = helper.distanceToSqr(target);
                // Aggressive gap-close: sprint when > 5 blocks away
                helper.setSprinting(distSq > 25.0);
            }
            super.tick();
        }

        @Override
        public void stop() {
            helper.setSprinting(false);
            super.stop();
        }

        @Override
        protected boolean canPerformAttack(LivingEntity entity) {
            if (!this.isTimeToAttack()) return false;
            WeaponKind k = WeaponKind.of(helper.getMainHandItem(), helper.getOffhandItem());
            double reach = helper.getBbWidth() * 2.0F + entity.getBbWidth();
            if (k == WeaponKind.SPEAR) {
                reach += 1.8;
            } else if (k == WeaponKind.HEAVY) {
                reach += 1.0;
            }
            double reachSq = reach * reach;
            return helper.distanceToSqr(entity) <= reachSq && helper.getSensing().hasLineOfSight(entity);
        }

        @Override
        protected int getAttackInterval() {
            double speed = this.helper.getAttributeValue(Attributes.ATTACK_SPEED);
            if (speed <= 0.1) speed = 4.0;
            int interval = (int) Math.round(20.0 / Math.max(0.5, speed));
            return this.adjustedTickDelay(Math.max(4, interval));
        }
    }

    static class FollowOwner extends Goal {
        private final HelperEntity helper;
        private ServerPlayer owner;

        FollowOwner(HelperEntity helper) {
            this.helper = helper;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (helper.isDowned() || helper.isRebellious() || helper.getStance() == HelperEntity.Stance.STAY) return false;
            if (helper.agent().isBusy()) return false;
            ServerPlayer o = helper.getOwner();
            if (o == null || o.level() != helper.level() || o.isSpectator()) return false;
            if (helper.distanceToSqr(o) < 36.0) return false; // closer than 6 blocks: stay put
            this.owner = o;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && !helper.agent().isBusy() && owner.isAlive() && !helper.isDowned() && !helper.isRebellious() && !helper.getNavigation().isDone()
                    && helper.getStance() != HelperEntity.Stance.STAY && helper.distanceToSqr(owner) > 9.0;
        }

        private double speed() {
            boolean far = helper.distanceToSqr(owner) > 100.0;
            helper.setSprinting(far);
            return far ? 1.5 : 1.15;
        }

        @Override
        public void start() {
            helper.getNavigation().moveTo(owner, speed());
        }

        @Override
        public void tick() {
            helper.getLookControl().setLookAt(owner, 10.0F, helper.getMaxHeadXRot());
            if (helper.tickCount % 10 == 0) helper.getNavigation().moveTo(owner, speed());
        }

        @Override
        public void stop() {
            owner = null;
            helper.setSprinting(false);
            helper.getNavigation().stop();
        }
    }

    /**
     * Bow or crossbow in hand: keep 5-14 blocks from the target, strafe while in sight, and shoot. A bow is drawn for a
     * second; a crossbow is cranked, then shouldered and aimed before the shot. The pose follows {@link HelperEntity#aim()}.
     */
    static class Ranged extends Goal {
        private final HelperEntity helper;
        private int cooldown;
        private int aimTicks;
        private int strafeTicks;
        private boolean strafeLeft;
        private boolean charged;

        Ranged(HelperEntity helper) {
            this.helper = helper;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private WeaponKind kind() {
            return WeaponKind.of(helper.getMainHandItem());
        }

        @Override
        public boolean canUse() {
            LivingEntity t = helper.getTarget();
            if (helper.isDowned() || !kind().ranged() || t == null || !t.isAlive()) return false;
            ItemStack weapon = helper.getMainHandItem();
            if (!(weapon.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem pw)) return false;
            if (kind() == WeaponKind.CROSSBOW && CrossbowItem.isCharged(weapon)) return true;
            return helper.hasAmmo(pw);
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = helper.getTarget();
            if (t == null || !t.isAlive() || helper.isTargetIgnored(t)) return false;
            return canUse();
        }

        @Override
        public void stop() {
            if (helper.isUsingItem()) helper.stopUsingItem();
            helper.setAim(HelperEntity.AIM_NONE);
            helper.getNavigation().stop();
            aimTicks = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = helper.getTarget();
            if (t == null) return;
            double d2 = helper.distanceToSqr(t);
            boolean sees = helper.getSensing().hasLineOfSight(t);
            helper.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (helper.getStance() != HelperEntity.Stance.STAY && (d2 > 196.0 || !sees)) {
                if (helper.tickCount % 10 == 0) helper.getNavigation().moveTo(t, 1.1);
            } else if (d2 < 25.0 && helper.getStance() != HelperEntity.Stance.STAY) {
                helper.getNavigation().stop();
                helper.getMoveControl().strafe(-0.6F, strafeLeft ? 0.4F : -0.4F);
            } else {
                helper.getNavigation().stop();
                if (++strafeTicks > 40) {
                    strafeTicks = 0;
                    strafeLeft = helper.getRandom().nextBoolean();
                }
                helper.getMoveControl().strafe(0.0F, strafeLeft ? 0.35F : -0.35F);
            }
            helper.setYRot(helper.yHeadRot);
            helper.yBodyRot = helper.yHeadRot;
            if (cooldown > 0) cooldown--;
            ItemStack weapon = helper.getMainHandItem();
            if (!(weapon.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem pw)) return;

            if (kind() == WeaponKind.BOW) {
                if (!helper.hasAmmo(pw)) {
                    if (helper.isUsingItem()) helper.stopUsingItem();
                    helper.setAim(HelperEntity.AIM_NONE);
                    return;
                }
                if (!helper.isUsingItem()) {
                    if (sees && cooldown <= 0) {
                        helper.startUsingItem(InteractionHand.MAIN_HAND);
                        helper.setAim(HelperEntity.AIM_BOW);
                    }
                } else if (helper.getTicksUsingItem() >= 20 && sees) {
                    helper.stopUsingItem();
                    helper.shootAt(t, false);
                    helper.setAim(HelperEntity.AIM_NONE);
                    cooldown = 12;
                }
            } else {
                charged = CrossbowItem.isCharged(weapon);
                if (!charged) {
                    if (!helper.hasAmmo(pw)) {
                        if (helper.isUsingItem()) helper.stopUsingItem();
                        helper.setAim(HelperEntity.AIM_NONE);
                        return;
                    }
                    if (!helper.isUsingItem() && cooldown <= 0) {
                        helper.startUsingItem(InteractionHand.MAIN_HAND);
                        helper.setAim(HelperEntity.AIM_CRANK);
                    } else if (helper.isUsingItem() && helper.getTicksUsingItem() >= 24) {
                        helper.stopUsingItem();
                        ItemStack ammo = helper.consumeAmmo(pw);
                        if (!ammo.isEmpty()) {
                            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(ammo));
                            helper.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 1.0F, 1.0F);
                            helper.setAim(HelperEntity.AIM_LOADED);
                            aimTicks = 0;
                        } else {
                            helper.setAim(HelperEntity.AIM_NONE);
                        }
                    }
                } else {
                    helper.setAim(HelperEntity.AIM_LOADED);
                    if (sees && ++aimTicks >= 10) {
                        helper.shootAt(t, true);
                        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
                        helper.setAim(HelperEntity.AIM_NONE);
                        cooldown = 8;
                        aimTicks = 0;
                    }
                }
            }
        }
    }

    /**
     * Dedicated spellcaster positioning and kiting goal:
     * Maintains a safe tactical casting distance (8 - 16 blocks), strafes to evade attacks,
     * backs away if rushed by hostiles, and faces the enemy so offensive spells bombard them.
     */
    static class MageCombat extends Goal {
        private final HelperEntity helper;
        private int strafeTicks;
        private boolean strafeLeft;

        MageCombat(HelperEntity helper) {
            this.helper = helper;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean shouldMage() {
            if (helper.isDowned()) return false;
            if (helper.getCombatTactic() == HelperEntity.CombatTactic.MELEE) return false;
            WeaponKind kind = WeaponKind.of(helper.getMainHandItem(), helper.getOffhandItem());
            if (kind.ranged()) return false;
            if (helper.getCombatTactic() == HelperEntity.CombatTactic.MAGIC) return true;
            if (helper.getCombatTactic() == HelperEntity.CombatTactic.AUTO && (kind == WeaponKind.STAFF || !kind.isMelee())) return true;
            return false;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = helper.getTarget();
            return shouldMage() && target != null && target.isAlive() && !helper.isTargetIgnored(target);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            helper.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = helper.getTarget();
            if (t == null) return;
            double d2 = helper.distanceToSqr(t);
            boolean sees = helper.getSensing().hasLineOfSight(t);
            helper.getLookControl().setLookAt(t, 30.0F, 30.0F);

            if (helper.getStance() != HelperEntity.Stance.STAY && (d2 > 256.0 || !sees)) {
                // Too far or blocked: advance to spellcasting range
                if (helper.tickCount % 10 == 0) helper.getNavigation().moveTo(t, 1.15);
            } else if (d2 < 36.0 && helper.getStance() != HelperEntity.Stance.STAY) {
                // Too close (under 6 blocks): back pedal and strafe away
                helper.getNavigation().stop();
                helper.getMoveControl().strafe(-0.55F, strafeLeft ? 0.35F : -0.35F);
            } else {
                // In sweet spot (6 - 16 blocks): strafe gracefully
                helper.getNavigation().stop();
                strafeTicks++;
                if (strafeTicks >= 30) {
                    strafeTicks = 0;
                    if (helper.getRandom().nextFloat() < 0.4F) strafeLeft = !strafeLeft;
                }
                helper.getMoveControl().strafe(0.0F, strafeLeft ? 0.3F : -0.3F);
            }
        }
    }

    /**
     * Spellcasting goal:
     * - Does not take MOVE or LOOK flags, allowing Melee pursuit to run concurrently.
     * - In MELEE tactic: only emergency healing when dying; offensive spells disabled.
     * - In AUTO tactic: if holding a melee weapon, suppresses spells within melee range (<= 6 blocks)
     *   so Emily executes pure weapon combos, only firing long-range openers when closing distance.
     * - In MAGIC / HYBRID tactic: weaves spells freely.
     */
    static class SpellCasting extends Goal {
        private final HelperEntity helper;
        private int cooldown = 30;

        SpellCasting(HelperEntity helper) {
            this.helper = helper;
            setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            if (helper.isDowned()) return false;
            HelperVariant v = helper.getVariant();
            if (v != HelperVariant.DEMON && v != HelperVariant.ANGEL && v != HelperVariant.GAIA && v != HelperVariant.ASTRAL && v != HelperVariant.JESTER) {
                return false;
            }
            if (!com.blighteddreams.helper.magic.HelperIronsCompat.isAvailable()) return false;

            HelperEntity.CombatTactic tactic = helper.getCombatTactic();
            WeaponKind kind = WeaponKind.of(helper.getMainHandItem(), helper.getOffhandItem());

            // Emergency healing: allowed in all modes
            if (v == HelperVariant.ANGEL) {
                ServerPlayer owner = helper.getOwner();
                if (owner != null && owner.isAlive() && owner.getHealth() < owner.getMaxHealth() * 0.75F && helper.distanceToSqr(owner) <= 256.0) {
                    return true;
                }
                if (helper.getHealth() < helper.getMaxHealth() * 0.55F) {
                    return true;
                }
            } else if (v == HelperVariant.GAIA || v == HelperVariant.ASTRAL || v == HelperVariant.JESTER) {
                if (helper.getHealth() < helper.getMaxHealth() * 0.45F) {
                    return true;
                }
            }

            // In MELEE tactic: NO offensive spells! 100% melee focus!
            if (tactic == HelperEntity.CombatTactic.MELEE) {
                return false;
            }

            LivingEntity target = helper.getTarget();
            if (target == null || !target.isAlive() || helper.isTargetIgnored(target)) return false;
            double distSq = helper.distanceToSqr(target);
            if (distSq > 576.0) return false; // within 24 blocks

            // In AUTO tactic with a melee weapon:
            // When in melee engagement range (<= 6 blocks = 36 distSq), do NOT cast offensive spells!
            // Strike with the melee weapon and trigger combo animations!
            if (tactic == HelperEntity.CombatTactic.AUTO && kind.isMelee()) {
                if (distSq <= 36.0) return false;
            }

            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (cooldown > 0) {
                cooldown--;
                return;
            }

            HelperVariant v = helper.getVariant();
            if (v == HelperVariant.DEMON) {
                LivingEntity target = helper.getTarget();
                if (target != null && target.isAlive() && helper.getSensing().hasLineOfSight(target)) {
                    int lvl = helper.getFireSpellLevel();
                    com.blighteddreams.helper.magic.HelperIronsCompat.castFire(helper, target, lvl);
                    helper.triggerCastAnim(com.blighteddreams.helper.client.HelperCasts.STYLE_CHARGE, 18, helper.getRandom().nextInt(3));
                    cooldown = 45 + helper.getRandom().nextInt(25); // ~2.2 to 3.5 seconds
                }
            } else if (v == HelperVariant.ANGEL) {
                ServerPlayer owner = helper.getOwner();
                // Priority 1: Heal owner
                if (owner != null && owner.isAlive() && owner.getHealth() < owner.getMaxHealth() * 0.75F && helper.distanceToSqr(owner) <= 256.0) {
                    int lvl = helper.getHolySpellLevel();
                    com.blighteddreams.helper.magic.HelperIronsCompat.castHeal(helper, owner, lvl);
                    helper.triggerCastAnim(com.blighteddreams.helper.client.HelperCasts.STYLE_OVERHEAD, 22, helper.getRandom().nextInt(3));
                    cooldown = 60 + helper.getRandom().nextInt(20);
                    return;
                }
                // Priority 2: Heal self
                if (helper.getHealth() < helper.getMaxHealth() * 0.55F) {
                    int lvl = helper.getHolySpellLevel();
                    com.blighteddreams.helper.magic.HelperIronsCompat.castHeal(helper, helper, lvl);
                    helper.triggerCastAnim(com.blighteddreams.helper.client.HelperCasts.STYLE_OVERHEAD, 22, helper.getRandom().nextInt(3));
                    cooldown = 60 + helper.getRandom().nextInt(20);
                    return;
                }
                // Priority 3: Smite enemy
                LivingEntity target = helper.getTarget();
                if (target != null && target.isAlive() && helper.getSensing().hasLineOfSight(target)) {
                    int lvl = helper.getHolySpellLevel();
                    com.blighteddreams.helper.magic.HelperIronsCompat.castSmite(helper, target, lvl);
                    helper.triggerCastAnim(com.blighteddreams.helper.client.HelperCasts.STYLE_POINT, 14, helper.getRandom().nextInt(3));
                    cooldown = 50 + helper.getRandom().nextInt(25);
                }
            } else if (v == HelperVariant.GAIA || v == HelperVariant.ASTRAL || v == HelperVariant.JESTER) {
                if (com.blighteddreams.helper.agent.EmilySpells.castBest(helper)) {
                    cooldown = 55 + helper.getRandom().nextInt(30);
                } else {
                    cooldown = 20;
                }
            }
        }
    }

    /** Attack whoever hurt the owner. */
    static class OwnerHurtBy extends TargetGoal {
        private final HelperEntity helper;
        private LivingEntity attacker;
        private int timestamp;

        OwnerHurtBy(HelperEntity helper) {
            super(helper, false);
            this.helper = helper;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (helper.isDowned() || helper.isRebellious()) return false;
            ServerPlayer o = helper.getOwner();
            if (o == null) return false;
            attacker = o.getLastHurtByMob();
            int t = o.getLastHurtByMobTimestamp();
            return t != timestamp && attacker != null && attacker != helper && !helper.isOwner(attacker)
                    && !helper.isTargetIgnored(attacker)
                    && canAttack(attacker, net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(attacker);
            ServerPlayer o = helper.getOwner();
            if (o != null) timestamp = o.getLastHurtByMobTimestamp();
            super.start();
        }
    }

    /** Attack whatever the owner attacked. */
    static class OwnerHurtTarget extends TargetGoal {
        private final HelperEntity helper;
        private LivingEntity victim;
        private int timestamp;

        OwnerHurtTarget(HelperEntity helper) {
            super(helper, false);
            this.helper = helper;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (helper.isDowned() || helper.isRebellious()) return false;
            ServerPlayer o = helper.getOwner();
            if (o == null) return false;
            victim = o.getLastHurtMob();
            int t = o.getLastHurtMobTimestamp();
            if (t == timestamp || victim == null || victim == helper || helper.isOwner(victim) || helper.isTargetIgnored(victim)) return false;
            if (isDummy(victim)) {
                return victim.isAlive();
            }
            return canAttack(victim, net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT);
        }

        @Override
        public boolean canContinueToUse() {
            if (victim != null && isDummy(victim)) {
                if (helper.isDowned() || helper.isRebellious()) return false;
                if (!victim.isAlive() || helper.isTargetIgnored(victim)) return false;
                ServerPlayer o = helper.getOwner();
                if (o == null || o.level() != helper.level()) return false;
                if (helper.distanceToSqr(o) > 400.0) return false;
                if (helper.getStance() == HelperEntity.Stance.STAY && helper.distanceToSqr(victim) > 25.0) return false;
                if (o.getLastHurtMob() != null && o.getLastHurtMob() != victim && o.getLastHurtMobTimestamp() != timestamp) {
                    return false;
                }
                return true;
            }
            return super.canContinueToUse();
        }

        @Override
        public void start() {
            mob.setTarget(victim);
            ServerPlayer o = helper.getOwner();
            if (o != null) timestamp = o.getLastHurtMobTimestamp();
            super.start();
        }
    }

    /**
     * Hostile mobs near her. FOLLOW and STAY: only ones that are already after her or her owner.
     * AGGRESSIVE: any hostile within 16 blocks. Creepers and Endermen are left alone.
     */
    static class Threats extends NearestAttackableTargetGoal<Mob> {
        Threats(HelperEntity helper) {
            super(helper, Mob.class, 10, true, false, e -> e instanceof Enemy && !avoid(e) && !helper.isTargetIgnored(e) && !isDummy(e) && wanted(helper, e));
        }

        private static boolean wanted(HelperEntity helper, LivingEntity e) {
            if (helper.getStance() == HelperEntity.Stance.AGGRESSIVE) return true;
            LivingEntity victim = e instanceof Mob m ? m.getTarget() : null;
            return victim != null && (victim == helper || helper.isOwner(victim));
        }

        @Override
        public boolean canUse() {
            HelperEntity h = (HelperEntity) mob;
            return !h.isDowned() && !h.isRebellious() && super.canUse();
        }

        @Override
        protected double getFollowDistance() {
            return 16.0;
        }
    }

    /** When rebellious, Emily actively attacks her owner! */
    static class RebelTarget extends TargetGoal {
        private final HelperEntity helper;

        RebelTarget(HelperEntity helper) {
            super(helper, false);
            this.helper = helper;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (helper.isDowned() || !helper.isRebellious()) return false;
            ServerPlayer o = helper.getOwner();
            return o != null && o.isAlive() && !o.isCreative() && !o.isSpectator();
        }

        @Override
        public void start() {
            mob.setTarget(helper.getOwner());
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            return helper.isRebellious() && !helper.isDowned() && super.canContinueToUse();
        }
    }

    /**
     * Fake-player autonomous routines when bored / idle:
     * - Auto-places torches in dark caves (light <= 3)
     * - Mines nearby exposed ores (coal, iron, copper, gold, diamond) with a pickaxe
     * - Sleeps in nearby bed when night or storm
     */
    static class AutonomousRoutine extends Goal {
        private final HelperEntity helper;
        private int coolDown = 0;
        private BlockPos miningTarget = null;
        private int miningTicks = 0;

        AutonomousRoutine(HelperEntity helper) {
            this.helper = helper;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (helper.isDowned() || helper.isRebellious() || helper.inCombat() || helper.getTarget() != null) return false;
            if (helper.agent().isBusy()) return false;
            // The agent planner replaces the old ad-hoc routine whenever autonomy is enabled.
            if (helper.agent().autonomy() != com.blighteddreams.helper.agent.EmilyAgent.Autonomy.OFF) return false;
            ServerPlayer o = helper.getOwner();
            if (o == null || o.level() != helper.level()) return false;
            return helper.distanceToSqr(o) <= 256.0;
        }

        @Override
        public void tick() {
            if (coolDown > 0) {
                coolDown--;
                return;
            }

            Level level = helper.level();
            if (!(level instanceof ServerLevel sl)) return;

            // 1. SLEEP IN BED AT NIGHT
            if (level.isNight() || level.isThundering()) {
                BlockPos bedPos = findNearbyBed(level, helper.blockPosition(), 8);
                if (bedPos != null) {
                    helper.getNavigation().moveTo(bedPos.getX() + 0.5, bedPos.getY(), bedPos.getZ() + 0.5, 1.0);
                    if (helper.blockPosition().closerThan(bedPos, 2.5)) {
                        helper.getLookControl().setLookAt(bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5);
                        coolDown = 40;
                        return;
                    }
                }
            }

            // 2. AUTO-TORCH IN DARK CAVES
            int light = level.getBrightness(LightLayer.BLOCK, helper.blockPosition());
            if (light <= 3) {
                if (helper.hasItem(Items.TORCH)) {
                    BlockPos torchPos = findTorchPlacement(level, helper.blockPosition());
                    if (torchPos != null) {
                        helper.extractItem(Items.TORCH, 1);
                        level.setBlock(torchPos, Blocks.TORCH.defaultBlockState(), 3);
                        helper.playSound(SoundEvents.WOOD_PLACE, 1.0F, 1.0F);
                        helper.triggerSwingAnim();
                        coolDown = 160; // 8 second cooldown between torches
                        return;
                    }
                }
            }

            // 3. AUTO-MINING NEARBY EXPOSED ORES
            if (helper.hasPickaxe()) {
                if (miningTarget == null || !isExposedOre(sl, miningTarget)) {
                    miningTarget = findNearbyOre(sl, helper.blockPosition(), 6);
                    miningTicks = 0;
                }

                if (miningTarget != null) {
                    double d2 = helper.distanceToSqr(miningTarget.getX() + 0.5, miningTarget.getY() + 0.5, miningTarget.getZ() + 0.5);
                    if (d2 > 6.0) {
                        helper.getNavigation().moveTo(miningTarget.getX() + 0.5, miningTarget.getY(), miningTarget.getZ() + 0.5, 1.0);
                    } else {
                        helper.getNavigation().stop();
                        helper.getLookControl().setLookAt(miningTarget.getX() + 0.5, miningTarget.getY() + 0.5, miningTarget.getZ() + 0.5);
                        helper.triggerSwingAnim();
                        miningTicks++;

                        if (miningTicks % 6 == 0) {
                            sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, sl.getBlockState(miningTarget)),
                                    miningTarget.getX() + 0.5, miningTarget.getY() + 0.5, miningTarget.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.05);
                            helper.playSound(SoundEvents.STONE_HIT, 0.8F, 1.0F);
                        }

                        if (miningTicks >= 35) { // ~1.8 seconds to mine
                            BlockState state = sl.getBlockState(miningTarget);
                            List<ItemStack> drops = Block.getDrops(state, sl, miningTarget, null, helper, helper.getMainHandItem());
                            sl.destroyBlock(miningTarget, false, helper);
                            for (ItemStack drop : drops) {
                                helper.addToBagOrDrop(drop);
                            }
                            helper.playSound(SoundEvents.STONE_BREAK, 1.0F, 1.0F);
                            miningTarget = null;
                            miningTicks = 0;
                            coolDown = 80; // 4 second breather
                            return;
                        }
                    }
                }
            }
        }

        private static boolean isOre(BlockState state) {
            Block b = state.getBlock();
            return b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE ||
                   b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE ||
                   b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE ||
                   b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE ||
                   b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE ||
                   b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE ||
                   b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE;
        }

        private static boolean isExposedOre(Level level, BlockPos pos) {
            if (!isOre(level.getBlockState(pos))) return false;
            for (Direction dir : Direction.values()) {
                if (level.getBlockState(pos.relative(dir)).isAir()) return true;
            }
            return false;
        }

        private static BlockPos findNearbyOre(Level level, BlockPos center, int radius) {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
                if (isExposedOre(level, pos)) {
                    return pos.immutable();
                }
            }
            return null;
        }

        private static BlockPos findNearbyBed(Level level, BlockPos center, int radius) {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
                if (level.getBlockState(pos).getBlock() instanceof BedBlock) {
                    return pos.immutable();
                }
            }
            return null;
        }

        private static BlockPos findTorchPlacement(Level level, BlockPos center) {
            if (level.getBlockState(center).isAir() && level.getBlockState(center.below()).isSolidRender(level, center.below())) {
                return center;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos wallPos = center.relative(dir);
                if (level.getBlockState(center).isAir() && level.getBlockState(wallPos).isSolidRender(level, wallPos)) {
                    return center;
                }
            }
            return null;
        }
    }
}
