package com.blighteddreams.helper.client;

import com.blighteddreams.helper.AvatarConfig;
import com.blighteddreams.helper.HelperEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws Emily as a multi-directional, depth-layered 2.5D character integrated into the 3D environment:
 *  - Switches directionally between front, left, right, back (open wings), and rear (folded wings) layer stacks
 *    based on the viewing angle relative to her body orientation (with hysteresis to avoid boundary flicker).
 *  - Within each directional sector, preserves sideways parallax so segmented depth layers (tail, wings, body,
 *    dress, hair, horns, glowing gems/eyes) slide naturally past each other in 3D space.
 *  - Casts a real, animated 2D silhouette shadow of her layers onto the ground blocks oriented away from the
 *    sun/moon angle, plus a soft ground-contact ambient occlusion shadow under her gown train and heels.
 *  - Shades layers with curved 3D cylindrical normals, vertical world light sampling (feet to head), ground
 *    contact AO, and full-bright emissive highlights on her emerald eyes and gems.
 */
public class HelperAvatarRenderer extends EntityRenderer<HelperEntity> {
    private static final float[][] DEBUG_COLORS = {
            {1f, 0.50f, 0.50f}, {0.50f, 1f, 0.50f}, {0.50f, 0.70f, 1f},
            {1f, 0.95f, 0.45f}, {0.95f, 0.55f, 1f}, {0.45f, 1f, 0.95f}, {1f, 0.75f, 0.45f}
    };
    private static final int HISTORY = 10;
    private static final int CURVE_COLS = 6;

    private final HelperSkinRenderer skinRenderer;
    private final HelperGeoRenderer geo;
    private final Map<HelperEntity, State> states = new WeakHashMap<>();
    private final Map<AbstractTexture, Boolean> filtered = new WeakHashMap<>();

    /** Per-entity client memory: recent positions, leaning, active directional sector, idle timer. */
    private static final class State {
        int lastTick = -1;
        final ArrayDeque<double[]> history = new ArrayDeque<>(); // newest first: x, y, z
        double speed;        // blocks per tick
        double vx;
        double vz;
        float roll;
        float pitch;
        double[] teleportFrom;
        int teleportTicks;
        int sector = 0;      // 0 = front, +1 = left, -1 = right, 2 = back/rear
        int idleTicks = 0;
        String overrideView = null;
    }

