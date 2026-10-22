package com.blighteddreams.helper.spike;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Body A of the spike: a ServerPlayer with no network peer (Carpet-style). The vanilla connection tick is what normally
 * calls ServerPlayer.doTick(); nobody ticks our connection, so tick() does it. Movement uses the ordinary LivingEntity
 * input fields (zza / jumping / sprinting) and lets Player.travel do the physics, so speed attributes, sprint, water, ladders
 * and so on behave as for a human.
 */
public class SpikePlayer extends ServerPlayer {
    final FakeConnection.Made net;
    @Nullable
    UUID owner;

    // behaviour driver
    @Nullable
    Vec3 walkTo;
    boolean sprint;
    boolean followOwner;
    boolean lookAtOwner;

    // perf (ns per tick of this player's own tick(), ring buffer)
    private static final int RING = 1200;
    private final long[] ring = new long[RING];
    private int ringPos;
    private long ringCount;
    private long superNs;
    private long doTickNs;
    long worstNs;

    SpikePlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info, FakeConnection.Made net) {
        super(server, level, profile, info);
        this.net = net;
    }

    @Override
    public void tick() {
        long t0 = System.nanoTime();
        super.tick();                       // ServerPlayer.tick: game mode, container sync, advancements, criteria
        long t1 = System.nanoTime();
        drive();
        this.doTick();                      // what the connection tick would call: Player.tick -> aiStep -> travel, health/food sync
        long t2 = System.nanoTime();
        if (this.server.getTickCount() % 10 == 0) {
            this.connection.resetPosition();
            this.serverLevel().getChunkSource().move(this);   // keeps chunk tracking following her
            this.resetLastActionTime();
        }
        long t3 = System.nanoTime();
        long total = t3 - t0;
        ring[ringPos] = total;
        ringPos = (ringPos + 1) % RING;
        ringCount++;
        superNs += t1 - t0;
        doTickNs += t2 - t1;
        if (total > worstNs) worstNs = total;
    }

    private void drive() {
        LivingEntity ownerEntity = null;
        if (owner != null) {
            ServerPlayer o = this.server.getPlayerList().getPlayer(owner);
            ownerEntity = o;
        }
        Vec3 goal = walkTo;
        if (followOwner && ownerEntity != null && this.distanceToSqr(ownerEntity) > 9.0) {
            goal = ownerEntity.position();
        } else if (followOwner) {
            goal = null;
        }
        if (goal == null) {
            this.zza = 0;
            this.xxa = 0;
            this.jumping = false;
            this.setSprinting(false);
            if (lookAtOwner && ownerEntity != null) faceTowards(ownerEntity.getEyePosition(), 30f);
            return;
        }
        double dx = goal.x - this.getX();
        double dz = goal.z - this.getZ();
        if (!followOwner && dx * dx + dz * dz < 0.6 * 0.6) {
            walkTo = null;
            this.zza = 0;
            this.setSprinting(false);
            return;
        }
        faceTowards(new Vec3(goal.x, this.getEyeY(), goal.z), 25f);
        this.zza = 1.0F;
        this.setSprinting(sprint);
        this.jumping = this.horizontalCollision && this.onGround();
    }

    void faceTowards(Vec3 target, float maxStep) {
        double dx = target.x - this.getX();
        double dz = target.z - this.getZ();
        double dy = target.y - this.getEyeY();
        float wantYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float wantPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
        float yaw = Mth.approachDegrees(this.getYRot(), wantYaw, maxStep);
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setXRot(Mth.clamp(Mth.approachDegrees(this.getXRot(), wantPitch, maxStep), -90f, 90f));
    }

    /** Summary of her own tick cost over the last up-to-1200 ticks, in microseconds. */
    String perfSummary() {
        int n = (int) Math.min(ringCount, RING);
        if (n == 0) return "no ticks yet";
        long[] copy = new long[n];
        System.arraycopy(ring, 0, copy, 0, n);
        java.util.Arrays.sort(copy);
        long sum = 0;
        for (long v : copy) sum += v;
        double avg = sum / (double) n / 1000.0;
        double median = copy[n / 2] / 1000.0;
        double p99 = copy[Math.min(n - 1, (int) (n * 0.99))] / 1000.0;
        double max = copy[n - 1] / 1000.0;
        return String.format("her own tick() over last %d ticks: avg %.1f us, median %.1f us, p99 %.1f us, max %.1f us (all-time worst %.1f us); split: ServerPlayer.tick %.1f us avg, doTick %.1f us avg (lifetime)",
                n, avg, median, p99, max, worstNs / 1000.0, superNs / (double) ringCount / 1000.0, doTickNs / (double) ringCount / 1000.0);
    }

    void resetPerf() {
        ringCount = 0;
        ringPos = 0;
        worstNs = 0;
        superNs = 0;
        doTickNs = 0;
    }

    /** The hurt animation, death and respawn paths all assume a client; log what happens when she dies. */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        Spike.LOG.info("[SPIKE] SpikePlayer.die({}) at {} health={} (vanilla would now wait for a respawn request that never comes)", source.type().msgId(), this.blockPosition(), this.getHealth());
        super.die(source);
    }

}
