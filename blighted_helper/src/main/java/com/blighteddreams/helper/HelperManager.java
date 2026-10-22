package com.blighteddreams.helper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * One companion active per player at a time, with separate levels, equipment, inventory,
 * curios, and custom names stored persistently per player and per companion variant.
 */
public final class HelperManager {
    public static final String KEY = "BlightedHelperId";
    public static final String HELPERS_TAG = "BlightedHelpers";
    public static final String ACTIVE_VARIANT_TAG = "BlightedActiveVariant";
    public static final String SUMMONED_TAG = "BlightedHelperSummoned";

    private HelperManager() {}

    @net.neoforged.bus.api.SubscribeEvent
    public static void onClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        CompoundTag orig = event.getOriginal().getPersistentData();
        CompoundTag clone = event.getEntity().getPersistentData();

        String id = orig.getString(KEY);
        if (!id.isEmpty()) clone.putString(KEY, id);

        boolean chosen = orig.getBoolean("HasChosenStarterHelper");
        if (chosen) clone.putBoolean("HasChosenStarterHelper", true);

        if (orig.contains(HELPERS_TAG)) {
            clone.put(HELPERS_TAG, orig.getCompound(HELPERS_TAG).copy());
        }
        if (orig.contains(ACTIVE_VARIANT_TAG)) {
            clone.putInt(ACTIVE_VARIANT_TAG, orig.getInt(ACTIVE_VARIANT_TAG));
        }
        if (orig.contains(SUMMONED_TAG)) {
            clone.putBoolean(SUMMONED_TAG, orig.getBoolean(SUMMONED_TAG));
        }

