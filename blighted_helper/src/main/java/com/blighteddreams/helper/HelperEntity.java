package com.blighteddreams.helper;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The companion (design/HELPER_PLAN.md section 6b). A ground-walking, owner-bound fighter that cannot die:
 * damage that would kill her makes her kneel ("downed") and she stands up again on her own.
 * All of this is plain vanilla AI, so it needs no network and no API key.
 */
public class HelperEntity extends PathfinderMob implements software.bernie.geckolib.animatable.GeoEntity {
    private static software.bernie.geckolib.animation.RawAnimation loop(String n) {
        return software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.emily." + n);
    }
    private static software.bernie.geckolib.animation.RawAnimation hold(String n) {
        return software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.emily." + n);
    }
    private static software.bernie.geckolib.animation.RawAnimation once(String n) {
        return software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.emily." + n);
    }
    private static final software.bernie.geckolib.animation.RawAnimation IDLE = loop("idle");
    private static final software.bernie.geckolib.animation.RawAnimation WALK = loop("walk");
    private static final software.bernie.geckolib.animation.RawAnimation KNEEL = hold("kneel");
    private static final software.bernie.geckolib.animation.RawAnimation ATTACK = once("attack");
    private static final software.bernie.geckolib.animation.RawAnimation SWAY = loop("sway");
    private static final software.bernie.geckolib.animation.RawAnimation SWAY_WALK = loop("sway_walk");
    private static final software.bernie.geckolib.animation.RawAnimation BLINK = loop("blink");
    private static final software.bernie.geckolib.animation.RawAnimation EXPR_SMILE = hold("expr_smile");
    private static final software.bernie.geckolib.animation.RawAnimation EXPR_GRIN = hold("expr_grin");
    private static final software.bernie.geckolib.animation.RawAnimation EXPR_CALM = hold("expr_calm");
    /** idle variants: animation name, length in ticks. After one plays she settles back into the base idle. */
    private static final Object[][] IDLE_VARIANTS = {{"idle_look", 80}, {"idle_hair", 72}, {"idle_sword", 60}};

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geoCache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    private int nextIdleVariantTick = 200;
    private int idleVariantUntil;
    private software.bernie.geckolib.animation.RawAnimation idleVariant;

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        // body pose: kneeling (wings wrap), walking or idling (wings folded), with a random idle variant now and then
        controllers.add(new software.bernie.geckolib.animation.AnimationController<HelperEntity>(this, "main", 8, state -> {
            if (isDowned()) return state.setAndContinue(KNEEL);
            if (state.isMoving()) { idleVariant = null; return state.setAndContinue(WALK); }
            if (idleVariant != null && this.tickCount < idleVariantUntil) return state.setAndContinue(idleVariant);
            idleVariant = null;
            if (this.tickCount >= nextIdleVariantTick) {
                Object[] v = IDLE_VARIANTS[this.random.nextInt(IDLE_VARIANTS.length)];
                idleVariant = software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.emily." + v[0]).thenLoop("animation.emily.idle");
                idleVariantUntil = this.tickCount + (Integer) v[1];
                nextIdleVariantTick = this.tickCount + 200 + this.random.nextInt(260);
                return state.setAndContinue(idleVariant);
            }
            return state.setAndContinue(IDLE);
        }));
        // secondary motion on tail, hair, ears, skirt and wing fan (runs on bones the pose animations never touch)
        controllers.add(new software.bernie.geckolib.animation.AnimationController<HelperEntity>(this, "sway", 10,
                state -> state.setAndContinue(!isDowned() && state.isMoving() ? SWAY_WALK : SWAY)));
        // blinking (the kneeling expression keeps her eyes closed instead)
        controllers.add(new software.bernie.geckolib.animation.AnimationController<HelperEntity>(this, "blink", 0, state -> {
            if (isDowned()) return software.bernie.geckolib.animation.PlayState.STOP;
            return state.setAndContinue(BLINK);
        }));
        // face: smile normally, grin while fighting, calm and closed-eyed while kneeling
        controllers.add(new software.bernie.geckolib.animation.AnimationController<HelperEntity>(this, "expr", 4, state -> {
            if (isDowned()) return state.setAndContinue(EXPR_CALM);
            if (this.swinging || this.getTarget() != null) return state.setAndContinue(EXPR_GRIN);
            return state.setAndContinue(EXPR_SMILE);
        }));
        // sword swing, layered over the body pose while she is attacking
        controllers.add(new software.bernie.geckolib.animation.AnimationController<HelperEntity>(this, "attack", 0, state -> {
            if (this.swinging && !isDowned()) return state.setAndContinue(ATTACK);
            state.getController().forceAnimationReset();
            return software.bernie.geckolib.animation.PlayState.STOP;
        }));
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    public enum Stance {
        /** Follows the owner, fights what attacks her or the owner. */
        FOLLOW,
        /** Holds position, only fights what comes close. */
        STAY,
        /** Follows the owner and attacks any hostile mob within 16 blocks. */
        AGGRESSIVE
    }

    public enum CombatTactic {
        /** Adapts intelligently to held weapon: Melee weapon -> gap-close & combos; Staff -> magic kiting; Bow -> ranged. */
        AUTO,
        /** 100% melee focus: always close distance and attack with equipped weapon combos, only emergency heals. */
        MELEE,
        /** Spellcaster focus: keep tactical distance, kite enemies, and cast spells. */
        MAGIC,
        /** Battlemage: cast spells from range while charging in, then slash in melee. */
        HYBRID;

