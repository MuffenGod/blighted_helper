package com.blighteddreams.helper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.client.render.CuriosLayer;

/**
 * Kept in its own class so the renderers never link against Curios when it is absent. Her model is not a HumanoidModel,
 * so a curio renderer that assumes one may throw: the layer then switches itself off (her curios keep working, they are
 * just not drawn) instead of crashing the game.
 */
public final class CuriosLayerAdder {
    private CuriosLayerAdder() {}

    public static void add(LivingEntityRenderer<?, ?> renderer) {
        addTyped(renderer);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addTyped(LivingEntityRenderer renderer) {
        renderer.addLayer(new SafeLayer(renderer, new CuriosLayer(renderer)));
    }

    private static final class SafeLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
        private final CuriosLayer<T, M> inner;
        private boolean broken;

        SafeLayer(RenderLayerParent<T, M> parent, CuriosLayer<T, M> inner) {
            super(parent);
            this.inner = inner;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limbSwing, float limbSwingAmount,
                           float partial, float age, float headYaw, float headPitch) {
            if (broken) return;
            ps.pushPose();
            try {
                inner.render(ps, buf, light, e, limbSwing, limbSwingAmount, partial, age, headYaw, headPitch);
            } catch (Throwable t) {
                broken = true;
                LoggerFactory.getLogger("blighted_enhancements/curios").warn("Curios drawing on the companion failed, turned off: {}", t.toString());
            } finally {
                ps.popPose();
            }
        }
    }
}