        // Backwards compatibility keys
        if (orig.contains("BlightedHelperVariant")) {
            clone.putInt("BlightedHelperVariant", orig.getInt("BlightedHelperVariant"));
        }
        if (orig.contains("BlightedHelperName")) {
            clone.putString("BlightedHelperName", orig.getString("BlightedHelperName"));
        }
        if (orig.contains("BlightedHelperLevel")) {
            clone.putInt("BlightedHelperLevel", orig.getInt("BlightedHelperLevel"));
        }
        if (orig.contains("BlightedHelperXp")) {
            clone.putInt("BlightedHelperXp", orig.getInt("BlightedHelperXp"));
        }
    }

    /** Awards XP to the active companion when she or her bonded player slays monsters or bosses nearby. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide) return;
        if (!(victim instanceof net.minecraft.world.entity.monster.Enemy) && !HelperLeveling.isBoss(victim)) return;

        net.minecraft.world.damagesource.DamageSource source = event.getSource();
        Entity directAttacker = source != null ? source.getEntity() : null;

        if (directAttacker instanceof HelperEntity helper) {
            HelperLeveling.awardKillXp(helper, victim);
            return;
        }

        if (directAttacker instanceof ServerPlayer player) {
            HelperEntity companion = find(player);
            if (companion != null && companion.isAlive() && !companion.isDowned() && companion.distanceToSqr(victim) <= 1024.0) {
                HelperLeveling.awardKillXp(companion, victim);
                return;
            }
        }

        if (victim.getLastHurtByMob() instanceof HelperEntity helper) {
            if (helper.isAlive() && !helper.isDowned() && helper.distanceToSqr(victim) <= 1024.0) {
                HelperLeveling.awardKillXp(helper, victim);
                return;
            }
        }

        if (victim.getLastHurtByMob() instanceof ServerPlayer player) {
            HelperEntity companion = find(player);
            if (companion != null && companion.isAlive() && !companion.isDowned() && companion.distanceToSqr(victim) <= 1024.0) {
                HelperLeveling.awardKillXp(companion, victim);
            }
        }
    }

    /** Prompts the player with the Sanctuary UI when they first join if they have no companion. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            String id = sp.getPersistentData().getString(KEY);
            boolean chosen = sp.getPersistentData().getBoolean("HasChosenStarterHelper");
            if (!chosen && id.isEmpty()) {
                com.blighteddreams.helper.util.ServerScheduler.later(sp.server, 25, () -> {
                    if (sp.isAlive() && sp.connection != null) {
                        openStarterScreenForPlayer(sp);
                    }
                });
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            saveActiveHelper(sp);
        }
    }

    private static UUID storedId(ServerPlayer p) {
        String s = p.getPersistentData().getString(KEY);
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static HelperEntity find(ServerPlayer p) {
        UUID id = storedId(p);
        if (id == null) return null;
        for (ServerLevel l : p.server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e instanceof HelperEntity h && h.isAlive()) return h;
        }
        return null;
    }

    /** Saves the active companion's full entity data, inventory, curios, level, and custom name into persistent storage. */
    public static void saveHelperState(ServerPlayer p, HelperEntity h) {
        if (p == null || h == null) return;
        int variantId = h.getVariant().id;
        CompoundTag helpers = p.getPersistentData().getCompound(HELPERS_TAG);
        String vKey = "variant_" + variantId;
        CompoundTag profile = helpers.contains(vKey) ? helpers.getCompound(vKey) : new CompoundTag();

        CompoundTag entityTag = h.saveWithoutId(new CompoundTag());
        entityTag.remove("UUID");

        profile.put("entityTag", entityTag);
        profile.putInt("variant", variantId);
        profile.putInt("level", h.getLevel());
        profile.putInt("xp", h.getXp());
        String customName = h.hasCustomName() ? h.getCustomName().getString() : h.getVariant().getRandomName();
        profile.putString("customName", customName);
        profile.putBoolean("initialized", true);

        helpers.put(vKey, profile);
        p.getPersistentData().put(HELPERS_TAG, helpers);

        // Update legacy root keys for compatibility
        p.getPersistentData().putInt("BlightedHelperVariant", variantId);
        p.getPersistentData().putString("BlightedHelperName", customName);
        p.getPersistentData().putInt("BlightedHelperLevel", h.getLevel());
        p.getPersistentData().putInt("BlightedHelperXp", h.getXp());
    }

    public static void saveActiveHelper(ServerPlayer p) {
        HelperEntity current = find(p);
        if (current != null && current.isAlive()) {
            saveHelperState(p, current);
        }
    }

    /**
     * Builds data bundle for the Companion Sanctuary UI:
     * - isSummoned: boolean
     * - activeVariant: int
     * - profiles: CompoundTag for each variant (level, name, initialized)
     */
    public static CompoundTag buildStarterSelectData(ServerPlayer sp) {
        saveActiveHelper(sp);

        CompoundTag tag = new CompoundTag();
        HelperEntity current = find(sp);
        tag.putBoolean("isSummoned", current != null);

        int activeVariant = (current != null)
                ? current.getVariant().id
                : (sp.getPersistentData().contains(ACTIVE_VARIANT_TAG)
                    ? sp.getPersistentData().getInt(ACTIVE_VARIANT_TAG)
                    : 0);
        tag.putInt("activeVariant", activeVariant);

        CompoundTag helpers = sp.getPersistentData().getCompound(HELPERS_TAG);
        CompoundTag profiles = new CompoundTag();

        for (HelperVariant v : HelperVariant.values()) {
            CompoundTag pTag = new CompoundTag();
            String vKey = "variant_" + v.id;
            if (helpers.contains(vKey)) {
                CompoundTag saved = helpers.getCompound(vKey);
                pTag.putBoolean("initialized", saved.getBoolean("initialized"));
                pTag.putInt("level", Math.max(1, saved.getInt("level")));
                pTag.putInt("xp", saved.getInt("xp"));
                String savedName = saved.getString("customName");
                pTag.putString("name", (savedName == null || savedName.isBlank()) ? v.getRandomName() : savedName);
            } else {
                pTag.putBoolean("initialized", false);
                pTag.putInt("level", 1);
                pTag.putInt("xp", 0);
                pTag.putString("name", v.getRandomName());
            }
            profiles.put(vKey, pTag);
        }
        tag.put("profiles", profiles);
        return tag;
    }

    public static void openStarterScreenForPlayer(ServerPlayer sp) {
        PacketDistributor.sendToPlayer(sp, new com.blighteddreams.helper.network.HelperPayloads.OpenStarterSelect(buildStarterSelectData(sp)));
    }

    /** Called when the player selects or switches their companion variant and name. */
    public static void chooseStarter(ServerPlayer p, int variantId, String name) {
        summonVariant(p, variantId, name);
    }

    /**
     * Summons a specific companion variant for the player:
     * - If another companion is out, her gear & level are safely saved first.
     * - Restores the target companion's separate level, gear, bag inventory, and curios.
     * - Keeps or applies the chosen custom name.
     */
    public static void summonVariant(ServerPlayer p, int variantId, String optName) {
        if (!HelperConfig.enabled()) return;
        p.getPersistentData().putBoolean("HasChosenStarterHelper", true);

        // 1. If a companion is currently out, save her state and discard her
        HelperEntity existing = find(p);
        if (existing != null) {
            saveHelperState(p, existing);
            existing.discard();
        }
        resetClientVoice(p);

        ServerLevel level = p.serverLevel();
        HelperEntity fresh = HelperEntities.HELPER.get().create(level);
        if (fresh == null) return;

        CompoundTag helpers = p.getPersistentData().getCompound(HELPERS_TAG);
        String vKey = "variant_" + variantId;
        HelperVariant variant = HelperVariant.byId(variantId);

        if (helpers.contains(vKey) && helpers.getCompound(vKey).contains("entityTag")) {
            // Restore existing companion profile!
            CompoundTag profile = helpers.getCompound(vKey);
            CompoundTag entityTag = profile.getCompound("entityTag");
            fresh.load(entityTag);
            fresh.setOwnerId(p.getUUID());
            fresh.setVariant(variant);

            // Preserve or update custom name
            if (optName != null && !optName.isBlank()) {
                fresh.setCustomName(Component.literal(optName.trim()));
                profile.putString("customName", optName.trim());
            } else if (profile.contains("customName")) {
                fresh.setCustomName(Component.literal(profile.getString("customName")));
            }
            fresh.applyLevelAttributes();
        } else {
            // Fresh companion for this variant
            fresh.setOwnerId(p.getUUID());
            fresh.setVariant(variant);
            String initialName = (optName != null && !optName.isBlank())
                    ? optName.trim()
                    : (helpers.contains(vKey) && helpers.getCompound(vKey).contains("customName"))
                    ? helpers.getCompound(vKey).getString("customName")
                    : variant.getRandomName();
            fresh.setCustomName(Component.literal(initialName));
            fresh.setLevel(1);
            fresh.setXp(0);
            fresh.applyLevelAttributes();
            fresh.equipStartingGear();
            fresh.setHealth(fresh.getMaxHealth());
        }

        fresh.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0F);
        teleportNear(fresh, p);
        p.getPersistentData().putString(KEY, fresh.getUUID().toString());
        p.getPersistentData().putInt(ACTIVE_VARIANT_TAG, variantId);
        p.getPersistentData().putBoolean(SUMMONED_TAG, true);
        level.addFreshEntity(fresh);
        fresh.teleportTo(fresh.getX(), fresh.getY(), fresh.getZ());

        // Save profile snapshot immediately
        saveHelperState(p, fresh);

        // Ensure player has the Dream Charm
        ensureDreamCharm(p);

        // FX & Feedback
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, fresh.getX(), fresh.getY() + 0.8, fresh.getZ(), 28, 0.4, 0.6, 0.4, 0.1);
        level.sendParticles(ParticleTypes.PORTAL, fresh.getX(), fresh.getY() + 0.5, fresh.getZ(), 22, 0.3, 0.5, 0.3, 0.05);
        level.playSound(null, fresh.getX(), fresh.getY(), fresh.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0F, 1.1F);

        String companionName = fresh.hasCustomName() ? fresh.getCustomName().getString() : variant.title;
        p.displayClientMessage(Component.literal("✦ You have summoned " + companionName + " (" + variant.title + ", Lv " + fresh.getLevel() + ")!").withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * Unsummons (dismisses) the active companion back into the Dream Charm.
     * Safely saves her level, inventory, and gear into her variant profile before removing her.
     */
    public static boolean unsummon(ServerPlayer p) {
        HelperEntity h = find(p);
        if (h == null) {
            p.displayClientMessage(Component.literal("§7No companion is currently summoned."), true);
            return false;
        }

        saveHelperState(p, h);
        String name = h.hasCustomName() ? h.getCustomName().getString() : h.getVariant().title;

        if (h.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, h.getX(), h.getY() + 0.8, h.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
            sl.sendParticles(ParticleTypes.WITCH, h.getX(), h.getY() + 0.5, h.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
            sl.playSound(null, h.getX(), h.getY(), h.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 1.2F);
        }

        p.getPersistentData().remove(KEY);
        p.getPersistentData().putBoolean(SUMMONED_TAG, false);
        resetClientVoice(p);
        h.discard();

        p.displayClientMessage(Component.literal("✦ " + name + " has returned to the Dream Charm.").withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** General summon: summons the active/last companion or calls her to the player's side. */
    public static void summon(ServerPlayer p) {
        HelperEntity current = find(p);
        if (current != null) {
            relocate(current, p);
            current.standUp();
            String name = current.hasCustomName() ? current.getCustomName().getString() : current.getVariant().title;
            p.displayClientMessage(Component.literal("✦ Called " + name + " to your side.").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        int activeVariant = p.getPersistentData().contains(ACTIVE_VARIANT_TAG)
                ? p.getPersistentData().getInt(ACTIVE_VARIANT_TAG)
                : 0;
        summonVariant(p, activeVariant, null);
    }

    /** Legacy helper method for commands / compatibility. */
    public static String summonOrRecall(ServerPlayer p) {
        summon(p);
        return "message.blighted_helper.helper_summoned";
    }

    /** Stops any speech still queued on the client when the companion is replaced, moved across dimensions or dismissed. */
    private static void resetClientVoice(ServerPlayer p) {
        if (p == null) return;
        try {
            PacketDistributor.sendToPlayer(p, new com.blighteddreams.helper.network.HelperPayloads.VoiceReset());
        } catch (Throwable ignored) {
            // A client without the packet (or during login) simply keeps its own voice state.
        }
    }

    public static void ensureDreamCharm(ServerPlayer p) {
        boolean hasCharm = false;
        for (net.minecraft.world.item.ItemStack is : p.getInventory().items) {
            if (is.getItem() instanceof DreamCharmItem) {
                hasCharm = true;
                break;
            }
        }
        if (!hasCharm) {
            net.minecraft.world.item.ItemStack charm = new net.minecraft.world.item.ItemStack(HelperItems.DREAM_CHARM.get());
            if (!p.getInventory().add(charm)) {
                p.drop(charm, false);
            }
        }
    }

    /** Moves the companion beside the player, crossing dimensions if needed. Returns the (possibly new) entity. */
    public static HelperEntity relocate(HelperEntity h, ServerPlayer p) {
        if (h.level() == p.level()) {
            teleportNear(h, p);
            return h;
        }
        CompoundTag tag = h.saveWithoutId(new CompoundTag());
        tag.remove("UUID");
        h.discard();
        resetClientVoice(p);
        ServerLevel level = p.serverLevel();
        HelperEntity copy = HelperEntities.HELPER.get().create(level);
        if (copy == null) return h;
        copy.load(tag);
        copy.setOwnerId(p.getUUID());
        copy.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0F);
        teleportNear(copy, p);
        p.getPersistentData().putString(KEY, copy.getUUID().toString());
        level.addFreshEntity(copy);
        copy.teleportTo(copy.getX(), copy.getY(), copy.getZ());
        return copy;
    }

    /**
     * Cross-dimension travel for the agent. Keeps owner, inventory, progression, memory and agent state. Unlike
     * {@link #relocate}, the landing spot is chosen by the caller.
     */
    public static boolean teleportTo(HelperEntity h, ServerLevel targetLevel, BlockPos pos) {
        if (targetLevel == null || pos == null) return false;
        if (h.level() == targetLevel) {
            h.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            h.getNavigation().stop();
            h.setDeltaMovement(0, 0, 0);
            h.fallDistance = 0.0F;
            return true;
        }
        UUID ownerId = h.getOwnerId();
        CompoundTag tag = h.saveWithoutId(new CompoundTag());
        tag.remove("UUID");
        h.discard();
        HelperEntity copy = HelperEntities.HELPER.get().create(targetLevel);
        if (copy == null) return false;
        copy.load(tag);
        copy.setOwnerId(ownerId);
        copy.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, copy.getYRot(), 0.0F);
        copy.setDeltaMovement(0, 0, 0);
        copy.fallDistance = 0.0F;
        targetLevel.addFreshEntity(copy);
        copy.teleportTo(copy.getX(), copy.getY(), copy.getZ());
        ServerPlayer owner = copy.getOwner();
        if (owner != null) owner.getPersistentData().putString(KEY, copy.getUUID().toString());
        resetClientVoice(owner);
        return true;
    }

    /** Called about once a second from her own tick: follow across dimensions, teleport if left behind, drop duplicates. */
    static void maintain(HelperEntity h) {
        ServerPlayer owner = h.getOwner();
        if (owner == null) return;

        UUID stored = storedId(owner);
        if (stored != null && !stored.equals(h.getUUID())) {
            h.discard(); // a newer companion has replaced this one
            return;
        }
        if (stored == null) owner.getPersistentData().putString(KEY, h.getUUID().toString());

        // stuck inside blocks (suffocating): get out beside the owner, even when told to stay
        if (h.isInWall() && h.level() == owner.level()) {
            teleportNear(h, owner);
            return;
        }
        // A roaming task (mining, exploring, building a portal) owns her position. Do not yank her home mid-job.
        if (h.agent().isRoaming() && h.level() == owner.level()) {
            h.stuckSeconds = 0;
            return;
        }
        if (h.getStance() == HelperEntity.Stance.STAY) return;
        if (h.level() != owner.level()) {
            relocate(h, owner);
            return;
        }
        double max = HelperConfig.teleportDistance();
        if (h.distanceToSqr(owner) > max * max) {
            teleportNear(h, owner);
            return;
        }
        // stuck: trying to reach the owner but hardly moving for 3 seconds: teleport beside them.
        boolean wantsOwner = h.getTarget() == null && h.distanceToSqr(owner) > 49.0 && owner.onGround();
        double moved = Math.sqrt(h.distanceToSqr(h.stuckX, h.stuckY, h.stuckZ));
        h.stuckX = h.getX();
        h.stuckY = h.getY();
        h.stuckZ = h.getZ();
        if (!wantsOwner || moved > 1.0) {
            h.stuckSeconds = 0;
            return;
        }
        boolean noPath = h.getNavigation().isDone() && h.getNavigation().createPath(owner, 1) == null;
        h.stuckSeconds += noPath ? 2 : 1;
        if (h.stuckSeconds >= 3) {
            h.stuckSeconds = 0;
            teleportNear(h, owner);
        }
    }

    /** Her arrows never hurt her owner. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onProjectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        if (event.getProjectile().getOwner() instanceof HelperEntity h
                && event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult ehr
                && (h.isOwner(ehr.getEntity() instanceof LivingEntity le ? le : null) || ehr.getEntity() == h)) {
            event.setCanceled(true);
        }
    }

    /** She fell out of the world: put her back beside her owner, or at world spawn. */
    static void rescue(HelperEntity h) {
        ServerPlayer owner = h.getOwner();
        if (owner != null && owner.level() == h.level()) {
            teleportNear(h, owner);
        } else if (h.level() instanceof ServerLevel sl) {
            BlockPos s = sl.getSharedSpawnPos();
            h.moveTo(s.getX() + 0.5, s.getY() + 1.0, s.getZ() + 0.5);
        }
        h.fallDistance = 0.0F;
        h.setDeltaMovement(0, 0, 0);
    }

    static void teleportNear(HelperEntity h, Entity target) {
        RandomSource r = h.getRandom();
        BlockPos targetPos = target.blockPosition();
        double bestX = target.getX();
        double bestY = target.getY();
        double bestZ = target.getZ();
        boolean found = false;

        for (int i = 0; i < 24; i++) {
            int dx = r.nextInt(7) - 3;
            int dz = r.nextInt(7) - 3;
            if (dx == 0 && dz == 0) continue;

            for (int dy = 2; dy >= -3; dy--) {
                BlockPos floor = targetPos.offset(dx, dy - 1, dz);
                BlockPos feet = targetPos.offset(dx, dy, dz);
                BlockPos head = feet.above();

                if (h.level().getBlockState(floor).blocksMotion()
                        && !h.level().getBlockState(feet).blocksMotion()
                        && !h.level().getBlockState(head).blocksMotion()) {
                    double cx = feet.getX() + 0.5;
                    double cy = feet.getY();
                    double cz = feet.getZ() + 0.5;
                    if (h.level().noCollision(h, h.getBoundingBox().move(cx - h.getX(), cy - h.getY(), cz - h.getZ()))) {
                        bestX = cx;
                        bestY = cy;
                        bestZ = cz;
                        found = true;
                        break;
                    }
                }
            }
            if (found) break;
        }

        if (h.isAddedToLevel()) {
            h.teleportTo(bestX, bestY, bestZ);
        } else {
            h.moveTo(bestX, bestY, bestZ, h.getYRot(), h.getXRot());
        }
        h.getNavigation().stop();
        h.setDeltaMovement(0, 0, 0);
        h.fallDistance = 0.0F;
    }
}
