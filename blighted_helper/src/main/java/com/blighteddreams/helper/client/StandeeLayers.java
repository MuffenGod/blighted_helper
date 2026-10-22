package com.blighteddreams.helper.client;

import com.blighteddreams.helper.BlightedHelper;
import com.blighteddreams.helper.ai.MiniJson;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The depth layers of the standee, read from {@code assets/blighted_enhancements/helper/standee/layers.json}, so a resource
 * pack can replace the art or the layout without rebuilding the mod (F3+T reloads). Supports both a default {@code layers}
 * stack and directional {@code views} ({@code front}, {@code left}, {@code right}, {@code back}, {@code rear}).
 */
public final class StandeeLayers {
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(BlightedHelper.MOD_ID, "helper/standee/layers.json");

    /** A slow rotation about a pivot, for wings, hair and tail. */
    public record Flap(float degrees, float hertz, float phase) {}

    /**
     * @param z        depth in blocks relative to the figure plane: negative = further from the viewer
     * @param pivotU   pivot as a fraction of the image width (0 = left)
     * @param pivotV   pivot as a fraction of the image height (0 = top)
     * @param breathe  vertical scale wobble, as a fraction
     * @param hemFade  fade the bottom of this layer into mist
     * @param emissive render this layer with full-bright lightmap (for glowing eyes and gems)
     */
    public record Layer(String id, ResourceLocation texture, float z, float pivotU, float pivotV, Flap flap, float breathe, boolean hemFade, boolean emissive) {}

    /** {@code aspect} is image width divided by height. Layers in each directional view are sorted far to near. */
    public record Stack(float aspect, List<Layer> layers, Map<String, List<Layer>> views) {
        public List<Layer> forDirection(String dir) {
            if (views != null && dir != null) {
                List<Layer> v = views.get(dir);
                if (v != null && !v.isEmpty()) {
                    return v;
                }
            }
            return layers;
        }
    }

    private static volatile Stack cached;

    private StandeeLayers() {}

    public static Stack get() {
        Stack s = cached;
        if (s == null) {
            s = load();
            cached = s;
        }
        return s;
    }

    /** Called on resource reload. */
    public static void invalidate() {
        cached = null;
    }

    private static Stack load() {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(FILE);
        if (res.isEmpty()) {
            BlightedHelper.LOGGER.warn("[Avatar] {} not found: the standee has no layers", FILE);
            return new Stack(0.7083f, List.of(), Map.of());
        }
        try (InputStream in = res.get().open()) {
            Object root = MiniJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            float aspect = num(MiniJson.path(root, "aspect"), 0.7083f);
            List<Layer> defaultLayers = parseLayerList(MiniJson.path(root, "layers"));
            Map<String, List<Layer>> views = new LinkedHashMap<>();
            Object viewsObj = MiniJson.path(root, "views");
            if (viewsObj instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    List<Layer> parsed = parseLayerList(entry.getValue());
                    if (!parsed.isEmpty()) {
                        views.put(key, parsed);
                    }
                }
            }
            if (defaultLayers.isEmpty() && views.containsKey("front")) {
                defaultLayers = views.get("front");
            }
            BlightedHelper.LOGGER.info("[Avatar] standee loaded: default={} layers, directional views={}",
                    defaultLayers.stream().map(Layer::id).toList(), views.keySet());
            return new Stack(aspect, defaultLayers, Map.copyOf(views));
        } catch (Exception e) {
            BlightedHelper.LOGGER.error("[Avatar] could not read {}: {}", FILE, e.toString());
            return new Stack(0.7083f, List.of(), Map.of());
        }
    }

    private static List<Layer> parseLayerList(Object layersObj) {
        List<Layer> out = new ArrayList<>();
        if (layersObj instanceof List<?> list) {
            for (Object o : list) {
                String id = MiniJson.string(o, "id");
                String tex = MiniJson.string(o, "texture");
                ResourceLocation rl = tex == null ? null : ResourceLocation.tryParse(tex);
                if (id == null || rl == null) {
                    BlightedHelper.LOGGER.warn("[Avatar] a layer needs an id and a valid texture: {}", o);
                    continue;
                }
                Object flap = MiniJson.path(o, "flap");
                out.add(new Layer(
                        id,
                        rl,
                        num(MiniJson.path(o, "z"), 0f),
                        num(MiniJson.path(o, "pivot", 0), 0.5f),
                        num(MiniJson.path(o, "pivot", 1), 0.5f),
                        flap instanceof Map<?, ?>
                                ? new Flap(num(MiniJson.path(flap, "deg"), 0f), num(MiniJson.path(flap, "hz"), 0.4f), num(MiniJson.path(flap, "phase"), 0f))
                                : null,
                        num(MiniJson.path(o, "breathe"), 0f),
                        Boolean.TRUE.equals(MiniJson.path(o, "hemFade")),
                        Boolean.TRUE.equals(MiniJson.path(o, "emissive"))
                ));
            }
        }
        out.sort(Comparator.comparingDouble(Layer::z));
        return List.copyOf(out);
    }

    private static float num(Object o, float fallback) {
        return o instanceof Number n ? n.floatValue() : fallback;
    }
}
