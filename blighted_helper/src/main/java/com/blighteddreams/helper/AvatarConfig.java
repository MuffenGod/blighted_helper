package com.blighteddreams.helper;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;

/**
 * How Emily is drawn (config/blighted_enhancements-avatar.toml). Client only. See design/HELPER_PLAN.md section 6f.
 * The values can also be changed in game with /helperavatar, which writes them back to this file.
 */
public class AvatarConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<String> STYLE;
    private static final ModConfigSpec.DoubleValue FIGURE_HEIGHT;
    private static final ModConfigSpec.DoubleValue HOVER;
    private static final ModConfigSpec.DoubleValue TURN;
    private static final ModConfigSpec.DoubleValue MAX_VIEW;
    private static final ModConfigSpec.DoubleValue DEPTH;
    private static final ModConfigSpec.DoubleValue HEM_FADE;
    private static final ModConfigSpec.BooleanValue DIRECTIONAL;
    private static final ModConfigSpec.BooleanValue CAST_SHADOW;
    private static final ModConfigSpec.BooleanValue ENV_SHADING;
    private static final ModConfigSpec.BooleanValue DRAGON_PARTS;
    private static final ModConfigSpec.BooleanValue PETALS;
    private static final ModConfigSpec.BooleanValue AFTERIMAGES;
    private static final ModConfigSpec.BooleanValue DEBUG_TINT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        STYLE = b.comment("skin = Minecraft player NPC with custom Emily skin and 3D dragon horns/wings/tail; standee = layered 2.5D figure; model = old cube 3D model.")
                .define("style", "skin");
        DRAGON_PARTS = b.comment("Show 3D Minecraft dragon horns, articulated bat wings, and swaying dragon tail on the skin NPC.")
                .define("dragonParts", true);
        FIGURE_HEIGHT = b.comment("Height of the painted figure in blocks, horns included. Her hitbox is 2.0 tall.")
                .defineInRange("figureHeight", 2.5, 1.2, 3.5);
        HOVER = b.comment("How far above the ground she floats, in blocks (0 = grounded on her heels/gown).")
                .defineInRange("hover", 0.0, 0.0, 1.0);
        TURN = b.comment("How much she turns toward you within each directional sector (0 = keeps sector facing, 1 = flat to camera).",
                        "Less than 1 leaves a sideways parallax view so the depth layers slide past each other.")
                .defineInRange("turnTowardPlayer", 0.65, 0.0, 1.0);
        MAX_VIEW = b.comment("Largest sideways parallax viewing angle within a directional sector, in degrees.")
                .defineInRange("maxViewAngle", 24.0, 0.0, 80.0);
        DEPTH = b.comment("Multiplies the spacing between depth layers. 1 = as designed, 2 = exaggerated, 0 = flat.")
                .defineInRange("depthScale", 1.0, 0.0, 4.0);
        HEM_FADE = b.comment("Fraction of her height at the bottom that fades into mist (0 = grounded high heels & gown train).")
                .defineInRange("hemFade", 0.0, 0.0, 0.4);
        DIRECTIONAL = b.comment("Switch between front, left, right, back, and folded-wing rear views based on camera angle relative to her body.")
                .define("directional", true);
        CAST_SHADOW = b.comment("Cast a directional 2D silhouette shadow of her layers (wings, horns, gown, tail) onto the ground.")
                .define("castShadow", true);
        ENV_SHADING = b.comment("Shade layers with curved 3D normals, ground ambient occlusion, and emissive eyes/gems so she blends into the world.")
                .define("envShading", true);
        PETALS = b.comment("Drifting petals around her.")
                .define("petals", true);
        AFTERIMAGES = b.comment("Fading ghost images behind her when she moves fast.")
                .define("afterimages", true);
        DEBUG_TINT = b.comment("Tint each layer a different colour, to see how the figure was cut.")
                .define("debugTint", false);
        SPEC = b.build();
    }

    private AvatarConfig() {}

    public static String style() { return STYLE.get().trim().toLowerCase(Locale.ROOT); }
    public static boolean skin() {
        String s = style();
        return s.equals("skin") || s.equals("player") || s.equals("npc");
    }
    public static boolean standee() { return style().equals("standee"); }
    public static void setStyle(String style) { STYLE.set(style); save(); }
    public static boolean dragonParts() { return DRAGON_PARTS.get(); }
    public static void setDragonParts(boolean v) { DRAGON_PARTS.set(v); save(); }
    public static float figureHeight() { return FIGURE_HEIGHT.get().floatValue(); }
    public static float hover() { return HOVER.get().floatValue(); }
    public static void setHover(double v) { HOVER.set(v); save(); }
    public static float turn() { return TURN.get().floatValue(); }
    public static void setTurn(double v) { TURN.set(v); save(); }
    public static float maxView() { return MAX_VIEW.get().floatValue(); }
    public static void setMaxView(double v) { MAX_VIEW.set(v); save(); }
    public static float depth() { return DEPTH.get().floatValue(); }
    public static void setDepth(double v) { DEPTH.set(v); save(); }
    public static float hemFade() { return HEM_FADE.get().floatValue(); }
    public static void setHemFade(double v) { HEM_FADE.set(v); save(); }
    public static boolean directional() { return DIRECTIONAL.get(); }
    public static void setDirectional(boolean v) { DIRECTIONAL.set(v); save(); }
    public static boolean castShadow() { return CAST_SHADOW.get(); }
    public static void setCastShadow(boolean v) { CAST_SHADOW.set(v); save(); }
    public static boolean envShading() { return ENV_SHADING.get(); }
    public static void setEnvShading(boolean v) { ENV_SHADING.set(v); save(); }
    public static boolean petals() { return PETALS.get(); }
    public static boolean afterimages() { return AFTERIMAGES.get(); }
    public static boolean debugTint() { return DEBUG_TINT.get(); }
    public static void setDebugTint(boolean v) { DEBUG_TINT.set(v); save(); }

    private static void save() {
        SPEC.save();
    }
}