    public HelperAvatarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.skinRenderer = new HelperSkinRenderer(ctx);
        this.geo = new HelperGeoRenderer(ctx);
        this.shadowRadius = 0.42F;
        this.shadowStrength = 0.65F;
    }

    @Override
    public float getShadowRadius(HelperEntity e) {
        return 0.42F * e.getScale();
    }

    @Override
    public ResourceLocation getTextureLocation(HelperEntity e) {
        if (AvatarConfig.skin()) {
            return skinRenderer.getTextureLocation(e);
        }
        StandeeLayers.Stack s = StandeeLayers.get();
        return AvatarConfig.standee() && !s.layers().isEmpty() ? s.layers().get(s.layers().size() - 1).texture() : geo.getTextureLocation(e);
    }

    @Override
    public void render(HelperEntity e, float entityYaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        State st = states.computeIfAbsent(e, k -> new State());
        if (e.tickCount != st.lastTick) {
            st.lastTick = e.tickCount;
            onTick(e, st);
        }
        if (AvatarConfig.skin()) {
            skinRenderer.render(e, entityYaw, partial, ps, buf, light);
            return;
        }
        StandeeLayers.Stack stack = StandeeLayers.get();
        if (!AvatarConfig.standee() || stack.layers().isEmpty()) {
            geo.render(e, entityYaw, partial, ps, buf, light);
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        double ex = Mth.lerp(partial, e.xo, e.getX());
        double ey = Mth.lerp(partial, e.yo, e.getY());
        double ez = Mth.lerp(partial, e.zo, e.getZ());
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        float camAngle = (float) Math.toDegrees(Math.atan2(cam.x - ex, cam.z - ez));
        float bodyAngle = -Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        float delta = Mth.wrapDegrees(camAngle - bodyAngle);

        boolean downed = e.isDowned();
        float atk = e.getAttackAnim(partial);
        float lunge = atk > 0f ? Mth.sin(atk * Mth.PI) : 0f;

        // Select directional view (front / left / right / back / rear) + compute sector-local parallax angle
        String viewKey = "front";
        float sectorCenter = 0f;
        if (AvatarConfig.directional()) {
            st.sector = updateSector(st.sector, delta);
            switch (st.sector) {
                case 1 -> {
                    viewKey = "left";
                    sectorCenter = 90f;
                }
                case -1 -> {
                    viewKey = "right";
                    sectorCenter = -90f;
                }
                case 2 -> {
                    boolean folded = downed || (e.getStance() == HelperEntity.Stance.STAY && st.speed < 0.01 && atk <= 0f) || st.idleTicks > 80;
                    viewKey = folded && !stack.forDirection("rear").isEmpty() ? "rear" : "back";
                    sectorCenter = delta >= 0f ? 180f : -180f;
                }
                default -> {
                    viewKey = "front";
                    sectorCenter = 0f;
                }
            }
        }
        List<StandeeLayers.Layer> activeLayers = stack.forDirection(viewKey);

        float localDelta = AvatarConfig.directional() ? Mth.wrapDegrees(delta - sectorCenter) : delta;
        float residual = Mth.clamp(localDelta * (1f - AvatarConfig.turn()), -AvatarConfig.maxView(), AvatarConfig.maxView());
        float yaw = camAngle - residual;
        double sin = Math.sin(Math.toRadians(yaw));
        double cos = Math.cos(Math.toRadians(yaw));

        // Lean into movement + natural walking stride bob
        double lateral = st.vx * cos - st.vz * sin;
        double toward = st.vx * sin + st.vz * cos;
        float time = (e.tickCount + partial) / 20f;
        float walkPos = e.walkAnimation.position(partial);
        float walkSpd = Math.min(1f, e.walkAnimation.speed(partial));
        float walkBob = Math.abs(Mth.cos(walkPos * 0.6662f)) * 0.032f * walkSpd;
        float walkSway = Mth.sin(walkPos * 0.6662f) * 1.8f * walkSpd;

        float targetRoll = (float) Mth.clamp(-lateral * 75.0, -10.0, 10.0) + walkSway + Mth.sin(time * Mth.TWO_PI * 0.17f) * 0.5f;
        float targetPitch = (float) Mth.clamp(toward * 50.0, -7.0, 7.0);
        st.roll += (targetRoll - st.roll) * 0.14f;
        st.pitch += (targetPitch - st.pitch) * 0.14f;

        float entScale = e.getScale();
        float hover = AvatarConfig.hover() * entScale;
        float idleBob = hover > 0.01f ? Mth.sin(time * Mth.TWO_PI * 0.28f) * 0.03f * entScale : 0f;
        float y = hover + idleBob + walkBob * entScale - (downed ? 0.38f * entScale : 0f);
        float roll = st.roll - lunge * 9f;
        float pitch = st.pitch + (downed ? 9f : 0f) - lunge * 6f;
        if (com.blighteddreams.helper.client.voice.HelperVoice.isSpeaking()) {
            residual *= 0.25f;
            yaw = camAngle - residual;
            pitch += Mth.sin(time * Mth.TWO_PI * 1.8f) * 1.5f;
            roll += Mth.cos(time * Mth.TWO_PI * 0.9f) * 0.8f;
        }
        float scale = (1f + 0.05f * lunge) * entScale;
        float tint = downed ? 0.68f : 1f;

        float height = AvatarConfig.figureHeight();
        float width = height * stack.aspect();
        int overlay = LivingEntityRenderer.getOverlayCoords(e, 0.0F);

        // Environmental light sampling: sample at feet and eye level for natural vertical world lighting
        int footLight = getEnvironmentalLight(e, ex, ey + 0.2 * entScale, ez, light);
        int headLight = getEnvironmentalLight(e, ex, ey + height * 0.78 * entScale, ez, light);

        // 1. Cast animated 2D silhouette shadow onto the ground blocks beneath her
        if (AvatarConfig.castShadow()) {
            drawCastShadow(e, partial, ps, buf, activeLayers, ex, ey, ez, yaw, width * entScale, height * entScale, time, walkSpd, downed, footLight);
        }

        // 2. Fading afterimages when dashing or teleporting
        if (AvatarConfig.afterimages()) {
            int n = 0;
            if (st.speed > 0.14) {
                Iterator<double[]> it = st.history.iterator();
                int age = 0;
                while (it.hasNext() && n < 3) {
                    double[] p = it.next();
                    age++;
                    if (age % 2 != 0) {
                        continue;
                    }
                    n++;
                    ghost(ps, buf, activeLayers, p[0] - ex, p[1] - ey, p[2] - ez, yaw, roll, pitch, y, scale, width, height, time, walkSpd, footLight, headLight, overlay, 0.28f / n);
                }
            }
            if (st.teleportFrom != null && st.teleportTicks > 0) {
                double[] p = st.teleportFrom;
                ghost(ps, buf, activeLayers, p[0] - ex, p[1] - ey, p[2] - ez, yaw, roll, pitch, y, scale, width, height, time, walkSpd, footLight, headLight, overlay, 0.42f * st.teleportTicks / 8f);
            }
        }

        // 3. Main directional depth-layered standee
        ps.pushPose();
        ps.translate(0.0, y, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.scale(scale, scale, 1f);
        drawStack(ps, buf, activeLayers, width, height, time, walkSpd, footLight, headLight, overlay, 1f, tint, tint, tint, downed);
        ps.popPose();
    }

    /**
     * 4-way directional sector selector with 5-degree hysteresis so walking near a 45/135-degree boundary never flickers.
     * Sector 0 = front ([-48, +48]), +1 = left ((+48, +132)), -1 = right ((-132, -48)), 2 = back/rear (|delta| >= 132).
     */
    private static int updateSector(int current, float delta) {
        float abs = Math.abs(delta);
        float frontLimit = (current == 0) ? 53f : 45f;
        float backLimit = (current == 2) ? 127f : 135f;
        if (abs <= frontLimit) {
            return 0;
        }
        if (abs >= backLimit) {
            return 2;
        }
        if (current == 1 && delta > -5f) {
            return 1;
        }
        if (current == -1 && delta < 5f) {
            return -1;
        }
        return delta >= 0f ? 1 : -1;
    }

    private static int getEnvironmentalLight(HelperEntity e, double x, double y, double z, int fallbackLight) {
        Level level = e.level();
        BlockPos pos = BlockPos.containing(x, y, z);
        int packed = LevelRenderer.getLightColor(level, pos);
        if (packed == 0) {
            packed = fallbackLight;
        }
        int block = LightTexture.block(packed);
        int sky = LightTexture.sky(packed);
        // If envShading is on, respect real world lighting (only a tiny floor of 2 so she isn't pitch-black in caves while her eyes/gems glow at 15)
        int minBlock = AvatarConfig.envShading() ? 2 : 5;
        return LightTexture.pack(Math.max(block, minBlock), sky);
    }

    /**
     * Projects her animated 2D layer stack onto the ground plane beneath her feet, skewed away from the sun/moon
     * direction, plus a soft ground-contact shadow under her gown hem so she looks anchored to the terrain.
     */
    private void drawCastShadow(HelperEntity e, float partial, PoseStack ps, MultiBufferSource buf,
                                List<StandeeLayers.Layer> layers, double ex, double ey, double ez, float yaw,
                                float width, float height, float time, float walkSpd, boolean drooping, int footLight) {
        Level level = e.level();
        double groundY = findGroundY(level, ex, ey, ez);
        float dyGround = (float) (groundY - ey);
        if (dyGround < -2.2f || dyGround > 0.6f) {
            return;
        }
        float heightFade = Mth.clamp(1.0f - Math.abs(dyGround) * 0.45f, 0.0f, 1.0f);
        int skyLvl = LightTexture.sky(footLight);
        int blkLvl = LightTexture.block(footLight);
        float lightFactor = Mth.clamp(Math.max(skyLvl, blkLvl * 0.85f) / 15.0f, 0.18f, 1.0f);
        float shadowAlpha = 0.44f * heightFade * lightFactor;
        if (shadowAlpha <= 0.02f) {
            return;
        }

        // Compute celestial shadow direction in world space, then transform into billboard-local space
        float celestial = level.getTimeOfDay(partial) * Mth.TWO_PI;
        float sunX = -Mth.sin(celestial);
        float sunY = Mth.cos(celestial);
        if (sunY < 0f) {
            // Moon shadow at night
            sunX = -sunX;
            sunY = -sunY;
            shadowAlpha *= 0.72f;
        }
        // World shadow vector points opposite the sun's horizontal azimuth, with a slight south offset
        float shadowLen = Mth.clamp((1.0f - Mth.clamp(sunY, 0.15f, 0.95f)) * 0.75f + 0.32f, 0.30f, 0.85f);
        float worldShX = -sunX * shadowLen;
        float worldShZ = 0.26f * shadowLen;

        // Convert world (worldShX, worldShZ) into local frame after YP.rotationDegrees(yaw)
        float rad = (float) Math.toRadians(yaw);
        float sinY = Mth.sin(rad);
        float cosY = Mth.cos(rad);
        // Local +X = (cosY, -sinY), Local +Z = (sinY, cosY)
        float localShX = worldShX * cosY - worldShZ * sinY;
        float localShZ = worldShX * sinY + worldShZ * cosY;
        // Ensure the projected shadow stretches away from the sprite baseline so it's always visible on the ground
        if (Math.abs(localShZ) < 0.24f) {
            localShZ = (localShZ >= 0f ? -0.32f : -0.32f);
        }

        float yPlane = dyGround + 0.010f;
        int shadowLight = LightTexture.pack(Math.max(blkLvl, 4), skyLvl);

        ps.pushPose();
        ps.translate(0.0f, yPlane, 0.0f);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));

        int layerIdx = 0;
        for (StandeeLayers.Layer l : layers) {
            if (l.emissive()) {
                continue;
            }
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(l.texture());
            if (filtered.put(tex, Boolean.TRUE) == null) {
                tex.setFilter(true, false);
            }
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(l.texture()));

            float flapAngle = 0f;
            float px = 0f, py = 0f;
            if (l.flap() != null) {
                px = (l.pivotU() - 0.5f) * width;
                py = (1f - l.pivotV()) * height;
                float amp = l.flap().degrees() * (1f + 0.35f * walkSpd);
                flapAngle = amp * (Mth.sin(time * Mth.TWO_PI * l.flap().hertz() + l.flap().phase()) + (drooping ? 1.4f : 0f));
            }
            // Tiny vertical stack offset so overlapping shadow layers don't z-fight each other
            float yLayer = layerIdx * 0.0008f;
            float zOff = l.z() * AvatarConfig.depth() * 0.5f;
            float baseA = shadowAlpha * 0.85f;
            float tipA = shadowAlpha * 0.18f;

            shadowQuad(ps.last(), vc, width, height, yLayer, zOff, localShX, localShZ, px, py, flapAngle, baseA, tipA, shadowLight);
            layerIdx++;
        }
        ps.popPose();
    }

    /** Projects one upright layer quad (with optional pivot rotation) onto the horizontal ground plane. */
    private static void shadowQuad(PoseStack.Pose pose, VertexConsumer vc, float width, float height,
                                   float yPlane, float zLayer, float shX, float shZ,
                                   float pivotX, float pivotY, float flapDeg,
                                   float aBottom, float aTop, int light) {
        float hw = width * 0.5f;
        float midY = height * 0.45f;
        float aMid = Mth.lerp(0.45f, aBottom, aTop);
        shadowStrip(pose, vc, hw, 0f, midY, 1f, 0.55f, aBottom, aMid, yPlane, zLayer, shX, shZ, pivotX, pivotY, flapDeg, light);
        shadowStrip(pose, vc, hw, midY, height, 0.55f, 0f, aMid, aTop, yPlane, zLayer, shX, shZ, pivotX, pivotY, flapDeg, light);
    }

    private static void shadowStrip(PoseStack.Pose pose, VertexConsumer vc, float hw, float y0, float y1,
                                    float v0, float v1, float a0, float a1,
                                    float yPlane, float zLayer, float shX, float shZ,
                                    float px, float py, float flapDeg, int light) {
        shadowVertex(pose, vc, -hw, y0, 0f, v0, a0, yPlane, zLayer, shX, shZ, px, py, flapDeg, light);
        shadowVertex(pose, vc, hw, y0, 1f, v0, a0, yPlane, zLayer, shX, shZ, px, py, flapDeg, light);
        shadowVertex(pose, vc, hw, y1, 1f, v1, a1, yPlane, zLayer, shX, shZ, px, py, flapDeg, light);
        shadowVertex(pose, vc, -hw, y1, 0f, v1, a1, yPlane, zLayer, shX, shZ, px, py, flapDeg, light);
    }

    private static void shadowVertex(PoseStack.Pose pose, VertexConsumer vc, float vx, float vy, float u, float v, float a,
                                     float yPlane, float zLayer, float shX, float shZ,
                                     float px, float py, float flapDeg, int light) {
        float rx = vx;
        float ry = vy;
        if (flapDeg != 0f) {
            float rad = (float) Math.toRadians(flapDeg);
            float c = Mth.cos(rad);
            float s = Mth.sin(rad);
            float dx = vx - px;
            float dy = vy - py;
            rx = px + dx * c - dy * s;
            ry = py + dx * s + dy * c;
        }
        float gx = rx + ry * shX;
        float gz = zLayer + ry * shZ;
        int alphaByte = (int) (Mth.clamp(a, 0f, 0.75f) * 255f);
        vc.addVertex(pose, gx, yPlane, gz)
                .setColor(8, 8, 14, alphaByte)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 1f, 0f);
    }

    private static double findGroundY(Level level, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y + 0.2, z);
        for (int i = 0; i <= 3; i++) {
            BlockPos p = base.below(i);
            BlockState state = level.getBlockState(p);
            VoxelShape shape = state.getCollisionShape(level, p);
            if (!shape.isEmpty()) {
                return p.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return Math.floor(y);
    }

    private void ghost(PoseStack ps, MultiBufferSource buf, List<StandeeLayers.Layer> layers, double dx, double dy, double dz, float yaw, float roll,
                       float pitch, float y, float scale, float width, float height, float time, float walkSpd, int footLight, int headLight, int overlay, float alpha) {
        ps.pushPose();
        ps.translate(dx, dy + y, dz);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.scale(scale, scale, 1f);
        drawStack(ps, buf, layers, width, height, time, walkSpd, footLight, headLight, overlay, alpha, 0.85f, 0.55f, 1f, false);
        ps.popPose();
    }

    /** Draws all layers in the active directional view, far to near, with curved 3D normals and environmental lighting. */
    private void drawStack(PoseStack ps, MultiBufferSource buf, List<StandeeLayers.Layer> layers, float width, float height, float time,
                           float walkSpd, int footLight, int headLight, int overlay, float alpha, float r, float g, float b, boolean drooping) {
        int index = 0;
        for (StandeeLayers.Layer l : layers) {
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(l.texture());
            if (filtered.put(tex, Boolean.TRUE) == null) {
                tex.setFilter(true, false);
            }
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(l.texture()));
            ps.pushPose();
            ps.translate(0f, 0f, l.z() * AvatarConfig.depth());
            if (l.flap() != null) {
                float px = (l.pivotU() - 0.5f) * width;
                float py = (1f - l.pivotV()) * height;
                float amp = l.flap().degrees() * (1f + 0.35f * walkSpd);
                float angle = amp * (Mth.sin(time * Mth.TWO_PI * l.flap().hertz() + l.flap().phase()) + (drooping ? 1.4f : 0f));
                ps.translate(px, py, 0f);
                ps.mulPose(Axis.ZP.rotationDegrees(angle));
                ps.translate(-px, -py, 0f);
            }
            if (l.breathe() != 0f) {
                float pivotY = height * 0.62f;
                ps.translate(0f, pivotY, 0f);
                ps.scale(1f, 1f + l.breathe() * Mth.sin(time * Mth.TWO_PI * 0.22f), 1f);
                ps.translate(0f, -pivotY, 0f);
            }
            float cr = r, cg = g, cb = b;
            if (AvatarConfig.debugTint()) {
                float[] d = DEBUG_COLORS[index % DEBUG_COLORS.length];
                cr *= d[0];
                cg *= d[1];
                cb *= d[2];
            }
            float hem = l.hemFade() ? AvatarConfig.hemFade() : 0f;
            int lowL = l.emissive() ? LightTexture.FULL_BRIGHT : footLight;
            int highL = l.emissive() ? LightTexture.FULL_BRIGHT : headLight;
            boolean shadeEnv = AvatarConfig.envShading() && !l.emissive();
            curvedQuad(ps.last(), vc, width, height, hem, cr, cg, cb, alpha, lowL, highL, overlay, shadeEnv);
            ps.popPose();
            index++;
        }
    }

    /**
     * Subdivides the layer quad horizontally and vertically with pseudo-cylindrical 3D normals and ground-contact
     * ambient occlusion so Minecraft's directional sun/sky/block lighting shades her roundly in the environment.
     */
    private static void curvedQuad(PoseStack.Pose pose, VertexConsumer vc, float width, float height, float hem,
                                   float r, float g, float b, float a, int footLight, int headLight, int overlay, boolean shadeEnv) {
        float yContact = height * 0.14f;
        float aoBottom = shadeEnv ? 0.86f : 1.0f;
        if (hem <= 0f) {
            curvedBand(pose, vc, width, 0f, yContact, 1f, 0.86f, a, a, r * aoBottom, g * aoBottom, b * aoBottom, r, g, b, footLight, footLight, overlay, shadeEnv);
            curvedBand(pose, vc, width, yContact, height, 0.86f, 0f, a, a, r, g, b, r, g, b, footLight, headLight, overlay, shadeEnv);
        } else {
            float yb = height * hem;
            curvedBand(pose, vc, width, yb, height, 1f - hem, 0f, a, a, r, g, b, r, g, b, footLight, headLight, overlay, shadeEnv);
            curvedBand(pose, vc, width, 0f, yb, 1f, 1f - hem, 0f, a, r * aoBottom, g * aoBottom, b * aoBottom, r, g, b, footLight, footLight, overlay, shadeEnv);
        }
    }

    private static void curvedBand(PoseStack.Pose pose, VertexConsumer vc, float width, float y0, float y1, float vBottom, float vTop,
                                   float aBottom, float aTop, float r0, float g0, float b0, float r1, float g1, float b1,
                                   int lightBottom, int lightTop, int overlay, boolean curvedNormals) {
        int cols = curvedNormals ? CURVE_COLS : 1;
        float halfW = width * 0.5f;
        float ny0 = curvedNormals ? (0.5f - vBottom) * 0.35f + 0.12f : 0f;
        float ny1 = curvedNormals ? (0.5f - vTop) * 0.35f + 0.12f : 0f;

        for (int i = 0; i < cols; i++) {
            float u0 = (float) i / cols;
            float u1 = (float) (i + 1) / cols;
            float x0 = -halfW + u0 * width;
            float x1 = -halfW + u1 * width;

            // Gentle cylindrical Z bulge (up to +0.018 blocks at center) + curved normals across X
            float zBulge0 = curvedNormals ? (1f - 4f * (u0 - 0.5f) * (u0 - 0.5f)) * 0.016f : 0f;
            float zBulge1 = curvedNormals ? (1f - 4f * (u1 - 0.5f) * (u1 - 0.5f)) * 0.016f : 0f;

            float nx0 = curvedNormals ? (u0 - 0.5f) * 1.05f : 0f;
            float nx1 = curvedNormals ? (u1 - 0.5f) * 1.05f : 0f;

            // Subtle side rim shading so cylindrical volume reads even under flat ambient light
            float s0 = curvedNormals ? (1f - 0.07f * Math.abs(u0 - 0.5f) * 2f) : 1f;
            float s1 = curvedNormals ? (1f - 0.07f * Math.abs(u1 - 0.5f) * 2f) : 1f;

            vertex(pose, vc, x0, y0, zBulge0, u0, vBottom, r0 * s0, g0 * s0, b0 * s0, aBottom, lightBottom, overlay, nx0, ny0);
            vertex(pose, vc, x1, y0, zBulge1, u1, vBottom, r0 * s1, g0 * s1, b0 * s1, aBottom, lightBottom, overlay, nx1, ny0);
            vertex(pose, vc, x1, y1, zBulge1, u1, vTop, r1 * s1, g1 * s1, b1 * s1, aTop, lightTop, overlay, nx1, ny1);
            vertex(pose, vc, x0, y1, zBulge0, u0, vTop, r1 * s0, g1 * s0, b1 * s0, aTop, lightTop, overlay, nx0, ny1);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v,
                               float r, float g, float b, float a, int light, int overlay, float nx, float ny) {
        float nz = (float) Math.sqrt(Math.max(0.15f, 1f - nx * nx - ny * ny));
        float invLen = Mth.invSqrt(nx * nx + ny * ny + nz * nz);
        vc.addVertex(pose, x, y, z)
                .setColor(
                        (int) (Mth.clamp(r, 0f, 1f) * 255f),
                        (int) (Mth.clamp(g, 0f, 1f) * 255f),
                        (int) (Mth.clamp(b, 0f, 1f) * 255f),
                        (int) (Mth.clamp(a, 0f, 1f) * 255f)
                )
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx * invLen, ny * invLen, nz * invLen);
    }

    // ------------------------------------------------------------------------------------------------ per-tick

    private void onTick(HelperEntity e, State st) {
        double[] now = {e.getX(), e.getY(), e.getZ()};
        double[] prev = st.history.peekFirst();
        if (prev != null) {
            double dx = now[0] - prev[0];
            double dz = now[2] - prev[2];
            double dy = now[1] - prev[1];
            st.vx = dx;
            st.vz = dz;
            st.speed = Math.hypot(dx, dz);
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 2.5) {
                st.teleportFrom = prev;
                st.teleportTicks = 8;
                st.history.clear();
                st.speed = 0;
            }
        }
        if (st.speed < 0.01 && !e.swinging) {
            st.idleTicks++;
        } else {
            st.idleTicks = 0;
        }
        if (st.teleportTicks > 0 && --st.teleportTicks == 0) {
            st.teleportFrom = null;
        }
        st.history.addFirst(now);
        while (st.history.size() > HISTORY) {
            st.history.removeLast();
        }
        if (AvatarConfig.petals()) {
            petals(e, st);
        }
    }

    private void petals(HelperEntity e, State st) {
        RandomSource r = e.getRandom();
        double chance = e.isDowned() ? 0.6 : st.speed > 0.05 ? 0.45 : 0.12;
        if (r.nextDouble() < chance) {
            double ox = (r.nextDouble() - 0.5) * 0.9;
            double oz = (r.nextDouble() - 0.5) * 0.9;
            double oy = AvatarConfig.hover() + 0.1 + r.nextDouble() * 0.9;
            e.level().addParticle(ParticleTypes.CHERRY_LEAVES, e.getX() + ox, e.getY() + oy, e.getZ() + oz, 0.0, 0.0, 0.0);
        }
    }
}
