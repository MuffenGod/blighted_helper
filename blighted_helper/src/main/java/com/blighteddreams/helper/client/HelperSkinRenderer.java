package com.blighteddreams.helper.client;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.AvatarConfig;
import com.blighteddreams.helper.HelperEntity;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders Emily using our custom Grimoire-of-Gaia-4-based articulated female half-dragon model
 * ({@link HelperGaiaModel}) and 128x64 texture ({@code textures/entity/emily_gaia.png}),
 * with emissive emerald eyes & gems and held-weapon rendering.
 */
public class HelperSkinRenderer extends MobRenderer<HelperEntity, HelperGaiaModel> {
    private static final Logger LOGGER = LoggerFactory.getLogger("blighted_enhancements/skin");
    private static final ResourceLocation DEFAULT_SKIN =
            ResourceLocation.fromNamespaceAndPath(BlightedHelper.MOD_ID, "textures/entity/emily_gaia.png");
    private static final ResourceLocation GLOW_SKIN =
            ResourceLocation.fromNamespaceAndPath(BlightedHelper.MOD_ID, "textures/entity/emily_gaia_glow.png");
    private static final String SKIN_OVERRIDE_FILE = "blighted_enhancements-emily-gaia.png";

    /** bumped by /helperavatar reloadparts so every renderer rebuilds its larger-texture model */
    private static volatile int xlGeneration = 0;
    private HelperGaiaModel baseModel;
    private HelperGaiaModel xlModel;
    private int xlBuilt = -1;

    private static ResourceLocation customSkinLoc;
    private static boolean checkedCustomSkin;

    public HelperSkinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HelperGaiaModel(HelperGaiaModel.createBodyLayer().bakeRoot()), 0.4F);
        this.baseModel = this.model;
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        this.addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getItemInHandRenderer()));
        this.addLayer(new EmissiveGlowLayer(this));
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
            CuriosLayerAdder.add(this);
        }
    }

    public static void reloadVariantParts() {
        HelperVariantParts.reload();
        HelperMotionData.reload();
        xlGeneration++;
    }

    /** Jester, infernal and holy use the 256x128 copy of the Gaia model (same body, plus extra parts); Gaia keeps her own. */
    private HelperGaiaModel modelFor(HelperEntity entity) {
        if (!entity.getVariant().usesXlModel() || customSkinLoc != null) return this.baseModel;   // a custom 128x64 skin file keeps the plain model
        if (this.xlModel == null || this.xlBuilt != xlGeneration) {
            this.xlModel = new HelperGaiaModel(HelperGaiaModel.createBodyLayerXl().bakeRoot());
            this.xlBuilt = xlGeneration;
        }
        return this.xlModel;
    }

    public static void invalidateSkinCache() {
        checkedCustomSkin = false;
        customSkinLoc = null;
    }

    @Override
    public ResourceLocation getTextureLocation(HelperEntity entity) {
        if (!checkedCustomSkin) {
            checkedCustomSkin = true;
            Path override = FMLPaths.CONFIGDIR.get().resolve(SKIN_OVERRIDE_FILE);
            if (Files.isRegularFile(override)) {
                try (InputStream in = Files.newInputStream(override)) {
                    NativeImage img = NativeImage.read(in);
                    DynamicTexture dyn = new DynamicTexture(img);
                    customSkinLoc = Minecraft.getInstance().getTextureManager()
                            .register("blighted_emily_custom_gaia_skin", dyn);
                    LOGGER.info("Loaded custom Emily Gaia skin from {}", override);
                } catch (Exception e) {
                    LOGGER.warn("Could not load custom Gaia skin {}: {}", override, e.toString());
                }
            }
        }
        return customSkinLoc != null ? customSkinLoc : entity.getVariant().texture;
    }

    @Override
    public void render(HelperEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = modelFor(entity);
        poseStack.pushPose();
        if (entity.isDowned()) {
            poseStack.translate(0.0D, -0.28D, 0.0D);
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();
        SlashTrails.render(entity, partialTicks, poseStack, buffer);

        if (entity.animCastStyle != HelperCasts.STYLE_NONE && !entity.isDowned()) {
            float pCast = (entity.tickCount + partialTicks - entity.animCastStartTick) / (float) Math.max(6, entity.animCastTicks);
            if (pCast >= 0 && pCast < 1.35F) {
                HelperCasts.CastPose c = HelperCasts.get(entity.animCastStyle, entity.animCastVariant);
                if (c.groundRing()) {
                    float alpha = pCast < 0.65F ? (pCast / 0.65F) : (1.0F - (pCast - 0.65F) / 0.7F);
                    float rad = 1.0F + (pCast < 0.65F ? pCast * 0.6F : 0.39F + (pCast - 0.65F) * 1.8F);
                    SpellSchool sc = SpellSchool.byId(entity.animCastSchool);
                    if (sc == SpellSchool.NONE) sc = SpellSchool.byId(entity.defaultSchoolFor(entity.animCastStyle));
                    SlashTrails.renderCastRing(poseStack, buffer, rad, alpha * 0.9F, sc.r, sc.g, sc.b);
                }
            }
        }
    }

    /**
     * Full-bright emissive overlay for emerald/ruby/sapphire/amethyst eyes, gems, and accents.
     */
    private static class EmissiveGlowLayer extends RenderLayer<HelperEntity, HelperGaiaModel> {
        public EmissiveGlowLayer(RenderLayerParent<HelperEntity, HelperGaiaModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HelperEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!AvatarConfig.envShading() || customSkinLoc != null) {
                return;
            }
            ResourceLocation glow = entity.getVariant().glowTexture;
            VertexConsumer vc = buffer.getBuffer(RenderType.eyes(glow));
            // fx.glow from the motion layer: 1 = as painted, < 1 dims, > 1 draws the glow layer a second time at the remainder (the layer is additive)
            float k = Math.max(0.0F, this.getParentModel().glowK);
            int first = Math.min(255, Math.round(255.0F * Math.min(1.0F, k)));
            this.getParentModel().renderToBuffer(poseStack, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF000000 | (first << 16) | (first << 8) | first);
            if (k > 1.0F) {
                int extra = Math.min(255, Math.round(255.0F * Math.min(1.0F, k - 1.0F)));
                this.getParentModel().renderToBuffer(poseStack, buffer.getBuffer(RenderType.eyes(glow)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF000000 | (extra << 16) | (extra << 8) | extra);
            }
        }
    }
}
