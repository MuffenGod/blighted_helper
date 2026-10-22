package com.blighteddreams.helper.client;

import com.blighteddreams.helper.AvatarConfig;
import com.blighteddreams.helper.HelperEntity;
import com.blighteddreams.helper.HelperVariant;
import com.blighteddreams.helper.WeaponKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Grimoire-of-Gaia-4-inspired articulated female half-dragon model for Emily (128x64 UV layout).
 * Combines the proportioned female hourglass rig of Grimoire of Gaia's SuccubusModel & ValkyrieModel
 * with Emily's custom features:
 *  - 6x6x6 head + 7x7x7 hair crown/circlet + blinking headeyes + cascading 3D back hair (hair1, hair2)
 *  - Stepped curved obsidian-and-gold dragon horns (hornbase, horn1)
 *  - Articulated torso (bodybottom 6x3x3 + hip belt wrap, bodymiddle 4x3x2 waist, bodytop_female 5x6x3 + angled chest)
 *  - Slender 2x6x2 upper/lower arms with off-shoulder sleeves & opera gloves
 *  - Flowing tiered split gown (skirt1, skirt2, skirt3 + right-leg panel) exposing her bare left leg through the slit
 *  - Crimson-and-obsidian bat wings (leftwing, rightwing) and 3-segment dragon tail (tail1, tail2, tail3)
 */
public class HelperGaiaModel extends EntityModel<HelperEntity> implements ArmedModel, HeadedModel {
    private final ModelPart root;
    private final ModelPart bodybottom;
    private final ModelPart bodymiddle;
    private final ModelPart bodytop;
    private final ModelPart bodytopFemale;
    private final ModelPart chest;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart headeyes;
    private final ModelPart hornbase;
    private final ModelPart hair1;
    private final ModelPart hair2;
    private final ModelPart rightarm;
    private final ModelPart rightarmlower;
    private final ModelPart leftarm;
    private final ModelPart leftarmlower;
    private final ModelPart rightwing;
    private final ModelPart leftwing;
    private final ModelPart skirt1;
    private final ModelPart skirt2;
    private final ModelPart skirt3;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart tail3;
    private final ModelPart rightleg;
    private final ModelPart rightleglower;
    private final ModelPart leftleg;
    private final ModelPart leftleglower;
    private final ModelPart halo;
    private record Extra(HelperVariantParts.Spec spec, ModelPart part, int index) {}
    /** variant being posed this frame, and how hard a jester flourish is kicking her hat (0..1) */
    private HelperVariant curVariant = HelperVariant.GAIA;
    private float hatKick;
    /** Extra parts of the larger-texture variants (empty on the plain Gaia model). */
    private final java.util.List<Extra> extras = new java.util.ArrayList<>();
    private final java.util.Map<String, ModelPart> stdCache = new java.util.HashMap<>();
    /** per-variant motion rigs (standard + extra parts, compiled tracks, spring binding) and the glow intensity the motion layer asked for this frame */
    private final java.util.Map<String, HelperMotionRig> motionRigs = new java.util.HashMap<>();
    private final HelperMotion.In motionIn = new HelperMotion.In();
    private ModelPart[] stdArray;
    public float glowK = 1.0F;
    /** the body layer's scratch pose (standard parts), their rest pose and input */
    private final float[] bodyPose = new float[25 * HelperBody.STRIDE];
    private final float[] bodyRest = new float[25 * HelperBody.STRIDE];
    private final HelperBody.In bodyIn = new HelperBody.In();
    private boolean casting;
    private final java.util.List<ModelPart> allParts = new java.util.ArrayList<>();
    /** Smoothed 0..1 "is talking" so the speech gesture eases in and out instead of switching on. */
    private float talkAmt;
    private float talkLastAge;

    public HelperGaiaModel(ModelPart bakedRoot) {
        this.root = bakedRoot.getChild("emily_gaia");
        this.bodybottom = this.root.getChild("bodybottom");
        this.bodymiddle = this.bodybottom.getChild("bodymiddle");
        this.bodytop = this.bodymiddle.getChild("bodytop");
        this.bodytopFemale = this.bodytop.getChild("bodytop_female");
        this.chest = this.bodytopFemale.getChild("chest");
        this.neck = this.bodytop.getChild("neck");
        this.head = this.neck.getChild("head");
        this.headeyes = this.head.getChild("headeyes");
        this.hornbase = this.head.getChild("hornbase");
        this.halo = this.head.getChild("halo");
        this.hair1 = this.neck.getChild("hair1");
        this.hair2 = this.hair1.getChild("hair2");
        this.rightarm = this.bodytop.getChild("rightarm");
        this.rightarmlower = this.rightarm.getChild("rightarmlower");
        this.leftarm = this.bodytop.getChild("leftarm");
        this.leftarmlower = this.leftarm.getChild("leftarmlower");
        this.rightwing = this.bodytop.getChild("rightwing");
        this.leftwing = this.bodytop.getChild("leftwing");
        this.skirt1 = this.bodybottom.getChild("skirt1");
        this.skirt2 = this.skirt1.getChild("skirt2");
        this.skirt3 = this.skirt2.getChild("skirt3");
        this.tail1 = this.bodybottom.getChild("tail1");
        this.tail2 = this.tail1.getChild("tail2");
        this.tail3 = this.tail2.getChild("tail3");
        this.rightleg = this.root.getChild("rightleg");
        this.rightleglower = this.rightleg.getChild("rightleglower");
        this.leftleg = this.root.getChild("leftleg");
        this.leftleglower = this.leftleg.getChild("leftleglower");
        this.root.getAllParts().forEach(this.allParts::add);
        this.buildExtras();
        this.stdArray = new ModelPart[]{this.root, this.bodybottom, this.bodymiddle, this.bodytop, this.bodytopFemale, this.neck, this.head, this.hair1, this.hair2,
                this.rightarm, this.rightarmlower, this.leftarm, this.leftarmlower, this.rightwing, this.leftwing, this.skirt1, this.skirt2, this.skirt3,
                this.tail1, this.tail2, this.tail3, this.rightleg, this.rightleglower, this.leftleg, this.leftleglower};
        for (int i = 0; i < this.stdArray.length; i++) {
            PartPose ip = this.stdArray[i].getInitialPose();
            int o = i * HelperBody.STRIDE;
            this.bodyRest[o] = ip.x; this.bodyRest[o + 1] = ip.y; this.bodyRest[o + 2] = ip.z;
            this.bodyRest[o + 3] = ip.xRot; this.bodyRest[o + 4] = ip.yRot; this.bodyRest[o + 5] = ip.zRot;
        }
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(buildMesh(), 128, 64);
    }

    /** Same body, a 256x128 texture (the Gaia layout stays in the top-left 128x64) and the extra variant parts. */
    public static LayerDefinition createBodyLayerXl() {
        MeshDefinition mesh = buildMesh();
        addExtraDefinitions(mesh);
        HelperVariantParts.Data d = HelperVariantParts.get();
        return LayerDefinition.create(mesh, d.texW, d.texH);
    }

    private static MeshDefinition buildMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition meshRoot = mesh.getRoot();

        PartDefinition root = meshRoot.addOrReplaceChild("emily_gaia",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // 1. Pelvis / Hips (bodybottom) + Ornate Gold Hip Belt Wrap
        PartDefinition bodybottom = root.addOrReplaceChild("bodybottom",
                CubeListBuilder.create()
                        .texOffs(0, 30).addBox(-3.0F, -1.5F, -1.5F, 6.0F, 3.0F, 3.0F)
                        .texOffs(64, 24).addBox(-3.5F, -1.0F, -2.0F, 7.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -13.5F, -0.5F, 0.0873F, 0.0F, 0.0F));

        // 2. Slim Hourglass Waist (bodymiddle)
        PartDefinition bodymiddle = bodybottom.addOrReplaceChild("bodymiddle",
                CubeListBuilder.create()
                        .texOffs(0, 25).addBox(-2.0F, -2.5F, -1.5F, 4.0F, 3.0F, 2.0F)
                        .texOffs(0, 25).addBox(-0.5F, -2.0F, -1.6F, 1.0F, 2.0F, 0.0F),
                PartPose.offsetAndRotation(0.0F, -1.5F, 0.5F, -0.0873F, 0.0F, 0.0F));

        // 3. Upper Torso (bodytop -> bodytop_female + chest)
        PartDefinition bodytop = bodymiddle.addOrReplaceChild("bodytop",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, -2.0F, 0.4F, -0.0873F, 0.0F, 0.0F));