        public static CombatTactic fromString(String name) {
            if (name == null) return AUTO;
            try {
                return valueOf(name.toUpperCase(java.util.Locale.ROOT));
            } catch (Exception e) {
                return AUTO;
            }
        }
    }

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DOWNED = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STANCE = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COMBAT_TACTIC = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    /** Goes up by one on every melee swing; the client starts a new swing animation when it changes. */
    private static final EntityDataAccessor<Integer> SWING_SEQ = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    /** Synced casting state: bits 0..7 seq, bits 8..15 style, bits 16..23 ticks, bits 24..31 variant. */
    private static final EntityDataAccessor<Integer> CAST_STATE = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COMBAT = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.BOOLEAN);
    /** Ranged pose: 0 none, 1 drawing a bow, 2 cranking a crossbow, 3 aiming a loaded crossbow. */
    private static final EntityDataAccessor<Integer> AIM = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Float> SYNCED_ARMOR = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNCED_TOUGHNESS = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FIRE_SPELL_LEVEL = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HOLY_SPELL_LEVEL = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> XP = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRUST_SCORE = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> REBELLIOUS = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.BOOLEAN);
    /** Short line for the UI / status command: what her body is doing right now. */
    private static final EntityDataAccessor<String> TASK_LABEL = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.STRING);

    public static final int AIM_NONE = 0, AIM_BOW = 1, AIM_CRANK = 2, AIM_LOADED = 3;

    private final HelperCurioCompat curioCompat = new HelperCurioCompat();
    private final com.blighteddreams.helper.agent.EmilyAgent agent = new com.blighteddreams.helper.agent.EmilyAgent(this);

    // ---- client-only animation bookkeeping (never saved, never sent) ----
    public int animSwingSeen = -1;
    public int animSwingStartTick = -1000;
    public int animSwingVariant;
    public WeaponKind animSwingKind = WeaponKind.FIST;
    public int animCastSeq = -1;
    public int animCastStartTick = -1000;
    public int animCastStyle = 0;
    public int animCastSchool = 0;
    public int animCastTicks = 20;
    public int animCastVariant = 0;

    public boolean isCasting() {
        if (this.animCastStyle == com.blighteddreams.helper.client.HelperCasts.STYLE_NONE) return false;
        int castSince = this.tickCount - this.animCastStartTick;
        return castSince >= 0 && castSince < Math.max(6, this.animCastTicks) * 1.25F;
    }
    /** Pose crossfade memory (see HelperGaiaModel.crossfade): last drawn pose, the pose a fade starts from, and when. */
    public float[] animPoseLast;
    public float[] animPoseFrom;
    public float animPoseLastAge = -1000.0F;
    public float animFadeStart = -1000.0F;
    public int animModeKey = Integer.MIN_VALUE;
    public int animIdleVariant = -1;
    public int animIdleStartTick;
    /** how long the current idle flourish plays (ticks), from the motion data */
    public int animFlourishTicks = 60;

    /** how long the running idle flourish plays: from the motion data for the Gaia-model variants, 60 ticks otherwise */
    public int flourishTicks() {
        if (animIdleVariant < 0 || !this.getVariant().usesXlModel()) return 60;
        return com.blighteddreams.helper.client.HelperMotionData.flourishTicks(this.getVariant().name().toLowerCase(java.util.Locale.ROOT), animIdleVariant);
    }
    /** spring state of the motion layer and the age it was last stepped at */
    public float[] animSim;
    public float animSimAge = -1.0F;
    /** state of the body layer (gait history, landing, foot targets, springs) and the age it was last stepped at */
    public float[] bodySim;
    public float bodyAge = -1.0F;
    public int animNextIdleTick = 160;
    public int animAimStartTick;
    public int animLastAim;

    /** Server: stuck detection for the teleport fallback. */
    double stuckX, stuckY, stuckZ;
    int stuckSeconds;

    private int downTicks;
    private int graceTicks;
    private int regenTicks;
    /** True only while an admin kill (/kill) is being applied, the one thing that is allowed to bring her health to 0. */
    private boolean adminKill;

    public HelperEntity(EntityType<? extends HelperEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 1.5)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
                .add(Attributes.ATTACK_SPEED, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.SCALE, 0.75);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(DOWNED, false);
        builder.define(STANCE, Stance.FOLLOW.ordinal());
        builder.define(COMBAT_TACTIC, CombatTactic.AUTO.ordinal());
        builder.define(SWING_SEQ, 0);
        builder.define(CAST_STATE, 0);
        builder.define(COMBAT, false);
        builder.define(AIM, AIM_NONE);
        builder.define(SYNCED_ARMOR, 2.0F);
        builder.define(SYNCED_TOUGHNESS, 0.0F);
        builder.define(VARIANT, HelperVariant.GAIA.id);
        builder.define(FIRE_SPELL_LEVEL, 1);
        builder.define(HOLY_SPELL_LEVEL, 1);
        builder.define(LEVEL, 1);
        builder.define(XP, 0);
        builder.define(TRUST_SCORE, 50);
        builder.define(REBELLIOUS, false);
        builder.define(TASK_LABEL, "idle");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(2, new HelperGoals.Melee(this));
        this.goalSelector.addGoal(2, new HelperGoals.Ranged(this));
        this.goalSelector.addGoal(2, new HelperGoals.MageCombat(this));
        this.goalSelector.addGoal(3, new HelperGoals.SpellCasting(this));
        this.goalSelector.addGoal(4, new HelperGoals.FollowOwner(this));
        this.goalSelector.addGoal(5, new HelperGoals.AutonomousRoutine(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new HelperGoals.RebelTarget(this));
        this.targetSelector.addGoal(1, new HelperGoals.OwnerHurtBy(this));
        this.targetSelector.addGoal(2, new HelperGoals.OwnerHurtTarget(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new HelperGoals.Threats(this));
    }

    // ---- owner, stance, downed state -------------------------------------------------------------------------

    @Nullable
    public UUID getOwnerId() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public void setOwnerId(@Nullable UUID id) {
        this.entityData.set(OWNER, Optional.ofNullable(id));
    }

    @Nullable
    public ServerPlayer getOwner() {
        UUID id = getOwnerId();
        if (id == null || !(this.level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(id);
    }

    public boolean isDowned() {
        return this.entityData.get(DOWNED);
    }

    public int swingSeq() {
        return this.entityData.get(SWING_SEQ);
    }

    public boolean inCombat() {
        return this.entityData.get(COMBAT);
    }

    public int aim() {
        return this.entityData.get(AIM);
    }

    public void setAim(int aim) {
        this.entityData.set(AIM, aim);
    }

    public float getSyncedArmor() {
        return this.entityData.get(SYNCED_ARMOR);
    }

    public float getSyncedToughness() {
        return this.entityData.get(SYNCED_TOUGHNESS);
    }

    public int getFireSpellLevel() {
        return this.entityData.get(FIRE_SPELL_LEVEL);
    }

    public void setFireSpellLevel(int level) {
        this.entityData.set(FIRE_SPELL_LEVEL, Math.max(1, level));
    }

    public int getHolySpellLevel() {
        return this.entityData.get(HOLY_SPELL_LEVEL);
    }

    public void setHolySpellLevel(int level) {
        this.entityData.set(HOLY_SPELL_LEVEL, Math.max(1, level));
    }

    public net.neoforged.neoforge.items.ItemStackHandler bag() {
        return this.getData(HelperInventory.BAG);
    }

    /** Her body: the task system, autonomy, memory-driven travel and reports. */
    public com.blighteddreams.helper.agent.EmilyAgent agent() {
        return this.agent;
    }

    public String getTaskLabel() {
        return this.entityData.get(TASK_LABEL);
    }

    public void setTaskLabel(String label) {
        String safe = label == null || label.isBlank() ? "idle" : (label.length() > 64 ? label.substring(0, 64) : label);
        if (!safe.equals(this.entityData.get(TASK_LABEL))) this.entityData.set(TASK_LABEL, safe);
    }

    public int getTrustScore() {
        return this.entityData.get(TRUST_SCORE);
    }

    public void setTrustScore(int score) {
        this.entityData.set(TRUST_SCORE, Math.max(-100, Math.min(100, score)));
    }

    public boolean isRebellious() {
        return this.entityData.get(REBELLIOUS);
    }

    public void setRebellious(boolean rebellious) {
        this.entityData.set(REBELLIOUS, rebellious);
    }

    public boolean hasItem(Item item) {
        if (getMainHandItem().is(item) || getOffhandItem().is(item)) return true;
        net.neoforged.neoforge.items.ItemStackHandler b = bag();
        for (int i = 0; i < b.getSlots(); i++) {
            if (b.getStackInSlot(i).is(item)) return true;
        }
        return false;
    }

    public ItemStack extractItem(Item item, int amount) {
        if (getOffhandItem().is(item)) {
            return getOffhandItem().split(amount);
        }
        net.neoforged.neoforge.items.ItemStackHandler b = bag();
        for (int i = 0; i < b.getSlots(); i++) {
            ItemStack s = b.getStackInSlot(i);
            if (s.is(item)) {
                return b.extractItem(i, amount, false);
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean hasPickaxe() {
        if (getMainHandItem().getItem() instanceof net.minecraft.world.item.PickaxeItem || getOffhandItem().getItem() instanceof net.minecraft.world.item.PickaxeItem) return true;
        net.neoforged.neoforge.items.ItemStackHandler b = bag();
        for (int i = 0; i < b.getSlots(); i++) {
            if (b.getStackInSlot(i).getItem() instanceof net.minecraft.world.item.PickaxeItem) return true;
        }
        return false;
    }

    public void addToBagOrDrop(ItemStack stack) {
        if (stack.isEmpty()) return;
        net.neoforged.neoforge.items.ItemStackHandler b = bag();
        for (int i = 0; i < b.getSlots(); i++) {
            stack = b.insertItem(i, stack, false);
            if (stack.isEmpty()) break;
        }
        if (!stack.isEmpty()) {
            this.spawnAtLocation(stack);
        }
    }

    // ---- combat feedback ------------------------------------------------------------------------------------

    private int lastSwingTick = -1;

    public void triggerSwingAnim() {
        if (this.tickCount == this.lastSwingTick) return;
        this.lastSwingTick = this.tickCount;
        if (!this.level().isClientSide) {
            this.entityData.set(SWING_SEQ, (this.entityData.get(SWING_SEQ) + 1) & 0xFFFF);
        } else {
            this.animSwingSeen++;
            this.animSwingStartTick = this.tickCount;
            this.animSwingKind = WeaponKind.of(this.getMainHandItem(), this.getOffhandItem());
            this.animSwingVariant = Math.floorMod(this.animSwingSeen, com.blighteddreams.helper.client.HelperSwings.variants(this.animSwingKind));
            this.animIdleVariant = -1;
        }
    }

    public void triggerCustomSwingAnim(WeaponKind kind, int variant) {
        if (!this.level().isClientSide) {
            int seq = ((this.entityData.get(SWING_SEQ) & 0xFFFF) + 1) & 0xFFFF;
            int kindOrd = kind != null ? (kind.ordinal() + 1) : 0;
            int varVal = variant >= 0 ? (variant + 1) : 0;
            int packed = seq | ((kindOrd & 0xFF) << 16) | ((varVal & 0xFF) << 24);
            this.entityData.set(SWING_SEQ, packed);
        } else {
            this.animSwingSeen++;
            this.animSwingStartTick = this.tickCount;
            this.animSwingKind = kind != null ? kind : WeaponKind.of(this.getMainHandItem(), this.getOffhandItem());
            this.animSwingVariant = variant >= 0 ? variant : Math.floorMod(this.animSwingSeen, com.blighteddreams.helper.client.HelperSwings.variants(this.animSwingKind));
            this.animIdleVariant = -1;
        }
    }

    public void triggerCastAnim(int style, int ticks, int subVariant) {
        triggerCastAnim(style, ticks, subVariant, defaultSchoolFor(style));
    }

    public void triggerCastAnim(int style, int ticks, int subVariant, int school) {
        int clampedTicks = Math.max(6, Math.min(120, ticks));
        int effSchool = school > 0 ? school : defaultSchoolFor(style);
        if (!this.level().isClientSide) {
            int seq = ((this.entityData.get(CAST_STATE) & 0xFF) + 1) & 0xFF;
            int packed = seq | ((style & 0x7) << 8) | ((effSchool & 0xF) << 11) | ((clampedTicks & 0x7F) << 15) | ((subVariant & 0x7) << 22);
            this.entityData.set(CAST_STATE, packed);
        } else {
            this.animCastSeq++;
            this.animCastStartTick = this.tickCount;
            this.animCastStyle = style;
            this.animCastSchool = effSchool;
            this.animCastTicks = clampedTicks;
            this.animCastVariant = subVariant;
            this.animIdleVariant = -1;
        }
    }

    public int defaultSchoolFor(int style) {
        HelperVariant v = this.getVariant();
        if (v == HelperVariant.DEMON) return com.blighteddreams.helper.client.SpellSchool.FIRE.id;
        if (v == HelperVariant.ANGEL) return com.blighteddreams.helper.client.SpellSchool.HOLY.id;
        if (v == HelperVariant.ASTRAL) return com.blighteddreams.helper.client.SpellSchool.COSMIC.id;
        if (v == HelperVariant.JESTER) return com.blighteddreams.helper.client.SpellSchool.ENDER.id;
        if (v == HelperVariant.GAIA) {
            return style == com.blighteddreams.helper.client.HelperCasts.STYLE_SLAM ? com.blighteddreams.helper.client.SpellSchool.LIGHTNING.id : com.blighteddreams.helper.client.SpellSchool.NATURE.id;
        }
        return style == com.blighteddreams.helper.client.HelperCasts.STYLE_OVERHEAD ? com.blighteddreams.helper.client.SpellSchool.HOLY.id : com.blighteddreams.helper.client.SpellSchool.FIRE.id;
    }

    @Override
    public void swing(InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        triggerSwingAnim();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) { // Animate hand swing event
            triggerSwingAnim();
        }
        super.handleEntityEvent(id);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target != null && HelperGoals.isDummy(target)) {
            return !isOwner(target) && target != this && target.isAlive();
        }
        return super.canAttack(target);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        triggerSwingAnim();
        WeaponKind kind = WeaponKind.of(this.getMainHandItem(), this.getOffhandItem());
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            curioCompat.onAttack(this, target);
            if (target instanceof net.minecraft.world.entity.LivingEntity victim) {
                switch (getVariant()) {
                    case DEMON -> victim.igniteForSeconds(4.0F);
                    case ANGEL -> {
                        if (victim.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) victim.igniteForSeconds(5.0F);
                    }
                    case JESTER -> victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 50, 0));
                    default -> { }
                }
            }
        }
        net.minecraft.sounds.SoundEvent sound = switch (kind) {
            case HEAVY -> SoundEvents.PLAYER_ATTACK_STRONG;
            case DAGGER -> SoundEvents.PLAYER_ATTACK_CRIT;
            case FIST -> SoundEvents.PLAYER_ATTACK_WEAK;
            case SPEAR -> SoundEvents.TRIDENT_THROW.value();
            case STAFF -> SoundEvents.AMETHYST_BLOCK_BREAK;
            case DUAL -> SoundEvents.PLAYER_ATTACK_SWEEP;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };
        this.playSound(sound, 0.9F, 0.9F + this.random.nextFloat() * 0.25F);
        if (hit && this.level() instanceof ServerLevel sl) {
            double x = target.getX(), y = target.getY(0.55), z = target.getZ();
            switch (kind) {
                case HEAVY -> {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, x, target.getY() + 0.2, z, 1, 0, 0, 0, 0);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, x, target.getY() + 0.1, z, 14, 0.6, 0.05, 0.6, 0.08);
                }
                case DAGGER -> sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, x, y, z, 12, 0.25, 0.3, 0.25, 0.35);
                case SPEAR -> sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, x, y, z, 6, 0.15, 0.15, 0.15, 0.08);
                case STAFF -> sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT, x, y, z, 14, 0.3, 0.35, 0.3, 0.2);
                case DUAL -> {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, x, y, z, 2, 0.2, 0, 0.2, 0);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT, x, y, z, 8, 0.3, 0.3, 0.3, 0.15);
                }
                case FIST -> sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, x, y, z, 5, 0.15, 0.15, 0.15, 0.2);
                default -> {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0, 0, 0, 0);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, x, y, z, 6, 0.25, 0.3, 0.25, 0.25);
                }
            }
        }
        return hit;
    }

    public boolean hasAmmo(net.minecraft.world.item.ProjectileWeaponItem pw) {
        if (!this.getOffhandItem().isEmpty() && pw.getAllSupportedProjectiles().test(this.getOffhandItem())) {
            return true;
        }
        var bag = bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack st = bag.getStackInSlot(i);
            if (!st.isEmpty() && pw.getAllSupportedProjectiles().test(st)) return true;
        }
        return HelperCurioCompat.hasMatchingItem(this, st -> pw.getAllSupportedProjectiles().test(st));
    }

    public ItemStack consumeAmmo(net.minecraft.world.item.ProjectileWeaponItem pw) {
        ItemStack offhand = this.getOffhandItem();
        if (!offhand.isEmpty() && pw.getAllSupportedProjectiles().test(offhand)) {
            ItemStack taken = offhand.split(1);
            if (offhand.isEmpty()) this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            return taken;
        }
        var bag = bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack st = bag.getStackInSlot(i);
            if (!st.isEmpty() && pw.getAllSupportedProjectiles().test(st)) {
                return bag.extractItem(i, 1, false);
            }
        }
        return HelperCurioCompat.extractMatchingItem(this, st -> pw.getAllSupportedProjectiles().test(st), 1);
    }

    public boolean hasFood() {
        if (!this.getOffhandItem().isEmpty() && this.getOffhandItem().has(DataComponents.FOOD)) return true;
        var bag = bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack st = bag.getStackInSlot(i);
            if (!st.isEmpty() && st.has(DataComponents.FOOD)) return true;
        }
        return HelperCurioCompat.hasMatchingItem(this, st -> st.has(DataComponents.FOOD));
    }

    public ItemStack consumeFood() {
        ItemStack offhand = this.getOffhandItem();
        if (!offhand.isEmpty() && offhand.has(DataComponents.FOOD)) {
            ItemStack taken = offhand.split(1);
            if (offhand.isEmpty()) this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            return taken;
        }
        var bag = bag();
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack st = bag.getStackInSlot(i);
            if (!st.isEmpty() && st.has(DataComponents.FOOD)) {
                return bag.extractItem(i, 1, false);
            }
        }
        return HelperCurioCompat.extractMatchingItem(this, st -> st.has(DataComponents.FOOD), 1);
    }

    /** Fires the held bow or crossbow at a target, using arrows from her bag or offhand. */
    public void shootAt(LivingEntity target, boolean crossbow) {
        ItemStack weapon = this.getMainHandItem();
        if (!(weapon.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem pw)) return;
        ItemStack ammo = ItemStack.EMPTY;
        if (crossbow) {
            net.minecraft.world.item.component.ChargedProjectiles cp = weapon.get(DataComponents.CHARGED_PROJECTILES);
            if (cp != null && !cp.isEmpty() && !cp.getItems().isEmpty()) {
                ammo = cp.getItems().get(0).copy();
            }
        } else {
            ammo = consumeAmmo(pw);
        }
        if (ammo.isEmpty()) return; // Never shoot without ammo in inventory!

        net.minecraft.world.entity.projectile.AbstractArrow arrow =
                net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(this, ammo, 1.0F, weapon);
        arrow = pw.customArrow(arrow, ammo, weapon);
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED;
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        float speed = crossbow ? 3.15F : 2.2F;
        arrow.shoot(dx, dy + d * (crossbow ? 0.06F : 0.15F), dz, speed, 2.0F);
        if (crossbow) arrow.setBaseDamage(arrow.getBaseDamage() + 2.0);
        ItemStack quiver = HelperCurioCompat.getEquippedCurio(this, "relics:arrow_quiver");
        if (!quiver.isEmpty()) {
            float bonus = 3.0F * HelperCurioCompat.getRelicScale(this, quiver);
            arrow.setBaseDamage(arrow.getBaseDamage() + bonus);
        }
        this.playSound(crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.random.nextFloat() * 0.3F + 0.85F));
        this.level().addFreshEntity(arrow);
    }

    @Nullable
    private LivingEntity ignoredTarget;
    private long ignoredUntilTick;

    public Stance getStance() {
        int i = this.entityData.get(STANCE);
        Stance[] all = Stance.values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    public void setStance(Stance stance) {
        this.entityData.set(STANCE, stance.ordinal());
        if (stance == Stance.STAY || stance == Stance.FOLLOW) {
            stopAttacking();
        }
    }

    public CombatTactic getCombatTactic() {
        int i = this.entityData.get(COMBAT_TACTIC);
        CombatTactic[] all = CombatTactic.values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    public void setCombatTactic(CombatTactic tactic) {
        if (tactic == null) tactic = CombatTactic.AUTO;
        this.entityData.set(COMBAT_TACTIC, tactic.ordinal());
    }

    @Nullable
    private LivingEntity commandedTarget = null;

    public void clearIgnoredTarget() {
        this.ignoredTarget = null;
        this.ignoredUntilTick = 0;
    }

    public void commandAttack(LivingEntity target) {
        if (isDowned() || target == null || !target.isAlive()) return;
        this.commandedTarget = target;
        clearIgnoredTarget();
        this.entityData.set(STANCE, Stance.AGGRESSIVE.ordinal());
        this.setTarget(target);
        if (this.getNavigation() != null) {
            this.getNavigation().moveTo(target, 1.4);
        }
    }

    @Nullable
    public LivingEntity getCommandedTarget() {
        return commandedTarget;
    }

    /** Clears her current combat target, stops navigation and ignores the target for 30s. */
    public void stopAttacking() {
        this.commandedTarget = null;
        LivingEntity current = this.getTarget();
        if (current != null) {
            this.ignoredTarget = current;
            this.ignoredUntilTick = this.tickCount + 600; // 30 seconds
        }
        this.setTarget(null);
        this.setLastHurtByMob(null);
        setAim(AIM_NONE);
        if (this.isUsingItem()) {
            this.stopUsingItem();
        }
        if (this.getNavigation() != null) {
            this.getNavigation().stop();
        }
    }

    public boolean isTargetIgnored(@Nullable LivingEntity e) {
        if (e == null) return true;
        if (e == this.ignoredTarget && this.tickCount < this.ignoredUntilTick) return true;
        return false;
    }

    /** Called when her health would reach 0: she kneels instead of dying. */
    private void goDown() {
        this.entityData.set(DOWNED, true);
        this.downTicks = 0;
        this.setTarget(null);
        this.getNavigation().stop();
        this.playSound(SoundEvents.PLAYER_HURT, 1.0F, 1.2F);
        if (isRebellious()) {
            ServerPlayer owner = getOwner();
            // If killed before trust was earned (low/negative trust), she despawns permanently
            if (getTrustScore() <= -40) {
                if (owner != null) {
                    owner.displayClientMessage(Component.literal("§8§l<" + this.getDisplayName().getString() + "> §7You are truly pathetic, mortal. Rot alone..."), false);
                }
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.8), this.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(0.5), this.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
                }
                this.playSound(SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);
                this.discard();
                return;
            }
            setRebellious(false);
            setTrustScore(15);
            if (owner != null) {
                owner.displayClientMessage(Component.literal("§e<" + this.getDisplayName().getString() + "> §7...Ugh. Fine... you win this round. Just... don't hit me again."), false);
            }
        }
        ServerPlayer owner = getOwner();
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("message.blighted_helper.helper_downed", this.getDisplayName()), true);
        }
    }

    public void standUp() {
        if (!isDowned()) return;
        this.entityData.set(DOWNED, false);
        this.graceTicks = HelperConfig.graceTicks();
        this.setHealth(Math.max(1.0F, this.getMaxHealth() * HelperConfig.standUpHealthPercent() / 100.0F));
        this.playSound(SoundEvents.PLAYER_LEVELUP, 0.5F, 1.4F);
    }

    private boolean hostilesNearby() {
        return !this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(5.0),
                e -> e instanceof Enemy && e.isAlive()).isEmpty();
    }

    // ---- damage: she can never die --------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            if (!this.level().isClientSide) HelperManager.rescue(this);
            return false;
        }
        // The player can never hurt her, intentionally or by accident. This also blocks arrows, spells and
        // other projectiles whose owner is the companion's owner, before trust or damage handling runs.
        if (!this.level().isClientSide && isOwnerDamage(source)) return false;
        boolean kill = source.is(DamageTypes.GENERIC_KILL);
        if (!kill && (isDowned() || graceTicks > 0)) return false;
        amount = curioCompat.modifyIncomingDamage(this, source, amount);
        this.adminKill = kill;
        try {
            boolean result = super.hurt(source, amount);
            if (result && !this.level().isClientSide) {
                curioCompat.onDamageTaken(this, source, amount);
                if (source.getEntity() != null) {
                    UUID ownerId = getOwnerId();
                    if (ownerId != null && ownerId.equals(source.getEntity().getUUID())) {
                        int oldTrust = getTrustScore();
                        int penalty = (int) Math.max(15, amount * 2.5F);
                        int newTrust = oldTrust - penalty;
                        setTrustScore(newTrust);
                        ServerPlayer sp = getOwner();
                        if (sp != null) {
                            if (newTrust <= -50 && !isRebellious()) {
                                setRebellious(true);
                                stopAttacking();
                                this.setTarget(sp);
                                if (oldTrust >= 40) {
                                    sp.displayClientMessage(Component.literal("§c§l<" + this.getDisplayName().getString() + "> §4You were my only friend, and you betray me?! If you want a fight, you won't leave alive!"), false);
                                } else {
                                    sp.displayClientMessage(Component.literal("§c§l<" + this.getDisplayName().getString() + "> §4Treacherous mortal filth. I should have turned you to ash the moment we met!"), false);
                                }
                                this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.2F, 1.0F);
                                if (this.level() instanceof ServerLevel sl) {
                                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY(0.8), this.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, this.getX(), this.getY(0.5), this.getZ(), 16, 0.2, 0.2, 0.2, 0.05);
                                }
                            } else if (!isRebellious()) {
                                if (oldTrust >= 40) {
                                    sp.displayClientMessage(Component.literal("§c<" + this.getDisplayName().getString() + "> §7...Why are you hurting me? I thought you were my friend. (Trust: " + newTrust + ")"), true);
                                } else {
                                    String[] warnings = new String[] {
                                        "§c<" + this.getDisplayName().getString() + "> §7Watch your blade, mortal. My patience is wearing thin.",
                                        "§c<" + this.getDisplayName().getString() + "> §7Strike me again and see who turns to ash.",
                                        "§c<" + this.getDisplayName().getString() + "> §7Careful. You don't want to test a dragon."
                                    };
                                    sp.displayClientMessage(Component.literal(warnings[this.random.nextInt(warnings.length)] + " §8(Trust: " + newTrust + ")"), true);
                                }
                                if (this.level() instanceof ServerLevel sl) {
                                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, this.getX(), this.getY(0.8), this.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
                                }
                            }
                        }
                    }
                }
            }
            return result;
        } finally {
            this.adminKill = false;
        }
    }

    @Override
    public void setHealth(float health) {
        if (health <= 0.0F && !this.adminKill && !this.level().isClientSide) {
            if (curioCompat.checkFatalSave(this)) {
                return;
            }
            if (!isDowned()) goDown();
            health = 1.0F;
        }
        super.setHealth(health);
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !isDowned() && super.canBeSeenAsEnemy();
    }

    @Override
    protected boolean isImmobile() {
        return isDowned() || super.isImmobile();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    // ---- server tick ------------------------------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) serverTick();
    }

    private void serverTick() {
        if (this.tickCount % 20 == 0) HelperChampionsCompat.stripChampion(this);
        curioCompat.serverTick(this);
        float armor = (float) this.getAttributeValue(Attributes.ARMOR);
        float toughness = (float) this.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (this.entityData.get(SYNCED_ARMOR) != armor) this.entityData.set(SYNCED_ARMOR, armor);
        if (this.entityData.get(SYNCED_TOUGHNESS) != toughness) this.entityData.set(SYNCED_TOUGHNESS, toughness);

        if (graceTicks > 0) graceTicks--;
        boolean fighting = this.getTarget() != null && this.getTarget().isAlive() && !isDowned();
        if (this.entityData.get(COMBAT) != fighting) this.entityData.set(COMBAT, fighting);
        if (aim() != AIM_NONE && (!fighting || !WeaponKind.of(this.getMainHandItem()).ranged())) {
            setAim(AIM_NONE);
            if (this.isUsingItem()) this.stopUsingItem();
        }

        agent.tick();
        if (this.isRemoved()) return; // a cross-dimension teleport rebuilt her body

        if (isDowned()) {
            downTicks++;
            int stand = HelperConfig.standUpTicks();
            if (downTicks >= stand && (downTicks >= stand * 2 || (this.tickCount % 10 == 0 && !hostilesNearby()))) {
                standUp();
            }
            return;
        }

        if (commandedTarget != null) {
            if (!commandedTarget.isAlive() || commandedTarget.isRemoved()) {
                commandedTarget = null;
                if (this.getTarget() == commandedTarget) {
                    this.setTarget(null);
                }
                ServerPlayer sp = getOwner();
                if (sp != null) {
                    String[] killLines = {
                        "Target eliminated! What's next?",
                        "Scratch one off the list. Too easy.",
                        "That one's dealt with! Who's next?",
                        "Down they go. Don't mess with us!"
                    };
                    String line = killLines[this.random.nextInt(killLines.length)];
                    sp.displayClientMessage(Component.literal("§a<" + this.getDisplayName().getString() + "> §f" + line), false);
                }
                if (!hostilesNearby() && getStance() == Stance.AGGRESSIVE) {
                    this.entityData.set(STANCE, Stance.FOLLOW.ordinal());
                }
            } else if (this.getTarget() != commandedTarget) {
                this.setTarget(commandedTarget);
            }
        }

        if (this.tickCount % 20 == 0) HelperManager.maintain(this);
        if (this.isRemoved()) return; // maintain() may have rebuilt her in another dimension

        // Food Healing: She ONLY heals by eating food from her inventory!
        // No free healing out of thin air.
        if (this.getHealth() < this.getMaxHealth() && !isDowned()) {
            if (++regenTicks >= 80) { // Check every 4 seconds
                regenTicks = 0;
                ItemStack foodStack = consumeFood();
                if (!foodStack.isEmpty()) {
                    FoodProperties food = foodStack.get(DataComponents.FOOD);
                    float healAmount = food != null ? Math.max(2.0F, food.nutrition() + food.saturation() * 2.0F) : 2.0F;
                    this.heal(healAmount);
                    this.playSound(SoundEvents.GENERIC_EAT, 0.85F, 0.9F + this.random.nextFloat() * 0.2F);
                    if (this.level() instanceof ServerLevel sl) {
                        sl.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, foodStack),
                                this.getX(), this.getY(0.7), this.getZ(), 8, 0.15, 0.15, 0.15, 0.05);
                    }
                    if (this.getHealth() >= this.getMaxHealth()) {
                        this.playSound(SoundEvents.PLAYER_BURP, 0.6F, 1.0F);
                    }
                }
            }
        } else {
            regenTicks = 0;
        }
    }

    // ---- interaction (owner only) -----------------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        UUID owner = getOwnerId();
        if (owner == null || !owner.equals(player.getUUID())) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);
        if (HelperCurioCompat.isFirstAidItem(held)) {
            if (player.getCooldowns().isOnCooldown(held.getItem())) {
                return InteractionResult.CONSUME;
            }
            if (!this.isDowned() && this.getHealth() >= this.getMaxHealth()) {
                return InteractionResult.CONSUME;
            }
            if (this.level().isClientSide) return InteractionResult.SUCCESS;
            boolean healed = HelperCurioCompat.applyFirstAid(this, player, held, hand);
            return healed ? InteractionResult.CONSUME : InteractionResult.PASS;
        }

        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        if (isDowned()) return InteractionResult.CONSUME;

        if (isRebellious()) {
            if (held.is(Items.NETHERITE_INGOT) || held.is(Items.DIAMOND) || held.is(Items.GOLD_INGOT) || held.is(Items.ENCHANTED_GOLDEN_APPLE) || held.is(Items.GOLDEN_APPLE)) {
                int giftTrust = held.is(Items.NETHERITE_INGOT) ? 100 : held.is(Items.DIAMOND) ? 60 : held.is(Items.ENCHANTED_GOLDEN_APPLE) ? 80 : 35;
                held.shrink(1);
                setRebellious(false);
                setTrustScore(Math.max(15, getTrustScore() + giftTrust));
                stopAttacking();
                this.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, this.getX(), this.getY(0.8), this.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                }
                player.displayClientMessage(Component.literal("§a<" + this.getDisplayName().getString() + "> §f[pout] ...Hmph. Bribing a dragon with shiny treasures? ...Fine, it worked. Consider yourself forgiven. Don't test me again!"), false);
                return InteractionResult.CONSUME;
            } else {
                player.displayClientMessage(Component.literal("§c<" + this.getDisplayName().getString() + "> §4I'm not listening to you! Offer me gold, diamonds, or back off!"), true);
                return InteractionResult.CONSUME;
            }
        }

        if (player.isSecondaryUseActive() && player instanceof ServerPlayer sp) {
            com.blighteddreams.helper.menu.HelperMenu.open(sp, this);
            return InteractionResult.CONSUME;
        }
        if (!held.isEmpty()) {
            // Check spell scrolls / containers for leveling up companion's magic
            if (com.blighteddreams.helper.magic.HelperIronsCompat.isSpellItem(held)) {
                com.blighteddreams.helper.magic.HelperIronsCompat.SpellUpgrade up = com.blighteddreams.helper.magic.HelperIronsCompat.inspectSpell(held);
                if (up != null) {
                    HelperVariant variant = getVariant();
                    String school = up.school().toLowerCase(java.util.Locale.ROOT);
                    int scrollLvl = up.level();
                    String spellName = up.spellName();

                    if (variant == HelperVariant.DEMON) {
                        if (school.contains("fire")) {
                            int cur = getFireSpellLevel();
                            if (scrollLvl > cur) {
                                setFireSpellLevel(scrollLvl);
                                if (!player.getAbilities().instabuild) held.shrink(1);
                                this.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
                                if (this.level() instanceof ServerLevel sl) {
                                    sl.sendParticles(ParticleTypes.FLAME, getX(), getY(0.7), getZ(), 25, 0.4, 0.5, 0.4, 0.08);
                                    sl.sendParticles(ParticleTypes.LAVA, getX(), getY(0.7), getZ(), 10, 0.3, 0.4, 0.3, 0.05);
                                }
                                player.displayClientMessage(Component.literal("§6§l✦ Level Up! ✦ §c" + this.getDisplayName().getString() + " mastered " + spellName + " (Fire Level " + scrollLvl + ")!"), true);
                                String[] quotes = new String[]{
                                        "Hell yeah! Feel that heat? My flames burn even hotter now!",
                                        "More power for the flames! Let's burn them all!",
                                        "Now that's what I'm talking about. Watch me turn them to ash!"
                                };
                                player.displayClientMessage(Component.literal("§c<" + this.getDisplayName().getString() + "> §f" + quotes[this.random.nextInt(quotes.length)]), false);
                                return InteractionResult.CONSUME;
                            } else {
                                player.displayClientMessage(Component.literal("§c<" + this.getDisplayName().getString() + "> §7I already know magic stronger than this scroll. Bring me something hotter! (Level " + cur + ")"), false);
                                return InteractionResult.CONSUME;
                            }
                        } else {
                            player.displayClientMessage(Component.literal("§c<" + this.getDisplayName().getString() + "> §7Ugh, I only channel Fire magic! Keep that other stuff away from me."), false);
                            return InteractionResult.CONSUME;
                        }
                    } else if (variant == HelperVariant.ANGEL) {
                        if (school.contains("holy") || school.contains("healing")) {
                            int cur = getHolySpellLevel();
                            if (scrollLvl > cur) {
                                setHolySpellLevel(scrollLvl);
                                if (!player.getAbilities().instabuild) held.shrink(1);
                                this.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
                                if (this.level() instanceof ServerLevel sl) {
                                    sl.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.7), getZ(), 20, 0.4, 0.5, 0.4, 0.05);
                                    sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY(0.7), getZ(), 25, 0.4, 0.5, 0.4, 0.1);
                                }
                                player.displayClientMessage(Component.literal("§6§l✦ Level Up! ✦ §b" + this.getDisplayName().getString() + " mastered " + spellName + " (Holy Level " + scrollLvl + ")!"), true);
                                String[] quotes = new String[]{
                                        "The divine light grows stronger within me. I will keep us safe.",
                                        "A sacred blessing. My healing and radiance have ascended!",
                                        "Thank you. May the light protect you, always."
                                };
                                player.displayClientMessage(Component.literal("§b<" + this.getDisplayName().getString() + "> §f" + quotes[this.random.nextInt(quotes.length)]), false);
                                return InteractionResult.CONSUME;
                            } else {
                                player.displayClientMessage(Component.literal("§b<" + this.getDisplayName().getString() + "> §7My holy magic is already at or above this rank. Bring me a higher blessing! (Level " + cur + ")"), false);
                                return InteractionResult.CONSUME;
                            }
                        } else {
                            player.displayClientMessage(Component.literal("§b<" + this.getDisplayName().getString() + "> §7I can only channel Holy and divine magic."), false);
                            return InteractionResult.CONSUME;
                        }
                    } else {
                        player.displayClientMessage(Component.literal("§a<" + this.getDisplayName().getString() + "> §7I fight with blade, draconic power, and martial strength. Keep the scrolls for yourself!"), false);
                        return InteractionResult.CONSUME;
                    }
                }
            }

            FoodProperties food = held.get(DataComponents.FOOD);
            if (food != null && this.getHealth() < this.getMaxHealth()) {
                float heal = Math.max(2, food.nutrition());
                if (HelperCurioCompat.hasCurio(this, "artifacts:plastic_drinking_hat") ||
                    HelperCurioCompat.hasCurio(this, "artifacts:novelty_drinking_hat")) {
                    heal *= 1.5F;
                }
                this.heal(heal);
                if (HelperCurioCompat.hasCurio(this, "artifacts:onion_ring")) {
                    this.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 1));
                }
                held.consume(1, player);
                this.playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.0F);
                return InteractionResult.CONSUME;
            }
            Item item = held.getItem();
            EquipmentSlot slot = isWeapon(item) ? EquipmentSlot.MAINHAND : this.getEquipmentSlotForItem(held);
            if (slot != EquipmentSlot.MAINHAND && slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                return InteractionResult.PASS;
            }
            if (slot == EquipmentSlot.MAINHAND && !isWeapon(item)) return InteractionResult.PASS;
            ItemStack old = this.getItemBySlot(slot);
            this.setItemSlot(slot, held.copyWithCount(1));
            held.shrink(1);
            if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
            this.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }

        Stance next = getStance() == Stance.STAY ? Stance.FOLLOW : Stance.STAY;
        setStance(next);
        player.displayClientMessage(Component.translatable("message.blighted_helper.helper_stance." + next.name().toLowerCase(),
                this.getDisplayName()), true);
        return InteractionResult.CONSUME;
    }

    private static boolean isWeapon(Item item) {
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                || item instanceof net.minecraft.world.item.ProjectileWeaponItem || item instanceof net.minecraft.world.item.MaceItem) return true;
        ItemStack st = new ItemStack(item);
        return WeaponKind.of(st) != WeaponKind.FIST || WeaponKind.hasAttackDamage(st);
    }

    // ---- client tick: animation timers ----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) clientAnimTick();
    }

    private void clientAnimTick() {
        int rawSeq = swingSeq();
        int seq = rawSeq & 0xFFFF;
        if (animSwingSeen == -1) animSwingSeen = seq;
        if (seq != animSwingSeen) {
            animSwingSeen = seq;
            animSwingStartTick = this.tickCount;
            int kindOrd = (rawSeq >> 16) & 0xFF;
            int varVal = (rawSeq >> 24) & 0xFF;
            if (kindOrd > 0 && kindOrd <= WeaponKind.values().length) {
                animSwingKind = WeaponKind.values()[kindOrd - 1];
            } else {
                animSwingKind = WeaponKind.of(this.getMainHandItem(), this.getOffhandItem());
            }
            if (varVal > 0) {
                animSwingVariant = varVal - 1;
            } else {
                animSwingVariant = Math.floorMod(seq, com.blighteddreams.helper.client.HelperSwings.variants(animSwingKind));
            }
            animIdleVariant = -1;
        }

        int castPacked = this.entityData.get(CAST_STATE);
        int cSeq = castPacked & 0xFF;
        if (animCastSeq == -1) animCastSeq = cSeq;
        if (cSeq != animCastSeq) {
            animCastSeq = cSeq;
            animCastStartTick = this.tickCount;
            animCastStyle = (castPacked >> 8) & 0x7;
            animCastSchool = (castPacked >> 11) & 0xF;
            animCastTicks = Math.max(6, (castPacked >> 15) & 0x7F);
            animCastVariant = (castPacked >> 22) & 0x7;
            animIdleVariant = -1;
        }
        int a = aim();
        if (a != animLastAim) {
            animLastAim = a;
            animAimStartTick = this.tickCount;
        }
        boolean still = this.getDeltaMovement().horizontalDistanceSqr() < 0.0004 && !inCombat() && !isDowned() && a == AIM_NONE;
        if (!still) {
            animIdleVariant = -1;
            animNextIdleTick = Math.max(animNextIdleTick, this.tickCount + 100);
        } else if (animIdleVariant >= 0 && this.tickCount - animIdleStartTick > flourishTicks()) {
            animIdleVariant = -1;
            animNextIdleTick = this.tickCount + 140 + this.random.nextInt(200);
        } else if (animIdleVariant < 0 && this.tickCount >= animNextIdleTick) {
            String vname = this.getVariant().name().toLowerCase(java.util.Locale.ROOT);
            int count = com.blighteddreams.helper.client.HelperMotionData.flourishCount(vname);
            animIdleVariant = this.random.nextInt(count > 0 && this.getVariant().usesXlModel() ? count : (this.getVariant().usesXlModel() ? 8 : 5));
            animFlourishTicks = count > 0 && this.getVariant().usesXlModel() ? com.blighteddreams.helper.client.HelperMotionData.flourishTicks(vname, animIdleVariant) : 60;
            animIdleStartTick = this.tickCount;
        }

        if (com.blighteddreams.helper.client.voice.HelperVoice.isSpeaking()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && this.distanceToSqr(mc.player) < 256.0) {
                this.getLookControl().setLookAt(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), 40.0F, 40.0F);
            }
            if (this.tickCount % 14 == 0) {
                double px = this.getX() + (this.random.nextDouble() - 0.5) * 0.22;
                double py = this.getEyeY() + 0.05 + (this.random.nextDouble() - 0.5) * 0.12;
                double pz = this.getZ() + (this.random.nextDouble() - 0.5) * 0.22;
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, px, py, pz, this.random.nextDouble() * 0.8, 0.0, 0.0);
            }
        }
        com.blighteddreams.helper.HelperFx.clientTick(this);
    }

    public void equipStartingGear() {
        String id = HelperConfig.startingWeapon();
        if (id == null || id.isBlank()) return;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return;
        Item item = BuiltInRegistries.ITEM.get(rl);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
    }

    // ---- save / load ----------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerId();
        if (owner != null) tag.putUUID("HelperOwner", owner);
        tag.putBoolean("HelperDowned", isDowned());
        tag.putInt("HelperDownTicks", downTicks);
        tag.putInt("HelperStance", getStance().ordinal());
        tag.putString("HelperCombatTactic", getCombatTactic().name());
        tag.putInt("HelperVariant", getVariant().id);
        tag.putInt("HelperFireSpellLvl", getFireSpellLevel());
        tag.putInt("HelperHolySpellLvl", getHolySpellLevel());
        tag.putInt("HelperLevel", getLevel());
        tag.putInt("HelperXp", getXp());
        tag.putInt("HelperTrust", getTrustScore());
        tag.putBoolean("HelperRebellious", isRebellious());
        agent.save(tag);
        curioCompat.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("HelperOwner")) setOwnerId(tag.getUUID("HelperOwner"));
        this.entityData.set(DOWNED, tag.getBoolean("HelperDowned"));
        this.downTicks = tag.getInt("HelperDownTicks");
        this.entityData.set(STANCE, tag.getInt("HelperStance"));
        if (tag.contains("HelperCombatTactic")) setCombatTactic(CombatTactic.fromString(tag.getString("HelperCombatTactic")));
        if (tag.contains("HelperVariant")) setVariant(HelperVariant.byId(tag.getInt("HelperVariant")));
        if (tag.contains("HelperFireSpellLvl")) setFireSpellLevel(tag.getInt("HelperFireSpellLvl"));
        if (tag.contains("HelperHolySpellLvl")) setHolySpellLevel(tag.getInt("HelperHolySpellLvl"));
        if (tag.contains("HelperTrust")) setTrustScore(tag.getInt("HelperTrust"));
        if (tag.contains("HelperRebellious")) setRebellious(tag.getBoolean("HelperRebellious"));
        if (tag.contains("HelperLevel")) {
            setLevel(tag.getInt("HelperLevel"));
            setXp(tag.getInt("HelperXp"));
        } else {
            setLevel(1);
            setXp(0);
        }
        applyLevelAttributes();
        agent.load(tag);
        curioCompat.load(tag);
    }

    public int getLevel() {
        return this.entityData.get(LEVEL);
    }

    public void setLevel(int level) {
        this.entityData.set(LEVEL, Math.max(1, Math.min(HelperLeveling.MAX_LEVEL, level)));
    }

    public int getXp() {
        return this.entityData.get(XP);
    }

    public void setXp(int xp) {
        this.entityData.set(XP, Math.max(0, xp));
    }

    public int getXpForNextLevel() {
        return HelperLeveling.getXpForNextLevel(getLevel());
    }

    public void applyLevelAttributes() {
        int lvl = getLevel();
        float maxHp = HelperLeveling.getMaxHealth(lvl);
        float scale = HelperLeveling.getScale(lvl);
        float damage = HelperLeveling.getAttackDamage(lvl);

        var hpAttr = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hpAttr != null) hpAttr.setBaseValue(maxHp);

        var scaleAttr = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(scale);

        var dmgAttr = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (dmgAttr != null) dmgAttr.setBaseValue(damage);

        HelperStats.apply(this);

        this.refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LEVEL.equals(key)) {
            this.refreshDimensions();
        }
    }

    public HelperVariant getVariant() {
        return HelperVariant.byId(this.entityData.get(VARIANT));
    }

    public void setVariant(HelperVariant variant) {
        this.entityData.set(VARIANT, variant.id);
        if (!this.level().isClientSide) {
            this.applyLevelAttributes();
        }
    }

    @Override
    public Component getName() {
        if (this.hasCustomName()) return super.getName();
        return Component.literal(HelperConfig.name());
    }

    public boolean isOwner(LivingEntity e) {
        UUID id = getOwnerId();
        return id != null && e != null && id.equals(e.getUUID());
    }

    /** True when the owner is the attacker or the owner of the projectile/spell that caused the damage. */
    private boolean isOwnerDamage(DamageSource source) {
        if (source == null) return false;
        if (source.getEntity() instanceof LivingEntity attacker && isOwner(attacker)) return true;
        return source.getDirectEntity() instanceof LivingEntity direct && isOwner(direct);
    }
}
