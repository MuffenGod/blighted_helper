package com.blighteddreams.helper.client;

import com.blighteddreams.helper.HelperEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class HelperGeoRenderer extends GeoEntityRenderer<HelperEntity> {
    public HelperGeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HelperGeoModel());
        this.shadowRadius = 0.5F;
        // weapons are drawn at empty locator bones on her gloves ("hand_item_r" and "hand_item_l")
        // unrendered while casting so Emily uses her bare hands for magic
        addRenderLayer(new BlockAndItemGeoLayer<>(this,
                (bone, entity) -> {
                    if (entity.isCasting()) return ItemStack.EMPTY;
                    if ("hand_item_r".equals(bone.getName())) return entity.getMainHandItem();
                    if ("hand_item_l".equals(bone.getName())) return entity.getOffhandItem();
                    return ItemStack.EMPTY;
                },
                (bone, entity) -> null));
        // emissive gems, eyes and inlay come from textures/entity/emily_glowmask.png
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void scaleModelForRender(float width, float height, com.mojang.blaze3d.vertex.PoseStack poseStack, HelperEntity animatable, software.bernie.geckolib.cache.object.BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float s = animatable.getScale();
        super.scaleModelForRender(width * s, height * s, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }

    @Override
    public float getShadowRadius(HelperEntity entity) {
        return 0.5F * entity.getScale();
    }

    @Override
    public void render(HelperEntity entity, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        SlashTrails.render(entity, partialTick, poseStack, bufferSource);

        if (entity.animCastStyle != HelperCasts.STYLE_NONE && !entity.isDowned()) {
            float pCast = (entity.tickCount + partialTick - entity.animCastStartTick) / (float) Math.max(6, entity.animCastTicks);
            if (pCast >= 0 && pCast < 1.35F) {
                HelperCasts.CastPose c = HelperCasts.get(entity.animCastStyle, entity.animCastVariant);
                if (c.groundRing()) {
                    float alpha = pCast < 0.65F ? (pCast / 0.65F) : (1.0F - (pCast - 0.65F) / 0.7F);
                    float rad = 1.0F + (pCast < 0.65F ? pCast * 0.6F : 0.39F + (pCast - 0.65F) * 1.8F);
                    SpellSchool sc = SpellSchool.byId(entity.animCastSchool);
                    if (sc == SpellSchool.NONE) sc = SpellSchool.byId(entity.defaultSchoolFor(entity.animCastStyle));
                    SlashTrails.renderCastRing(poseStack, bufferSource, rad, alpha * 0.9F, sc.r, sc.g, sc.b);
                }
            }
        }
    }
}