        PartDefinition bodytopFemale = bodytop.addOrReplaceChild("bodytop_female",
                CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-2.5F, -6.0F, -3.0F, 5.0F, 6.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 0.6F));

        bodytopFemale.addOrReplaceChild("chest",
                CubeListBuilder.create()
                        .texOffs(0, 36).addBox(-2.3F, -0.45F, -2.0F, 2.0F, 2.0F, 2.0F)
                        .texOffs(0, 36).mirror().addBox(0.3F, -0.45F, -2.0F, 2.0F, 2.0F, 2.0F).mirror(false),
                PartPose.offsetAndRotation(0.0F, -5.3F, -2.5F, 0.7854F, 0.0F, 0.0F));

        // 4. Neck & Head (6x6x6 head + 7x7x7 hair crown/circlet + shoulder locks)
        PartDefinition neck = bodytop.addOrReplaceChild("neck",
                CubeListBuilder.create()
                        .texOffs(0, 12).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -6.0F, -0.9F, 0.0873F, 0.0F, 0.0F));

        PartDefinition head = neck.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F)
                        .texOffs(36, 0).addBox(-3.5F, -6.5F, -3.5F, 7.0F, 7.0F, 7.0F)
                        .texOffs(48, 37).addBox(-3.4F, -0.5F, -2.5F, 1.0F, 5.0F, 1.0F)
                        .texOffs(48, 37).mirror().addBox(2.4F, -0.5F, -2.5F, 1.0F, 5.0F, 1.0F).mirror(false),
                PartPose.offset(0.0F, 0.0F, 0.5F));

        head.addOrReplaceChild("headeyes",
                CubeListBuilder.create()
                        .texOffs(24, 0).addBox(-3.0F, -6.0F, -3.1F, 6.0F, 6.0F, 0.0F),
                PartPose.ZERO);

        head.addOrReplaceChild("halo",
                CubeListBuilder.create()
                        .texOffs(36, 50).addBox(-3.5F, -9.5F, -3.5F, 7.0F, 1.0F, 7.0F),
                PartPose.ZERO);

        // 5. Stepped Curved Obsidian & Gold Dragon Horns
        PartDefinition hornbase = head.addOrReplaceChild("hornbase",
                CubeListBuilder.create()
                        .texOffs(36, 37).addBox(2.0F, -7.0F, -1.5F, 2.0F, 2.0F, 2.0F)
                        .texOffs(36, 37).addBox(-4.0F, -7.0F, -1.5F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2618F, 0.0F, 0.0F));

        hornbase.addOrReplaceChild("horn1",
                CubeListBuilder.create()
                        .texOffs(36, 41).addBox(2.8F, -9.0F, -1.0F, 2.0F, 2.0F, 3.0F)
                        .texOffs(36, 41).addBox(-4.8F, -9.0F, -1.0F, 2.0F, 2.0F, 3.0F)
                        .texOffs(36, 46).addBox(3.2F, -10.8F, 0.0F, 2.0F, 2.0F, 2.0F)
                        .texOffs(36, 46).addBox(-5.2F, -10.8F, 0.0F, 2.0F, 2.0F, 2.0F)
                        .texOffs(36, 50).addBox(3.5F, -11.8F, 0.5F, 1.0F, 1.0F, 1.0F)
                        .texOffs(36, 50).addBox(-4.5F, -11.8F, 0.5F, 1.0F, 1.0F, 1.0F),
                PartPose.ZERO);

        // 6. Voluminous Cascading 3D Back Hair (hair1 -> hair2)
        PartDefinition hair1 = neck.addOrReplaceChild("hair1",
                CubeListBuilder.create()
                        .texOffs(36, 14).addBox(-4.0F, -5.5F, 1.0F, 8.0F, 8.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, 0.1745F, 0.0F, 0.0F));

        hair1.addOrReplaceChild("hair2",
                CubeListBuilder.create()
                        .texOffs(36, 25).addBox(-4.5F, -1.0F, 0.0F, 9.0F, 9.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 1.5F, 0.0873F, 0.0F, 0.0F));

        // 7. Slender Articulated Arms + Off-Shoulder Sleeves + Long Black Opera Gloves
        PartDefinition rightarm = bodytop.addOrReplaceChild("rightarm",
                CubeListBuilder.create()
                        .texOffs(16, 12).addBox(-2.0F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                        .texOffs(64, 0).addBox(-2.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(-2.5F, -4.5F, -0.9F, 0.0873F, 0.0F, 0.1745F));

        rightarm.addOrReplaceChild("rightarmlower",
                CubeListBuilder.create()
                        .texOffs(16, 18).addBox(-1.005F, 0.0F, -2.0F, 2.0F, 6.0F, 2.0F)
                        .texOffs(64, 6).addBox(-1.5F, 0.5F, -2.5F, 2.0F, 6.0F, 3.0F),
                PartPose.offset(-1.0F, 5.0F, 1.0F));

        PartDefinition leftarm = bodytop.addOrReplaceChild("leftarm",
                CubeListBuilder.create()
                        .texOffs(16, 12).mirror().addBox(0.0F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F).mirror(false)
                        .texOffs(64, 0).mirror().addBox(-0.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F).mirror(false),
                PartPose.offsetAndRotation(2.5F, -4.5F, -0.9F, 0.0873F, 0.0F, -0.1745F));

        leftarm.addOrReplaceChild("leftarmlower",
                CubeListBuilder.create()
                        .texOffs(16, 18).mirror().addBox(-0.995F, 0.0F, -2.0F, 2.0F, 6.0F, 2.0F).mirror(false)
                        .texOffs(64, 6).mirror().addBox(-0.5F, 0.5F, -2.5F, 2.0F, 6.0F, 3.0F).mirror(false),
                PartPose.offset(1.0F, 5.0F, 1.0F));

        // 8. Crimson & Obsidian Bat Wings
        bodytop.addOrReplaceChild("rightwing",
                CubeListBuilder.create()
                        .texOffs(104, 42).addBox(0.0F, -1.0F, 0.0F, 0.0F, 14.0F, 8.0F),
                PartPose.offsetAndRotation(-1.5F, -5.0F, 1.6F, 0.4363F, -0.65F, 0.0F));

        bodytop.addOrReplaceChild("leftwing",
                CubeListBuilder.create()
                        .texOffs(104, 42).addBox(0.0F, -1.0F, 0.0F, 0.0F, 14.0F, 8.0F),
                PartPose.offsetAndRotation(1.5F, -5.0F, 1.6F, 0.4363F, 0.65F, 0.0F));

        // 9. Flowing Tiered Split Gown Train (skirt1 -> skirt2 -> skirt3)
        PartDefinition skirt1 = bodybottom.addOrReplaceChild("skirt1",
                CubeListBuilder.create()
                        .texOffs(96, 8).addBox(-4.0F, 0.0F, -2.2F, 8.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 0.5F, 0.0F, 0.14F, 0.0F, 0.0F));

        PartDefinition skirt2 = skirt1.addOrReplaceChild("skirt2",
                CubeListBuilder.create()
                        .texOffs(96, 16).addBox(-4.5F, 0.0F, -2.2F, 9.0F, 5.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 3.5F, 0.2F, 0.05F, 0.0F, 0.0F));

        skirt2.addOrReplaceChild("skirt3",
                CubeListBuilder.create()
                        .texOffs(96, 25).addBox(-5.0F, 0.0F, -2.2F, 10.0F, 5.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 4.5F, 0.2F, 0.05F, 0.0F, 0.0F));

        // 10. 3-Segment Articulated Dragon Tail (tail1 -> tail2 -> tail3)
        PartDefinition tail1 = bodybottom.addOrReplaceChild("tail1",
                CubeListBuilder.create()
                        .texOffs(64, 34).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 0.5F, 1.5F, 0.5236F, 0.0F, 0.0F));

        PartDefinition tail2 = tail1.addOrReplaceChild("tail2",
                CubeListBuilder.create()
                        .texOffs(64, 43).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 5.5F, 0.0F, 0.4363F, 0.0F, 0.0F));

        tail2.addOrReplaceChild("tail3",
                CubeListBuilder.create()
                        .texOffs(64, 51).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F)
                        .texOffs(70, 51).addBox(-1.5F, 4.0F, -0.5F, 3.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 5.5F, 0.0F, 0.3491F, 0.0F, 0.0F));

        // 11. Legs (Right leg has outer gown panel; Left leg is bare through the high slit)
        PartDefinition rightleg = root.addOrReplaceChild("rightleg",
                CubeListBuilder.create()
                        .texOffs(24, 12).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 7.0F, 3.0F)
                        .texOffs(76, 34).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F),
                PartPose.offset(-2.0F, -13.0F, 0.0F));

        rightleg.addOrReplaceChild("rightleglower",
                CubeListBuilder.create()
                        .texOffs(24, 22).addBox(-1.505F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F)
                        .texOffs(76, 45).addBox(-2.005F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        PartDefinition leftleg = root.addOrReplaceChild("leftleg",
                CubeListBuilder.create()
                        .texOffs(24, 12).mirror().addBox(-1.5F, -1.0F, -1.5F, 3.0F, 7.0F, 3.0F).mirror(false),
                PartPose.offset(2.0F, -13.0F, 0.0F));

        leftleg.addOrReplaceChild("leftleglower",
                CubeListBuilder.create()
                        .texOffs(24, 22).mirror().addBox(-1.495F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F).mirror(false),
                PartPose.offset(0.0F, 6.0F, 0.0F));

        return mesh;
    }


    // ---------------------------------------------------------------- extra variant parts (jester / infernal / holy)

    private static final java.util.Map<String, String> STD_PATHS = new java.util.HashMap<>();
    static {
        String bb = "bodybottom", bm = bb + "/bodymiddle", bt = bm + "/bodytop", nk = bt + "/neck", hd = nk + "/head";
        STD_PATHS.put("bodybottom", bb); STD_PATHS.put("bodymiddle", bm); STD_PATHS.put("bodytop", bt);
        STD_PATHS.put("bodytop_female", bt + "/bodytop_female"); STD_PATHS.put("chest", bt + "/bodytop_female/chest");
        STD_PATHS.put("neck", nk); STD_PATHS.put("head", hd); STD_PATHS.put("hornbase", hd + "/hornbase"); STD_PATHS.put("halo", hd + "/halo");
        STD_PATHS.put("hair1", nk + "/hair1"); STD_PATHS.put("hair2", nk + "/hair1/hair2");
        STD_PATHS.put("rightarm", bt + "/rightarm"); STD_PATHS.put("rightarmlower", bt + "/rightarm/rightarmlower");
        STD_PATHS.put("leftarm", bt + "/leftarm"); STD_PATHS.put("leftarmlower", bt + "/leftarm/leftarmlower");
        STD_PATHS.put("rightwing", bt + "/rightwing"); STD_PATHS.put("leftwing", bt + "/leftwing");
        STD_PATHS.put("skirt1", bb + "/skirt1"); STD_PATHS.put("skirt2", bb + "/skirt1/skirt2"); STD_PATHS.put("skirt3", bb + "/skirt1/skirt2/skirt3");
        STD_PATHS.put("tail1", bb + "/tail1"); STD_PATHS.put("tail2", bb + "/tail1/tail2"); STD_PATHS.put("tail3", bb + "/tail1/tail2/tail3");
        STD_PATHS.put("rightleg", "rightleg"); STD_PATHS.put("rightleglower", "rightleg/rightleglower");
        STD_PATHS.put("leftleg", "leftleg"); STD_PATHS.put("leftleglower", "leftleg/leftleglower");
    }

    private static String pathOf(String name, java.util.Map<String, HelperVariantParts.Spec> byName) {
        String std = STD_PATHS.get(name);
        if (std != null) return std;
        HelperVariantParts.Spec s = byName.get(name);
        if (s == null) return null;
        String parent = pathOf(s.parent, byName);
        return parent == null ? null : parent + "/" + s.name;
    }

    private static void addExtraDefinitions(MeshDefinition mesh) {
        HelperVariantParts.Data data = HelperVariantParts.get();
        java.util.Map<String, HelperVariantParts.Spec> byName = new java.util.HashMap<>();
        for (HelperVariantParts.Spec s : data.parts) byName.put(s.name, s);
        PartDefinition emily = mesh.getRoot().getChild("emily_gaia");
        for (HelperVariantParts.Spec s : data.parts) {
            String parentPath = pathOf(s.parent, byName);
            if (parentPath == null) continue;
            PartDefinition parent = emily;
            for (String seg : parentPath.split("/")) parent = parent.getChild(seg);
            CubeListBuilder cubes = CubeListBuilder.create();
            for (HelperVariantParts.Cube c : s.cubes) {
                cubes.texOffs((int) c.u, (int) c.v);
                if (c.mirror) cubes.mirror();
                cubes.addBox(c.x, c.y, c.z, c.w, c.h, c.d, new net.minecraft.client.model.geom.builders.CubeDeformation(c.inflate));
                if (c.mirror) cubes.mirror(false);
            }
            parent.addOrReplaceChild(s.name, cubes, PartPose.offsetAndRotation(s.px, s.py, s.pz, s.rx, s.ry, s.rz));
        }
    }

    private ModelPart std(String name) {
        if (this.stdCache.containsKey(name)) return this.stdCache.get(name);
        ModelPart found = null;
        String path = STD_PATHS.get(name);
        if (path != null) {
            ModelPart p = this.root;
            boolean ok = true;
            for (String seg : path.split("/")) {
                if (!p.hasChild(seg)) { ok = false; break; }
                p = p.getChild(seg);
            }
            if (ok) found = p;
        }
        this.stdCache.put(name, found);
        return found;
    }

    private void buildExtras() {
        HelperVariantParts.Data data = HelperVariantParts.get();
        java.util.Map<String, HelperVariantParts.Spec> byName = new java.util.HashMap<>();
        for (HelperVariantParts.Spec s : data.parts) byName.put(s.name, s);
        java.util.Map<String, Integer> chainIndex = new java.util.HashMap<>();
        for (HelperVariantParts.Spec s : data.parts) {
            String path = pathOf(s.name, byName);
            if (path == null) continue;
            ModelPart p = this.root;
            boolean ok = true;
            for (String seg : path.split("/")) {
                if (!p.hasChild(seg)) { ok = false; break; }
                p = p.getChild(seg);
            }
            if (!ok) continue;
            int idx = byName.containsKey(s.parent) ? chainIndex.getOrDefault(s.parent, -1) + 1 : 0;
            chainIndex.put(s.name, idx);
            this.extras.add(new Extra(s, p, idx));
        }
    }

    /** Shows this variant's extra parts, hides the standard ones it replaces, and runs follow / sway on them. */
    private void applyVariantParts(HelperEntity entity, float age, float speed) {
        if (this.extras.isEmpty()) return;
        String v = entity.getVariant().name().toLowerCase(java.util.Locale.ROOT);
        HelperVariantParts.Data pd = HelperVariantParts.get();
        java.util.Map<String, float[]> tw = pd.tweak.get(v);
        for (String n : pd.tweakNames) {            // small per-variant proportion changes on standard parts
            ModelPart p = std(n);
            if (p == null) continue;
            float[] s = tw == null ? null : tw.get(n);
            p.xScale = s == null ? 1.0F : s[0];
            p.yScale = s == null ? 1.0F : s[1];
            p.zScale = s == null ? 1.0F : s[2];
        }
        // one model instance draws every variant: un-hide whatever another variant hid (hair slab, gown skirt, ...) before this one hides its own.
        // hornbase, halo, wings and tail are re-decided every frame in computePose (dragonParts config), so they are left alone here.
        for (java.util.List<String> names : pd.hide.values()) {
            for (String n : names) {
                if (n.equals("hornbase") || n.equals("halo") || n.equals("leftwing") || n.equals("rightwing") || n.equals("tail1")) continue;
                ModelPart p = std(n);
                if (p != null) p.visible = true;
            }
        }
        java.util.List<String> hide = pd.hide.get(v);
        if (hide != null) {
            for (String n : hide) {
                ModelPart p = std(n);
                if (p != null) p.visible = false;
            }
        }
        boolean motion = HelperMotionData.has(v);   // the motion layer owns sway, scale and prop visibility
        for (Extra e : this.extras) {
            boolean on = e.spec().appliesTo(v);
            e.part().visible = on && !e.spec().hidden;
            e.part().xScale = e.part().yScale = e.part().zScale = e.spec().scale;
            if (!on) continue;
            if (e.spec().follow != null) {
                ModelPart f = std(e.spec().follow);
                if (f != null) {
                    e.part().x = f.x; e.part().y = f.y; e.part().z = f.z;
                    e.part().xRot = f.xRot; e.part().yRot = f.yRot; e.part().zRot = f.zRot;
                    if (e.spec().followVisible) e.part().visible = f.visible;   // e.g. the blink overlay
                }
            }
            float[] sw = motion ? null : e.spec().sway;
            if (sw != null) {
                float ph = age * sw[3] + sw[4] + e.index() * 0.55F;
                // chains swing harder when she moves, and the jester's hat gets kicked by her flourishes
                float amp = 1.0F + 2.0F * Math.min(1.0F, speed) + (this.curVariant == HelperVariant.JESTER ? 3.0F * this.hatKick : 0.0F);
                e.part().xRot += Mth.sin(ph) * sw[0] * amp;
                e.part().yRot += Mth.sin(ph + 1.3F) * sw[1] * amp;
                e.part().zRot += Mth.sin(ph + 2.1F) * sw[2] * amp;
            }
        }
    }

    private static final float PI = (float) Math.PI;

    @Override
    public void setupAnim(HelperEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        computePose(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        crossfade(entity, ageInTicks);
        applyVariantParts(entity, ageInTicks, limbSwingAmount);   // after the crossfade, so replica parts copy the final pose
        runMotion(entity, limbSwing, limbSwingAmount, ageInTicks);
    }

    /**
     * The premium motion layer: reads the finished pose into arrays, runs {@link HelperMotion} (idle / walk / run / flourish tracks, fan spreads and
     * springs for hair, cloth, tails and feathers) and writes the result back to the parts. Only the Gaia-model variants that have motion data use it.
     */
    private void runMotion(HelperEntity entity, float limbSwing, float limbSwingAmount, float age) {
        this.glowK = 1.0F;
        if (this.extras.isEmpty()) return;
        HelperVariant hv = entity.getVariant();
        String v = hv.name().toLowerCase(java.util.Locale.ROOT);
        if (!HelperMotionData.has(v)) return;
        HelperMotionRig rig = this.motionRigs.get(v);
        if (rig == null || rig.generation != HelperMotionData.generation) {
            java.util.List<HelperVariantParts.Spec> specs = new java.util.ArrayList<>();
            java.util.List<ModelPart> parts = new java.util.ArrayList<>();
            for (Extra e : this.extras) {
                if (!e.spec().appliesTo(v)) continue;
                specs.add(e.spec());
                parts.add(e.part());
            }
            rig = new HelperMotionRig(v, this.stdArray, specs, parts);
            this.motionRigs.put(v, rig);
        }
        final int S = HelperMotion.STRIDE;
        float[] pose = rig.pose;
        for (int i = 0; i < rig.n; i++) {
            ModelPart p = rig.parts[i];
            int o = i * S;
            pose[o] = p.x; pose[o + 1] = p.y; pose[o + 2] = p.z;
            pose[o + 3] = p.xRot; pose[o + 4] = p.yRot; pose[o + 5] = p.zRot;
            pose[o + 6] = rig.rest[o + 6];
            pose[o + 7] = 1.0F;
        }
        boolean downed = entity.isDowned();
        boolean combat = entity.inCombat() && !downed;
        boolean calm = !combat && !downed && entity.aim() == HelperEntity.AIM_NONE && !entity.isPassenger();
        float amt = Math.min(1.0F, limbSwingAmount);
        HelperMotion.In in = this.motionIn;
        in.age = age;
        float last = entity.animSimAge;
        in.dt = last < 0.0F || age < last || age - last > 6.0F ? 0.0F : age - last;
        entity.animSimAge = age;
        in.limbSwing = limbSwing;
        in.amt = amt;
        in.run = entity.isSprinting() ? HelperSwings.clamp01((amt - 0.35F) / 0.4F) : 0.0F;
        in.air = !entity.onGround() && !entity.isInWater() && !entity.isPassenger() ? 1.0F : 0.0F;
        in.idle = 1.0F - HelperSwings.clamp01(amt * 6.0F);
        in.calm = calm;
        in.fl = -1;
        in.flT = 0.0F;
        int fl = entity.animIdleVariant;
        if (calm && fl >= 0 && fl < rig.vm.nflourish) {
            in.fl = fl;
            float dur = Math.max(1.0F, rig.vm.flourish[fl].dur);
            in.flT = HelperSwings.clamp01((age - entity.animIdleStartTick) / dur);
        }
        double yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
        double dx = entity.getDeltaMovement().x, dz = entity.getDeltaMovement().z;
        in.vf = (float) (dx * -Mth.sin((float) yaw) + dz * Mth.cos((float) yaw));
        in.vl = (float) (dx * Mth.cos((float) yaw) + dz * Mth.sin((float) yaw));
        // weapon arms are the body layer's: the idle and walk clips leave them alone
        in.busy = (combat || this.bodyIn.carry) && !downed ? (int) HelperBody.CARRY[Math.max(0, Math.min(9, this.bodyIn.kind)) * 9 + 8] : 0;
        int simLen = 8 + rig.bind.nsim * 4;
        if (entity.animSim == null || entity.animSim.length != simLen) entity.animSim = new float[simLen];
        HelperMotion.run(pose, rig.rest, rig.xch, rig.vm, in, rig.bind, entity.animSim);
        for (int i = 0; i < rig.n; i++) {
            ModelPart p = rig.parts[i];
            int o = i * S;
            p.x = pose[o]; p.y = pose[o + 1]; p.z = pose[o + 2];
            p.xRot = pose[o + 3]; p.yRot = pose[o + 4]; p.zRot = pose[o + 5];
            HelperVariantParts.Spec sp = rig.specs[i];
            if (sp != null) {
                if (sp.hidden) p.visible = pose[o + 6] > 0.5F;
                float sc = sp.scale * pose[o + 7];
                p.xScale = sc; p.yScale = sc; p.zScale = sc;
            }
        }
        // parts that copy a standard part (the jester's head replica, the infernal's wings) take its final pose
        for (int i = 0; i < rig.n; i++) {
            int f = rig.follow[i];
            if (f < 0) continue;
            ModelPart p = rig.parts[i], q = rig.parts[f];
            p.x = q.x; p.y = q.y; p.z = q.z;
            p.xRot = q.xRot; p.yRot = q.yRot; p.zRot = q.zRot;
        }
        this.glowK = rig.xch[rig.fxGlow];
    }

    /** Ticks a pose-mode change (combat, carry, aim, airborne, downed, cast) is eased over instead of snapping. */
    private static final float FADE_TICKS = 5.0F;

    /** Everything that picks a different pose family; a change in this key starts a crossfade. */
    private static int modeKey(HelperEntity entity, float ageInTicks) {
        WeaponKind kind = WeaponKind.of(entity.getMainHandItem(), entity.getOffhandItem());
        int aim = entity.aim();
        boolean downed = entity.isDowned();
        boolean combat = entity.inCombat() && !downed;
        boolean air = !entity.onGround() && !entity.isInWater() && !entity.isPassenger();
        boolean carry = !entity.getMainHandItem().isEmpty() && entity.animIdleVariant != 3;
        boolean cast = false;
        if (entity.animCastStyle != HelperCasts.STYLE_NONE && aim == HelperEntity.AIM_NONE) {
            float pCast = (ageInTicks - entity.animCastStartTick) / (float) Math.max(6, entity.animCastTicks);
            cast = pCast >= 0 && pCast < 1.35F;
        }
        int key = (downed ? 1 : 0) | (entity.isPassenger() ? 2 : 0) | (air ? 4 : 0) | (combat ? 8 : 0)
                | (aim << 4) | (carry ? 64 : 0) | (cast ? 128 : 0);
        if (combat || carry || aim != HelperEntity.AIM_NONE) key |= (kind.ordinal() + 1) << 8;
        return key;
    }

    /**
     * Eases between pose families. When the mode key changes, the pose she was last drawn in is kept and the new
     * pose is blended in over {@link #FADE_TICKS}; steady poses and the swing/flourish curves are untouched.
     */
    private void crossfade(HelperEntity entity, float ageInTicks) {
        int n = this.allParts.size();
        if (entity.animPoseLast == null || entity.animPoseLast.length != n * 6) {
            entity.animPoseLast = new float[n * 6];
            entity.animPoseFrom = new float[n * 6];
            entity.animModeKey = modeKey(entity, ageInTicks);
        } else {
            int key = modeKey(entity, ageInTicks);
            if (key != entity.animModeKey) {
                entity.animModeKey = key;
                // a long gap means she was off screen: snap instead of fading from a stale pose
                if (ageInTicks >= entity.animPoseLastAge && ageInTicks - entity.animPoseLastAge < 8.0F) {
                    System.arraycopy(entity.animPoseLast, 0, entity.animPoseFrom, 0, n * 6);
                    entity.animFadeStart = ageInTicks;
                } else {
                    entity.animFadeStart = -1000.0F;
                }
            }
        }
        float f = (ageInTicks - entity.animFadeStart) / FADE_TICKS;
        boolean fading = f >= 0.0F && f < 1.0F;
        float w = fading ? HelperSwings.easeInOutSine(f) : 1.0F;
        float[] last = entity.animPoseLast;
        float[] from = entity.animPoseFrom;
        for (int i = 0; i < n; i++) {
            ModelPart part = this.allParts.get(i);
            int o = i * 6;
            if (fading) {
                part.x = Mth.lerp(w, from[o], part.x);
                part.y = Mth.lerp(w, from[o + 1], part.y);
                part.z = Mth.lerp(w, from[o + 2], part.z);
                part.xRot = Mth.lerp(w, from[o + 3], part.xRot);
                part.yRot = Mth.lerp(w, from[o + 4], part.yRot);
                part.zRot = Mth.lerp(w, from[o + 5], part.zRot);
            }
            last[o] = part.x;
            last[o + 1] = part.y;
            last[o + 2] = part.z;
            last[o + 3] = part.xRot;
            last[o + 4] = part.yRot;
            last[o + 5] = part.zRot;
        }
        entity.animPoseLastAge = ageInTicks;
    }

    private void computePose(HelperEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.allParts.forEach(ModelPart::resetPose);
        boolean showDragon = AvatarConfig.dragonParts();
        HelperVariant variant = entity.getVariant();
        this.curVariant = variant;
        this.hatKick = 0.0F;
        if (variant == HelperVariant.ANGEL) {
            this.halo.visible = true;
            this.hornbase.visible = false;
            this.tail1.visible = false;
            this.rightwing.visible = true;
            this.leftwing.visible = true;
            this.halo.yRot = ageInTicks * 0.04F;
        } else if (variant == HelperVariant.DEMON) {
            this.halo.visible = false;
            this.hornbase.visible = showDragon;
            this.tail1.visible = showDragon;
            this.rightwing.visible = showDragon;
            this.leftwing.visible = showDragon;
            this.hornbase.xRot = -0.52F;
            this.hornbase.zRot = 0.12F;
        } else if (variant == HelperVariant.ASTRAL) {
            this.halo.visible = false;
            this.hornbase.visible = showDragon;
            this.tail1.visible = showDragon;
            this.rightwing.visible = showDragon;
            this.leftwing.visible = showDragon;
            this.hornbase.xRot = -0.05F;
            this.hornbase.zRot = -0.08F;
        } else {
            this.halo.visible = false;
            this.hornbase.visible = showDragon;
            this.tail1.visible = showDragon;
            this.rightwing.visible = showDragon;
            this.leftwing.visible = showDragon;
            this.hornbase.xRot = -0.2618F;
            this.hornbase.zRot = 0.0F;
        }

        WeaponKind kind = WeaponKind.of(entity.getMainHandItem(), entity.getOffhandItem());
        this.casting = entity.isCasting();
        int aim = entity.aim();
        boolean downed = entity.isDowned();
        boolean combat = entity.inCombat() && !downed;
        float amt = Math.min(1.0F, limbSwingAmount);
        float run = entity.isSprinting() ? HelperSwings.clamp01((amt - 0.35F) / 0.4F) : 0.0F;
        float air = !entity.onGround() && !entity.isInWater() && !entity.isPassenger() ? 1.0F : 0.0F;
        float idle = 1.0F - HelperSwings.clamp01(amt * 6.0F);
        float yawRad = netHeadYaw * Mth.DEG_TO_RAD;
        float pitchRad = headPitch * Mth.DEG_TO_RAD;

        // ---- eyes and head
        this.headeyes.visible = !downed && (entity.tickCount % 70 < 2 || (entity.tickCount + 23) % 230 < 2);
        this.head.yRot = yawRad;
        this.head.xRot = pitchRad;
        this.hair1.yRot = yawRad * 0.6F;

        // ---- speech gesture and expression
        float talkDt = ageInTicks - this.talkLastAge;
        this.talkLastAge = ageInTicks;
        if (talkDt < 0.0F || talkDt > 10.0F) talkDt = 0.0F;
        boolean talking = !downed && com.blighteddreams.helper.client.voice.HelperVoice.isSpeaking();
        this.talkAmt += ((talking ? 1.0F : 0.0F) - this.talkAmt) * Math.min(1.0F, talkDt * 0.25F);
        if (this.talkAmt > 0.01F) {
            // two unrelated rhythms read as syllables and emphasis rather than a metronome
            float talkNod = (Mth.sin(ageInTicks * 0.55F) * 0.6F + Mth.sin(ageInTicks * 0.93F + 1.3F) * 0.4F) * 0.045F;
            float talkTilt = Mth.cos(ageInTicks * 0.33F) * 0.03F;
            this.head.xRot += talkNod * this.talkAmt;
            this.head.zRot += talkTilt * this.talkAmt;
            this.chest.xRot += talkNod * 0.5F * this.talkAmt;
            this.hair1.xRot += talkNod * 0.4F * this.talkAmt;
        }

        // ---- body layer (HelperBody, shared with the model studio): contrapposto and breathing, the gait with planted feet, pelvis bob /
        // sway / twist, banking, airborne and landing, and the standard wings, tail, hair and gown
        HelperBody.In bi = this.bodyIn;
        float lastBody = entity.bodyAge;
        bi.age = ageInTicks;
        bi.dt = lastBody < 0.0F || ageInTicks < lastBody || ageInTicks - lastBody > 6.0F ? -1.0F : ageInTicks - lastBody;
        entity.bodyAge = ageInTicks;
        bi.limbSwing = limbSwing;
        bi.amt = amt;
        bi.run = run;
        bi.air = air;
        bi.vy = (float) entity.getDeltaMovement().y;
        float bodyYaw = Mth.rotLerp(ageInTicks - entity.tickCount, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
        double dx = entity.getDeltaMovement().x, dz = entity.getDeltaMovement().z;
        bi.vf = (float) (dx * -Mth.sin(bodyYaw) + dz * Mth.cos(bodyYaw));
        bi.vl = (float) (dx * Mth.cos(bodyYaw) + dz * Mth.sin(bodyYaw));
        bi.yaw = bodyYaw;
        bi.combat = combat;
        bi.carry = !entity.getMainHandItem().isEmpty() && entity.animIdleVariant != 3;
        bi.calm = !combat && !downed && aim == HelperEntity.AIM_NONE && !entity.isPassenger();
        bi.kind = kind.ordinal();
        bi.aim = aim;
        bi.style = variant.ordinal();
        bi.riding = this.riding;
        bi.flourish = entity.animIdleVariant >= 0;
        if (entity.bodySim == null || entity.bodySim.length != HelperBody.S_LEN) entity.bodySim = new float[HelperBody.S_LEN];
        readStd();
        HelperBody.locomotion(this.bodyPose, this.bodyRest, bi, entity.bodySim);
        writeStd();
        this.chest.xRot += Mth.sin(ageInTicks * 0.08F) * 0.025F;

        // ---- downed: kneel, nothing else
        if (downed) {
            this.head.xRot = Math.max(this.head.xRot, 0.45F);
            this.bodybottom.xRot += 0.2F;
            this.rightarm.xRot = -0.35F;
            this.leftarm.xRot = -0.25F;
            this.rightarmlower.xRot = -0.5F;
            this.leftarmlower.xRot = -0.3F;
            this.rightleg.xRot = -1.25F;
            this.leftleg.xRot = -1.25F;
            this.rightleglower.xRot = 1.35F;
            this.leftleglower.xRot = 1.35F;
            this.skirt1.xRot = 0.45F;
            this.rightwing.yRot = -1.15F;
            this.leftwing.yRot = 1.15F;
            this.rightwing.zRot = 0.35F;
            this.leftwing.zRot = -0.35F;
            return;
        }
        if (this.riding) {
            this.rightleg.xRot = -1.4137F;
            this.rightleg.yRot = 0.3142F;
            this.leftleg.xRot = -1.4137F;
            this.leftleg.yRot = -0.3142F;
        }

        // ---- idle flourishes (only while standing still and calm)
        boolean ownMotion = variant.usesXlModel() && !this.extras.isEmpty() && HelperMotionData.has(variant.name().toLowerCase(java.util.Locale.ROOT));
        if (ownMotion) {
            // idle, flourishes, sway and the spring dynamics run in runMotion() after the crossfade
        } else if (entity.animIdleVariant >= 0 && !combat && aim == HelperEntity.AIM_NONE) {
            float t = HelperSwings.clamp01((ageInTicks - entity.animIdleStartTick) / 60.0F);
            float e = HelperSwings.easeInOutSine(Mth.sin(t * PI));
            idleFlourish(entity.animIdleVariant, kind, t, e, ageInTicks);
        } else if (!combat && aim == HelperEntity.AIM_NONE && idle > 0.8F) {
            // Calm idle weight shifts & gentle breathing
            float idleShift = Mth.sin(ageInTicks * 0.05F) * 0.035F;
            this.bodybottom.zRot += idleShift;
            this.bodytop.zRot -= idleShift * 0.6F;
            this.rightleg.zRot += idleShift * 0.5F;
            this.leftleg.zRot += idleShift * 0.5F;
        }

        // ---- how she holds what is in her hand: fighting stance or relaxed carry (HelperBody); aim poses own the arms, the body layer the feet
        readStd();
        HelperBody.stance(this.bodyPose, this.bodyRest, bi, entity.bodySim);
        writeStd();
        if (aim != HelperEntity.AIM_NONE) {
            aimPose(entity, kind, aim, ageInTicks, yawRad, pitchRad);
        }

        // ---- jester / nun: their own calm idle on top of the shared one (hands stay free of the combat and aim poses)
        if (!ownMotion && !combat && aim == HelperEntity.AIM_NONE && idle > 0.8F) {
            variantIdle(entity, ageInTicks);
        }

        // ---- melee swing, blended over everything above
        float p = (ageInTicks - entity.animSwingStartTick) / HelperSwings.ticks(entity.animSwingKind);
        if (p < 0 || p >= 1) {
            float attackAnim = entity.getAttackAnim(ageInTicks - entity.tickCount);
            if (attackAnim > 0.0F && attackAnim < 1.0F) {
                p = attackAnim;
            }
        }
        if (p >= 0 && p < 1 && aim == HelperEntity.AIM_NONE) {
            readStd();
            HelperBody.swing(this.bodyPose, this.bodyRest, bi, entity.bodySim, bodySwing(entity.animSwingKind, entity.animSwingVariant), p);
            writeStd();
        } else if (entity.animCastStyle != HelperCasts.STYLE_NONE && aim == HelperEntity.AIM_NONE) {
            float pCast = (ageInTicks - entity.animCastStartTick) / (float) Math.max(6, entity.animCastTicks);
            if (pCast >= 0 && pCast < 1.35F) {
                applyCast(HelperCasts.get(entity.animCastStyle, entity.animCastVariant), pCast, amt > 0.15F);
            }
        }

        // ---- feet onto their targets (leg IK), a steady head, and the springs of the tail, hair, gown and wings
        readStd();
        HelperBody.finish(this.bodyPose, this.bodyRest, bi, entity.bodySim);
        writeStd();
    }

    private void readStd() {
        float[] p = this.bodyPose;
        for (int i = 0; i < this.stdArray.length; i++) {
            ModelPart q = this.stdArray[i];
            int o = i * HelperBody.STRIDE;
            p[o] = q.x; p[o + 1] = q.y; p[o + 2] = q.z;
            p[o + 3] = q.xRot; p[o + 4] = q.yRot; p[o + 5] = q.zRot;
        }
    }

    private void writeStd() {
        float[] p = this.bodyPose;
        for (int i = 0; i < this.stdArray.length; i++) {
            ModelPart q = this.stdArray[i];
            int o = i * HelperBody.STRIDE;
            q.x = p[o]; q.y = p[o + 1]; q.z = p[o + 2];
            q.xRot = p[o + 3]; q.yRot = p[o + 4]; q.zRot = p[o + 5];
        }
    }

    /** HelperSwings' key poses as the body layer's swing (cached per swing) */
    private final java.util.Map<HelperSwings.Swing, HelperBody.Swing> bodySwings = new java.util.IdentityHashMap<>();

    private HelperBody.Swing bodySwing(WeaponKind kind, int variant) {
        HelperSwings.Swing s = HelperSwings.get(kind, variant);
        HelperBody.Swing b = this.bodySwings.get(s);
        if (b == null) {
            b = new HelperBody.Swing();
            b.windup = s.windup();
            b.strike = s.strike();
            b.follow = s.follow();
            b.spin = s.spin();
            b.leftDelay = s.leftDelay();
            b.kind = kind.ordinal();
            this.bodySwings.put(s, b);
        }
        return b;
    }

    /** Arm values: right x, y, z, forearm; left x, y, z, forearm. */
    private void arms(float rx, float ry, float rz, float rl, float lx, float ly, float lz, float ll) {
        this.rightarm.xRot = rx;
        this.rightarm.yRot = ry;
        this.rightarm.zRot = rz;
        this.rightarmlower.xRot = rl;
        this.leftarm.xRot = lx;
        this.leftarm.yRot = ly;
        this.leftarm.zRot = lz;
        this.leftarmlower.xRot = ll;
    }

    /** Calm standing idle that makes the jester, the infernal and the nun feel different from Gaia: all of them move the whole body. */
    private void variantIdle(HelperEntity entity, float age) {
        if (this.curVariant == HelperVariant.JESTER) {
            // a playful hop, 1.5x the first speed, alternating legs: a hop on the right leg (left knee tucked), then one on the left
            float c = age * 0.225F;
            boolean leftUp = (((int) Math.floor(c / (2.0F * PI))) & 1) == 0;
            float sn = Mth.sin(c);
            float air = Math.max(0.0F, sn);                          // airborne half of the cycle
            float crouch = Math.max(0.0F, -sn);                      // knees-bent half
            float kick = air * Mth.sin(c * 2.0F + 0.6F);
            float liftL = leftUp ? 1.0F : 0.15F, liftR = leftUp ? 0.15F : 1.0F;
            float tuckL = air * (0.15F + 0.85F * liftL), tuckR = air * (0.15F + 0.85F * liftR);
            this.root.y += crouch * 1.3F - air * 3.4F;
            this.rightleg.xRot += -0.45F * crouch - 0.80F * tuckR + (leftUp ? 0.0F : kick * 0.25F);
            this.rightleglower.xRot += 0.9F * crouch + 1.5F * tuckR;
            this.leftleg.xRot += -0.45F * crouch - 0.80F * tuckL + (leftUp ? kick * 0.25F : 0.0F);
            this.leftleglower.xRot += 0.9F * crouch + 1.5F * tuckL;
            this.rightleg.zRot += 0.12F * tuckR;
            this.leftleg.zRot -= 0.12F * tuckL;
            this.bodybottom.zRot += (leftUp ? -0.07F : 0.07F) * air + sn * 0.03F;   // hips swing over the standing leg
            this.bodybottom.yRot += Mth.sin(c * 0.5F) * 0.10F;                      // hips and shoulders twist against each other
            this.bodytop.yRot -= Mth.sin(c * 0.5F) * 0.20F;
            this.bodytop.xRot += 0.20F * crouch - 0.08F * air;
            this.head.xRot += 0.10F * crouch - 0.10F * air;
            this.head.zRot += Mth.sin(age * 0.16F + 1.0F) * 0.09F;
            this.head.yRot += Mth.sin(age * 0.1F) * 0.12F + Mth.sin(c * 0.5F) * 0.12F;
            this.leftarm.zRot -= 0.18F + 0.85F * air;                // arms fling up with the jump
            this.leftarm.xRot += -0.25F * air + 0.20F * crouch;
            if (entity.getMainHandItem().isEmpty()) {
                this.rightarm.zRot += 0.18F + 0.85F * air;
                this.rightarm.xRot += -0.25F * air + 0.20F * crouch;
            }
            this.hair1.xRot -= 0.22F * air - 0.12F * crouch;         // her hair lags behind the hop
            this.hair2.xRot -= 0.18F * air - 0.08F * crouch;
            this.hatKick = Math.max(this.hatKick, 0.2F + 0.7F * Math.abs(Mth.cos(c)));   // the hat whips on launch and landing
        } else if (this.curVariant == HelperVariant.DEMON) {
            float br = Mth.sin(age * 0.06F);
            float tw = Mth.sin(age * 0.035F);
            float wf = Mth.sin(age * 0.07F);
            this.root.y += 0.3F;
            this.bodybottom.zRot += 0.10F + br * 0.015F;            // weight on one hip
            this.bodybottom.yRot -= tw * 0.05F;
            this.bodytop.zRot -= 0.07F;
            this.bodytop.yRot += tw * 0.09F;                         // shoulders slowly turn
            this.bodytop.xRot += br * 0.012F;
            this.head.zRot += -0.09F + Mth.sin(age * 0.05F + 1.0F) * 0.03F;
            this.head.xRot -= 0.07F;                                // chin up
            this.head.yRot += tw * 0.10F;
            this.rightleg.xRot += -0.14F;                           // one knee relaxed forward, the other leg straight
            this.rightleglower.xRot += 0.18F;
            this.leftleg.zRot -= 0.05F;
            this.leftarm.xRot = 0.25F;                              // left hand on her hip
            this.leftarm.yRot = 0.0F;
            this.leftarm.zRot = -0.95F;
            this.leftarmlower.xRot = -1.05F;
            this.rightwing.zRot += 0.08F * wf;                      // slow wing breathing
            this.leftwing.zRot -= 0.08F * wf;
            this.rightwing.yRot -= 0.04F * wf;
            this.leftwing.yRot += 0.04F * wf;
            this.hair1.xRot += Mth.sin(age * 0.05F) * 0.03F;
            this.tail1.yRot += Mth.sin(age * 0.09F) * 0.22F;        // slow lazy tail swish
            this.tail2.yRot += Mth.sin(age * 0.09F - 0.8F) * 0.25F;
            this.tail3.yRot += Mth.sin(age * 0.09F - 1.6F) * 0.3F;
        } else if (this.curVariant == HelperVariant.GAIA) {
            float breathe = Mth.sin(age * 0.05F);
            float p = Mth.sin(age * 0.03F);                         // slow, composed weight shift
            float wf = Mth.sin(age * 0.055F);
            this.root.y += Math.abs(p) * 0.22F;
            this.bodybottom.zRot += p * 0.045F;
            this.bodybottom.yRot += Mth.sin(age * 0.021F) * 0.04F;
            this.bodytop.zRot -= p * 0.035F;
            this.bodytop.yRot -= Mth.sin(age * 0.021F) * 0.06F;     // shoulders counter-turn
            this.bodytop.xRot += breathe * 0.012F;
            this.rightleg.zRot += p * 0.025F;
            this.leftleg.zRot += p * 0.025F;
            this.rightleglower.xRot += Math.max(0.0F, p) * 0.12F;   // the unweighted knee softens
            this.leftleglower.xRot += Math.max(0.0F, -p) * 0.12F;
            this.head.yRot += Mth.sin(age * 0.026F + 0.8F) * 0.12F;
            this.head.xRot -= 0.03F + breathe * 0.015F;
            this.rightwing.zRot += 0.05F + 0.05F * wf;              // wings breathe
            this.leftwing.zRot -= 0.05F + 0.05F * wf;
            this.rightwing.yRot -= 0.035F * wf;
            this.leftwing.yRot += 0.035F * wf;
            this.hair1.xRot += breathe * 0.02F;
            this.hair2.xRot += Mth.sin(age * 0.045F - 0.7F) * 0.03F;
            this.tail1.yRot += Mth.sin(age * 0.07F) * 0.2F;         // a slow, regal tail sweep
            this.tail2.yRot += Mth.sin(age * 0.07F - 0.8F) * 0.22F;
            this.tail3.yRot += Mth.sin(age * 0.07F - 1.6F) * 0.26F;
        } else if (this.curVariant == HelperVariant.ANGEL) {
            float breathe = Mth.sin(age * 0.05F);
            float p = Mth.sin(age * 0.035F);                        // slow weight shift from one leg to the other
            float fl = Mth.sin(age * 0.06F);
            this.root.y += Math.abs(p) * 0.25F;
            this.bodybottom.zRot += p * 0.05F;
            this.bodybottom.xRot += breathe * 0.008F;
            this.bodytop.zRot -= p * 0.04F;
            this.bodytop.yRot += Mth.sin(age * 0.028F) * 0.07F;     // a faint turn of the shoulders
            this.bodytop.xRot += 0.03F + breathe * 0.012F;
            this.rightleg.zRot += p * 0.03F;
            this.leftleg.zRot += p * 0.03F;
            this.rightleg.xRot -= Math.max(0.0F, p) * 0.05F;
            this.leftleg.xRot -= Math.max(0.0F, -p) * 0.05F;
            this.rightleglower.xRot += Math.max(0.0F, p) * 0.14F;   // the unweighted knee softens
            this.leftleglower.xRot += Math.max(0.0F, -p) * 0.14F;
            this.head.yRot += Mth.sin(age * 0.03F + 0.5F) * 0.12F;
            this.head.xRot += 0.10F + breathe * 0.02F;              // head slightly bowed
            this.leftwing.zRot -= 0.05F + fl * 0.05F;               // wings stir gently
            this.rightwing.zRot += 0.05F + fl * 0.05F;
            this.leftwing.yRot += fl * 0.04F;
            this.rightwing.yRot -= fl * 0.04F;
            this.hair1.xRot += breathe * 0.02F;
            this.hair2.xRot += Mth.sin(age * 0.05F - 0.7F) * 0.03F;
            if (entity.getMainHandItem().isEmpty()) {              // hands together in prayer
                arms(-1.0F, -0.55F, 0.0F, -1.3F, -1.0F, 0.55F, 0.0F, -1.3F);
            } else {                                               // free hand over her heart, sword hand relaxed
                this.leftarm.xRot = -0.95F;
                this.leftarm.yRot = 0.5F;
                this.leftarm.zRot = 0.0F;
                this.leftarmlower.xRot = -1.35F;
            }
        }
    }

    /** Jester and nun versions of the first three idle flourishes (all full-body), plus extra flourishes 5..7 for the larger-texture variants. */
    private boolean variantFlourish(int v, float t, float e, float age) {
        if (this.curVariant == HelperVariant.JESTER && v <= 2) {
            switch (v) {
                case 0 -> { // sweeping bow: back leg trails, knees give, the whole spine folds
                    blendArms(e, -0.6F, 0.0F, 1.2F, -0.4F, -0.9F, 0.35F, -0.15F, -1.4F);
                    this.root.y += 0.9F * e;
                    this.rightleg.xRot += 0.35F * e;
                    this.rightleglower.xRot += 0.55F * e;
                    this.leftleg.xRot -= 0.30F * e;
                    this.leftleglower.xRot += 0.40F * e;
                    this.bodybottom.xRot -= 0.12F * e;
                    this.bodybottom.yRot += 0.15F * e;
                    this.bodytop.xRot += 0.55F * e;
                    this.bodytop.yRot -= 0.15F * e;
                    this.head.xRot += 0.15F * e;
                    this.hatKick = Math.max(this.hatKick, e);
                }
                case 1 -> { // giggle: the whole body bounces and shakes, hand over her mouth
                    float sh = Mth.sin(age * 0.9F);
                    this.root.y -= Math.abs(sh) * 1.0F * e;
                    this.rightleglower.xRot += Math.abs(sh) * 0.35F * e;
                    this.leftleglower.xRot += Math.abs(sh) * 0.35F * e;
                    this.bodybottom.zRot += sh * 0.09F * e;
                    this.bodytop.zRot += Mth.sin(age * 1.1F) * 0.06F * e;
                    this.bodytop.yRot += Mth.sin(age * 0.5F) * 0.15F * e;
                    this.bodytop.xRot += 0.12F * e;
                    this.head.zRot += sh * 0.22F * e;
                    this.leftarm.xRot = Mth.lerp(e, this.leftarm.xRot, -1.9F);
                    this.leftarm.yRot = Mth.lerp(e, this.leftarm.yRot, 0.35F);
                    this.leftarm.zRot = Mth.lerp(e, this.leftarm.zRot, 0.1F);
                    this.leftarmlower.xRot = Mth.lerp(e, this.leftarmlower.xRot, -1.5F);
                    this.hatKick = Math.max(this.hatKick, e);
                }
                default -> { // one full twirl with a little hop, legs tucking in the air
                    float up = Mth.sin(t * PI);
                    this.root.yRot += HelperSwings.easeInOutSine(t) * (2.0F * PI);
                    this.root.y -= up * 2.6F * e;
                    this.rightleg.xRot -= up * 0.5F * e;
                    this.rightleglower.xRot += up * 0.9F * e;
                    this.leftleg.xRot -= up * 0.3F * e;
                    this.leftleglower.xRot += up * 0.6F * e;
                    this.bodytop.xRot -= 0.08F * e;
                    this.bodytop.zRot += Mth.sin(t * PI * 2.0F) * 0.1F * e;
                    blendArms(e, -0.2F, 0.0F, 1.4F, -0.2F, -0.2F, 0.0F, -1.4F, -0.2F);
                    this.hair1.xRot -= 0.3F * up * e;
                    this.hatKick = Math.max(this.hatKick, e);
                }
            }
            return true;
        }
        if (v >= 5) {
            float c = age * 0.5F;
            switch (this.curVariant) {
                case JESTER -> {
                    switch (v) {
                        case 5 -> { // dance: hips swing, arms pump, feet tap
                            this.bodybottom.zRot += Mth.sin(c * 0.6F) * 0.2F * e;
                            this.bodytop.zRot -= Mth.sin(c * 0.6F) * 0.14F * e;
                            this.root.y -= Math.abs(Mth.sin(c * 0.6F)) * 1.4F * e;
                            this.rightleg.xRot -= Math.max(0.0F, Mth.sin(c * 0.6F)) * 0.5F * e;
                            this.leftleg.xRot -= Math.max(0.0F, -Mth.sin(c * 0.6F)) * 0.5F * e;
                            blendArms(e, -0.9F + Mth.sin(c * 1.2F) * 0.3F, 0.0F, -0.3F, -1.2F, -0.9F - Mth.sin(c * 1.2F) * 0.3F, 0.0F, 0.3F, -1.2F);
                            this.head.zRot += Mth.sin(c * 0.6F + 1.0F) * 0.18F * e;
                            this.hatKick = Math.max(this.hatKick, e);
                        }
                        case 6 -> { // juggling: both hands circling
                            blendArms(e, -1.15F + Mth.sin(c) * 0.45F, 0.0F, -0.2F, -0.9F - Mth.sin(c + 1.0F) * 0.3F,
                                    -1.15F + Mth.sin(c + PI) * 0.45F, 0.0F, 0.2F, -0.9F - Mth.sin(c + 1.0F + PI) * 0.3F);
                            this.head.xRot -= (0.25F + Mth.sin(c) * 0.08F) * e;     // eyes up on the balls
                            this.head.yRot += Mth.sin(c * 0.5F) * 0.2F * e;
                            this.hatKick = Math.max(this.hatKick, 0.5F * e);
                        }
                        default -> { // tip of the hat with a bow
                            blendArms(e, -2.6F, 0.0F, 0.1F, -1.7F, this.leftarm.xRot, this.leftarm.yRot, this.leftarm.zRot, this.leftarmlower.xRot);
                            this.bodytop.xRot += 0.3F * e;
                            this.head.xRot += 0.25F * e;
                            this.hatKick = Math.max(this.hatKick, e);
                        }
                    }
                }
                case ANGEL -> {
                    switch (v) {
                        case 5 -> { // floats up, arms and wings open, face lifted to the light
                            this.root.y -= 3.0F * e;
                            blendArms(e, -0.4F, 0.0F, 0.9F, -0.2F, -0.4F, 0.0F, -0.9F, -0.2F);
                            this.head.xRot -= 0.3F * e;
                            this.bodytop.xRot -= 0.1F * e;
                            this.rightleg.xRot += 0.15F * e;
                            this.leftleg.xRot += 0.15F * e;
                            this.rightwing.yRot = Mth.lerp(e, this.rightwing.yRot, -0.05F);
                            this.leftwing.yRot = Mth.lerp(e, this.leftwing.yRot, 0.05F);
                            this.rightwing.zRot += 0.4F * e;
                            this.leftwing.zRot -= 0.4F * e;
                        }
                        case 6 -> { // curtsy, lifting the hem
                            this.root.y += 1.8F * e;
                            this.rightleg.xRot += 0.5F * e;
                            this.rightleglower.xRot += 0.6F * e;
                            this.leftleg.xRot -= 0.35F * e;
                            this.leftleglower.xRot += 0.7F * e;
                            blendArms(e, -0.25F, 0.0F, 0.55F, -0.4F, -0.25F, 0.0F, -0.55F, -0.4F);
                            this.head.xRot += 0.2F * e;
                            this.bodytop.xRot += 0.15F * e;
                        }
                        default -> { // both hands on the cross, head bowed, a slow sway
                            blendArms(e, -1.0F, -0.45F, 0.0F, -1.5F, -1.0F, 0.45F, 0.0F, -1.5F);
                            this.head.xRot += 0.25F * e;
                            this.bodytop.zRot += Mth.sin(age * 0.1F) * 0.03F * e;
                        }
                    }
                }
                case DEMON -> {
                    switch (v) {
                        case 5 -> { // a smirking beckon
                            blendArms(e, -1.3F, -0.3F, 0.1F, -1.2F + Mth.sin(c * 1.2F) * 0.4F, this.leftarm.xRot, this.leftarm.yRot, this.leftarm.zRot, this.leftarmlower.xRot);
                            this.head.zRot -= 0.15F * e;
                            this.bodytop.yRot -= 0.2F * e;
                        }
                        case 6 -> { // dramatic reveal: wings thrown wide, chest out, fists down
                            this.rightwing.yRot = Mth.lerp(e, this.rightwing.yRot, -0.05F);
                            this.leftwing.yRot = Mth.lerp(e, this.leftwing.yRot, 0.05F);
                            this.rightwing.zRot += 0.5F * e;
                            this.leftwing.zRot -= 0.5F * e;
                            this.head.xRot -= 0.3F * e;
                            this.bodytop.xRot -= 0.2F * e;
                            this.tail1.xRot -= 0.3F * e;
                            blendArms(e, 0.1F, 0.0F, 0.4F, -0.2F, 0.1F, 0.0F, -0.4F, -0.2F);
                        }
                        default -> { // arms crossed, impatient foot tap
                            blendArms(e, -0.9F, -0.9F, 0.0F, -1.4F, -0.9F, 0.9F, 0.0F, -1.4F);
                            this.rightleglower.xRot += Math.max(0.0F, Mth.sin(age * 0.8F)) * 0.45F * e;
                            this.head.zRot += 0.08F * e;
                        }
                    }
                }
                default -> { }
            }
            return this.curVariant == HelperVariant.JESTER || this.curVariant == HelperVariant.ANGEL || this.curVariant == HelperVariant.DEMON;
        }
        if (this.curVariant == HelperVariant.ANGEL && v <= 2) {
            switch (v) {
                case 0 -> { // a blessing: a step forward, right hand raised, left over her heart
                    blendArms(e, -1.7F, -0.2F, 0.3F, -0.6F, -0.95F, 0.5F, 0.0F, -1.35F);
                    this.root.y += 0.5F * e;
                    this.leftleg.xRot -= 0.30F * e;
                    this.leftleglower.xRot += 0.20F * e;
                    this.rightleg.xRot += 0.18F * e;
                    this.rightleglower.xRot += 0.30F * e;
                    this.bodybottom.yRot += 0.10F * e;
                    this.bodytop.yRot -= 0.10F * e;
                    this.bodytop.xRot -= 0.05F * e;
                    this.head.xRot -= 0.12F * e;
                    this.leftwing.zRot -= 0.15F * e;
                    this.rightwing.zRot += 0.15F * e;
                }
                case 1 -> { // deep bow in prayer: hips back, knees bend, the spine folds forward
                    arms(-1.0F, -0.55F, 0.0F, -1.3F, -1.0F, 0.55F, 0.0F, -1.3F);
                    this.root.y += 1.0F * e;
                    this.rightleg.xRot -= 0.20F * e;
                    this.leftleg.xRot -= 0.20F * e;
                    this.rightleglower.xRot += 0.38F * e;
                    this.leftleglower.xRot += 0.38F * e;
                    this.bodybottom.xRot -= 0.20F * e;
                    this.bodytop.xRot += 0.45F * e;
                    this.head.xRot += 0.30F * e;
                    this.leftwing.zRot += 0.10F * e;
                    this.rightwing.zRot -= 0.10F * e;
                }
                default -> { // looks up toward the light: leans back, rises on her toes, hands opening, wings lifting
                    blendArms(e, -0.5F, 0.0F, 0.7F, -0.3F, -0.5F, 0.0F, -0.7F, -0.3F);
                    this.root.y -= 0.9F * e;
                    this.rightleg.xRot += 0.08F * e;
                    this.leftleg.xRot += 0.08F * e;
                    this.bodybottom.xRot += 0.10F * e;
                    this.bodytop.xRot -= 0.22F * e;
                    this.head.xRot -= 0.45F * e;
                    this.leftwing.zRot -= 0.25F * e;
                    this.rightwing.zRot += 0.25F * e;
                    this.leftwing.yRot = Mth.lerp(e, this.leftwing.yRot, 0.1F);
                    this.rightwing.yRot = Mth.lerp(e, this.rightwing.yRot, -0.1F);
                }
            }
            return true;
        }
        return false;
    }

    private void idleFlourish(int v, WeaponKind kind, float t, float e, float age) {
        if (variantFlourish(v, t, e, age)) return;
        switch (v) {
            case 0 -> { // big stretch, wings wide
                blendArms(e, -2.9F, 0.15F, -0.25F, -0.25F, -2.9F, -0.15F, 0.25F, -0.25F);
                this.bodytop.xRot -= 0.25F * e;
                this.head.xRot -= 0.35F * e;
                this.rightwing.yRot = Mth.lerp(e, this.rightwing.yRot, -0.12F);
                this.leftwing.yRot = Mth.lerp(e, this.leftwing.yRot, 0.12F);
                this.rightwing.zRot += 0.35F * e;
                this.leftwing.zRot -= 0.35F * e;
                this.tail1.xRot -= 0.35F * e;
            }
            case 1 -> { // glance back at her own tail as it flicks
                this.head.yRot += 1.0F * e;
                this.bodytop.yRot += 0.3F * e;
                this.tail1.yRot += Mth.sin(age * 0.7F) * 0.6F * e;
                this.tail2.yRot += Mth.sin(age * 0.7F - 0.7F) * 0.7F * e;
                this.tail3.yRot += Mth.sin(age * 0.7F - 1.4F) * 0.8F * e;
                this.leftarm.zRot -= 0.25F * e;
            }
            case 2 -> { // wing ruffle: open and shut twice
                float open = Math.abs(Mth.sin(t * PI * 2.0F)) * e;
                this.rightwing.yRot = Mth.lerp(open, this.rightwing.yRot, -0.05F);
                this.leftwing.yRot = Mth.lerp(open, this.leftwing.yRot, 0.05F);
                this.rightwing.zRot += 0.45F * open;
                this.leftwing.zRot -= 0.45F * open;
                this.rightwing.xRot -= 0.2F * open;
                this.leftwing.xRot -= 0.2F * open;
                this.head.zRot += 0.1F * open;
            }
            case 3 -> { // play with the weapon
                switch (kind) {
                    case DUAL -> { // twin blade spin & cross flourish
                        float c = t * PI * 4.0F;
                        blendArms(e,
                                -1.05F + Mth.sin(c) * 0.18F, Mth.sin(c) * 0.45F, Mth.cos(c) * 0.25F, -0.6F,
                                -1.05F - Mth.sin(c) * 0.18F, -Mth.sin(c) * 0.45F, -Mth.cos(c) * 0.25F, -0.6F);
                        this.head.xRot += 0.15F * e;
                    }
                    case SWORD, DAGGER -> { // wrist twirl
                        float c = t * PI * 4.0F;
                        blendArms(e, -1.05F + Mth.sin(c) * 0.15F, Mth.sin(c) * 0.5F, Mth.cos(c) * 0.3F, -0.6F,
                                this.leftarm.xRot, this.leftarm.yRot, this.leftarm.zRot - 0.2F, this.leftarmlower.xRot);
                        this.head.xRot += 0.2F * e;
                        this.head.yRot -= 0.2F * e;
                    }
                    case BOW -> { // lift the bow and check the string
                        blendArms(e, -1.4F, 0.1F, 0, -1.1F, -1.2F, 0.6F, 0, -0.9F);
                        this.head.yRot -= 0.3F * e;
                        this.head.xRot += 0.15F * e;
                    }
                    case CROSSBOW -> { // inspect the bolt groove
                        blendArms(e, -1.0F, -0.3F, 0, -0.8F, -1.2F, 0.5F, 0, -0.7F);
                        this.head.xRot += 0.35F * e;
                    }
                    case HEAVY, AXE -> { // lean on it
                        blendArms(e, -0.35F, -0.1F, 0, -0.05F, -0.45F, 0.45F, 0, -0.3F);
                        this.bodytop.xRot += 0.15F * e;
                        this.bodybottom.zRot += 0.08F * e;
                    }
                    case SPEAR, STAFF -> { // tap the shaft on the ground
                        float tap = Math.abs(Mth.sin(t * PI * 3.0F));
                        blendArms(e, -0.45F - 0.15F * tap, 0.1F, 0.1F, -1.0F, this.leftarm.xRot, this.leftarm.yRot, this.leftarm.zRot, this.leftarmlower.xRot);
                    }
                    default -> { // crack her knuckles
                        blendArms(e, -1.2F, -0.45F, 0, -1.0F, -1.2F, 0.45F, 0, -1.0F);
                        this.head.xRot += 0.2F * e;
                    }
                }
            }
            default -> { // hair flip
                blendArms(e, this.rightarm.xRot, this.rightarm.yRot, this.rightarm.zRot, this.rightarmlower.xRot,
                        -2.5F, -0.5F, -0.3F, -1.7F);
                this.head.zRot += 0.25F * e;
                this.head.xRot -= 0.15F * e;
                this.hair1.yRot += Mth.sin(t * PI * 2.0F) * 0.35F * e;
                this.hair2.xRot -= 0.25F * e;
            }
        }
    }

    private void blendArms(float w, float rx, float ry, float rz, float rl, float lx, float ly, float lz, float ll) {
        this.rightarm.xRot = Mth.lerp(w, this.rightarm.xRot, rx);
        this.rightarm.yRot = Mth.lerp(w, this.rightarm.yRot, ry);
        this.rightarm.zRot = Mth.lerp(w, this.rightarm.zRot, rz);
        this.rightarmlower.xRot = Mth.lerp(w, this.rightarmlower.xRot, rl);
        this.leftarm.xRot = Mth.lerp(w, this.leftarm.xRot, lx);
        this.leftarm.yRot = Mth.lerp(w, this.leftarm.yRot, ly);
        this.leftarm.zRot = Mth.lerp(w, this.leftarm.zRot, lz);
        this.leftarmlower.xRot = Mth.lerp(w, this.leftarmlower.xRot, ll);
    }

    /** Bow drawing, crossbow cranking and crossbow aiming: three clearly different poses. */
    private void aimPose(HelperEntity entity, WeaponKind kind, int aim, float age, float yaw, float pitch) {
        float since = age - entity.animAimStartTick;
        this.rightwing.yRot = -0.95F;
        this.leftwing.yRot = 0.95F;
        if (aim == HelperEntity.AIM_BOW) {
            float drawProgress = HelperSwings.clamp01(since / 20.0F);
            float draw = HelperSwings.easeInOutSine(drawProgress);
            float turn = 0.85F * HelperSwings.easeInOutSine(HelperSwings.clamp01(since / 12.0F));
            this.bodybottom.yRot -= turn * 0.4F;
            this.bodytop.yRot -= turn * 0.6F;
            this.head.yRot = yaw + turn;
            // Bow arm held forward towards target at chest height with slight natural elbow flex
            float rx = -1.45F + pitch;
            float ry = yaw + turn - 0.12F;
            float rz = 0.05F;
            float rl = -0.1F;
            // String arm pulls firmly back anchored directly at her chest / sternum (not down to her hip)
            float lx = -1.15F + pitch * 0.4F;
            float ly = Mth.lerp(draw, yaw + turn + 0.25F, yaw + turn + 0.68F);
            float lz = -0.32F * draw;
            float ll = Mth.lerp(draw, -0.45F, -1.42F);
            if (drawProgress >= 0.95F) ll += Mth.sin(age * 2.1F) * 0.015F;
            arms(rx, ry, rz, rl, lx, ly, lz, ll);
            this.rightleg.zRot += 0.15F;
            this.leftleg.zRot -= 0.2F;
            this.rightleg.xRot -= 0.15F;
            this.leftleg.xRot += 0.1F;
            this.tail1.yRot -= 0.3F;
            this.head.zRot -= 0.08F * draw;
        } else if (aim == HelperEntity.AIM_CRANK) {
            // Tactile 4-phase crossbow reload:
            // 0.0..0.20: Lower stock to waist, left hand reaches forward along rail to catch the string
            // 0.20..0.55: Heavy mechanical draw pull under tension with torso recoil and tension shudder
            // 0.55..0.85: String catches sear lock; left hand grabs bolt from belt pouch and seats it into rail
            // 0.85..1.00: Left hand cradles fore-end as crossbow is raised toward ready shoulder
            float progress = HelperSwings.clamp01(since / 24.0F);

            // Stance: grounded combat reload footing
            this.rightleg.zRot += 0.12F;
            this.leftleg.zRot -= 0.14F;
            this.rightleg.xRot -= 0.16F;
            this.rightleglower.xRot += 0.28F;
            this.leftleg.xRot += 0.14F;
            this.leftleglower.xRot += 0.22F;
            this.tail1.yRot -= 0.25F;

            float rx, ry, rz, rl;
            float lx, ly, lz, ll;

            if (progress < 0.20F) {
                float p = progress / 0.20F;
                float ease = HelperSwings.easeInOutSine(p);
                this.bodytop.xRot += Mth.lerp(ease, 0.05F, 0.22F);
                this.head.xRot = Mth.lerp(ease, pitch, 0.42F);
                this.head.yRot = Mth.lerp(ease, yaw, 0.1F);

                // Lower crossbow to hip level
                rx = Mth.lerp(ease, -1.2F + pitch, -0.75F);
                ry = Mth.lerp(ease, yaw, -0.25F);
                rz = 0.08F;
                rl = Mth.lerp(ease, -0.2F, -0.65F);

                // Left arm reaches out toward the string
                lx = Mth.lerp(ease, -1.0F, -0.85F);
                ly = Mth.lerp(ease, 0.2F, 0.15F);
                lz = 0.0F;
                ll = Mth.lerp(ease, -0.5F, -0.3F);
            } else if (progress < 0.55F) {
                float p = (progress - 0.20F) / 0.35F;
                float ease = HelperSwings.easeOutQuad(p);
                // Body pulls back against draw tension
                float tensionShudder = Mth.sin(age * 2.8F) * 0.02F * p;
                this.bodytop.xRot += 0.22F - 0.10F * ease + tensionShudder;
                this.bodytop.yRot += 0.12F * ease;
                this.head.xRot = 0.40F + tensionShudder;
                this.head.yRot = 0.08F;

                // Right arm braces stock at hip
                rx = -0.75F - 0.05F * ease;
                ry = -0.25F + 0.05F * ease;
                rz = 0.08F;
                rl = -0.65F - 0.15F * ease;

                // Left hand grips string and heaves back to latch mechanism
                lx = Mth.lerp(ease, -0.85F, -0.45F);
                ly = Mth.lerp(ease, 0.15F, 0.48F);
                lz = -0.15F * ease;
                ll = Mth.lerp(ease, -0.3F, -1.45F) + tensionShudder;
            } else if (progress < 0.85F) {
                float p = (progress - 0.55F) / 0.30F;
                float ease = HelperSwings.easeInOutSine(p);
                this.bodytop.xRot += 0.16F;
                this.bodytop.yRot += 0.08F * (1.0F - ease);
                this.head.xRot = 0.45F; // watching the bolt seat
                this.head.yRot = 0.05F;

                // Crossbow locked at hip
                rx = -0.80F;
                ry = -0.20F;
                rz = 0.08F;
                rl = -0.75F;

                // Left hand reaches down to hip/belt pouch, grabs bolt and slots it into track
                float reachX = p < 0.5F ? Mth.lerp(p * 2.0F, -0.45F, 0.25F) : Mth.lerp((p - 0.5F) * 2.0F, 0.25F, -0.75F);
                float reachY = p < 0.5F ? Mth.lerp(p * 2.0F, 0.48F, -0.35F) : Mth.lerp((p - 0.5F) * 2.0F, -0.35F, 0.20F);
                float reachL = p < 0.5F ? Mth.lerp(p * 2.0F, -1.45F, -0.40F) : Mth.lerp((p - 0.5F) * 2.0F, -0.40F, -0.95F);
                lx = reachX;
                ly = reachY;
                lz = -0.05F;
                ll = reachL;
            } else {
                float p = (progress - 0.85F) / 0.15F;
                float ease = HelperSwings.easeInOutSine(p);
                this.bodytop.xRot += Mth.lerp(ease, 0.16F, 0.0F);
                this.head.xRot = Mth.lerp(ease, 0.45F, pitch);
                this.head.yRot = Mth.lerp(ease, 0.05F, yaw);

                // Raise stock from hip toward shoulder aim
                rx = Mth.lerp(ease, -0.80F, -1.35F + pitch);
                ry = Mth.lerp(ease, -0.20F, yaw - 0.15F);
                rz = Mth.lerp(ease, 0.08F, 0.0F);
                rl = Mth.lerp(ease, -0.75F, -0.15F);

                // Left arm returns to cradle the foregrip
                lx = Mth.lerp(ease, -0.75F, -1.15F + pitch * 0.5F);
                ly = Mth.lerp(ease, 0.20F, yaw + 0.35F);
                lz = Mth.lerp(ease, -0.05F, 0.0F);
                ll = Mth.lerp(ease, -0.95F, -0.65F);
            }
            arms(rx, ry, rz, rl, lx, ly, lz, ll);
        } else {
            // Loaded crossbow shouldered, disciplined sniper aiming down the rail
            float turn = 0.35F * HelperSwings.easeOutQuad(HelperSwings.clamp01(since / 4.0F));
            this.bodytop.yRot -= turn;
            this.head.yRot = yaw + turn;
            this.head.xRot = pitch + 0.06F;
            this.head.zRot = 0.12F;
            arms(-1.35F + pitch, yaw + turn - 0.12F, 0.04F, -0.22F,
                 -1.3F + pitch, yaw + turn + 0.62F, -0.08F, -0.85F);
            this.rightleg.zRot += 0.1F;
            this.leftleg.zRot -= 0.1F;
        }
    }

    private void applyCast(HelperCasts.CastPose c, float p, boolean walking) {
        float[] from, to;
        float w;
        if (p < 0.65F) { // Gather phase - duration scales with spell cast ticks
            from = c.gather();
            to = c.gather();
            w = 1.0F;
            float tremor = Mth.sin(p * 28.0F) * 0.02F;
            this.bodytop.xRot += tremor;
            this.head.zRot += tremor * 0.5F;
        } else if (p < 1.0F) { // Explosive release snap
            from = c.gather();
            to = c.release();
            w = HelperSwings.easeOutExpo((p - 0.65F) / 0.35F);
        } else { // Follow-through recovery
            from = c.release();
            to = c.recover();
            w = HelperSwings.easeInOutSine(HelperSwings.clamp01((p - 1.0F) / 0.35F));
        }

        float[] k = new float[12];
        for (int i = 0; i < 12; i++) k[i] = Mth.lerp(w, from[i], to[i]);

        this.bodytop.yRot += k[0] * 0.6F;
        this.bodybottom.yRot += k[0] * 0.4F;
        this.bodytop.xRot += k[1] * 0.6F;
        this.bodymiddle.xRot += k[1] * 0.4F;
        arms(k[2], k[3], k[4], k[5], k[6], k[7], k[8], k[9]);

        if (!walking) {
            float lunge = k[10], crouch = k[11];
            this.root.z -= lunge;
            this.root.y += crouch;
            this.rightleg.xRot -= lunge * 0.15F + crouch * 0.12F;
            this.rightleglower.xRot += lunge * 0.12F + crouch * 0.3F;
            this.leftleg.xRot += lunge * 0.12F - crouch * 0.1F;
            this.leftleglower.xRot += crouch * 0.3F;
        }

        float flare = c.wingFlare() * (p >= 0.65F && p <= 1.0F ? 1.0F : 0.6F);
        this.rightwing.yRot = Mth.lerp(flare * 0.7F, this.rightwing.yRot, -0.2F);
        this.leftwing.yRot = Mth.lerp(flare * 0.7F, this.leftwing.yRot, 0.2F);
        this.rightwing.zRot += 0.4F * flare;
        this.leftwing.zRot -= 0.4F * flare;
        this.tail1.yRot -= k[0] * 0.5F;
        this.tail2.yRot -= k[0] * 0.3F;
        this.hair1.yRot -= k[0] * 0.3F;
        this.hair2.xRot += 0.25F * flare;
        this.head.xRot += k[1] * 0.4F;   // (the yaw is steadied by the body layer)
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }

    @Override
    public ModelPart getHead() {
        return this.head;
    }

    /**
     * Moves to her wrist through the whole chain (root, hips, waist, chest, upper arm, forearm), so the weapon follows
     * every twist of the swing. ItemInHandLayer then adds its own (+-1/16, 0.125, -0.625) step; the last translate
     * here cancels that so the grip lands in her glove.
     */
    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        if (this.casting) {
            poseStack.scale(0.0F, 0.0F, 0.0F);
            return;
        }
        boolean left = arm == HumanoidArm.LEFT;
        this.root.translateAndRotate(poseStack);
        this.bodybottom.translateAndRotate(poseStack);
        this.bodymiddle.translateAndRotate(poseStack);
        this.bodytop.translateAndRotate(poseStack);
        (left ? this.leftarm : this.rightarm).translateAndRotate(poseStack);
        (left ? this.leftarmlower : this.rightarmlower).translateAndRotate(poseStack);
        poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, -4.5F / 16.0F, 1.0F / 16.0F);
    }
}
