package dev.infuse.opswords.altar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.pack.ResourcePackService;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Size and centre of each weapon model, read from the resource pack bundled in the jar, so any
 * weapon can be shown at the same height and centred on an altar.
 */
final class ModelBounds {

    record Bounds(float height, Vector3f center) {
    }

    private static final Bounds FALLBACK = new Bounds(1f, new Vector3f(8, 8, 8));
    private final Map<SwordType, Bounds> bounds = new EnumMap<>(SwordType.class);

    ModelBounds(OPSwordsPlugin plugin) {
        try (InputStream in = plugin.getResource(ResourcePackService.PACK_FILE)) {
            if (in == null) {
                return;
            }
            ZipInputStream zip = new ZipInputStream(in);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith("assets/op_swords/models/item/") || !name.endsWith(".json")) {
                    continue;
                }
                SwordType type = SwordType.byId(name.substring(name.lastIndexOf('/') + 1, name.length() - 5));
                if (type != null) {
                    bounds.put(type, measure(JsonParser.parseReader(new InputStreamReader(zip, StandardCharsets.UTF_8)).getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not measure weapon models for altars: " + e.getMessage());
        }
    }

    private static Bounds measure(JsonObject model) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        JsonArray elements = model.getAsJsonArray("elements");
        if (elements == null || elements.isEmpty()) {
            return FALLBACK;
        }
        for (JsonElement el : elements) {
            JsonObject e = el.getAsJsonObject();
            for (String corner : new String[]{"from", "to"}) {
                JsonArray v = e.getAsJsonArray(corner);
                for (int i = 0; i < 3; i++) {
                    float f = v.get(i).getAsFloat();
                    min[i] = Math.min(min[i], f);
                    max[i] = Math.max(max[i], f);
                }
            }
        }
        float height = Math.max(0.1f, (max[1] - min[1]) / 16f);
        return new Bounds(height, new Vector3f((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2));
    }

    /**
     * A transformation showing the weapon on an altar, {@code height} blocks tall, centred on the
     * display entity and turned {@code yaw} radians around the vertical axis. Swords hang
     * point-down; hammers, axes, staves and the rest are shown head-up.
     */
    Transformation onAltar(SwordType type, float height, float yaw) {
        Bounds b = bounds.getOrDefault(type, FALLBACK);
        float scale = Math.min(4f, height / b.height());
        Quaternionf rotation = new Quaternionf().rotateY(yaw);
        if (type.isSword()) {
            rotation.rotateZ((float) Math.PI);
        }
        // item displays draw the model around its (8, 8, 8); shift so the model's own centre is there instead
        Vector3f offset = new Vector3f(b.center()).sub(8, 8, 8).mul(scale / 16f);
        Vector3f translation = rotation.transform(offset).negate();
        return new Transformation(translation, rotation, new Vector3f(scale), new Quaternionf());
    }
}
