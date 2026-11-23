package dev.blackclover.smp.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Checks that every model, texture and item-definition reference in the pack resolves. */
public final class PackLinter {

    private PackLinter() {
    }

    public static List<String> lint(Map<String, byte[]> files) {
        List<String> problems = new ArrayList<>();
        JsonObject meta = parse(files, "pack.mcmeta", problems);
        if (meta != null && (!meta.has("pack") || !meta.getAsJsonObject("pack").has("pack_format"))) {
            problems.add("pack.mcmeta: missing pack.pack_format");
        }
        for (String path : files.keySet()) {
            if (path.matches("assets/[^/]+/models/.+\\.json")) {
                lintModel(files, path, problems);
            } else if (path.matches("assets/[^/]+/items/.+\\.json")) {
                JsonObject def = parse(files, path, problems);
                if (def != null) {
                    walkItemModel(files, path, def.get("model"), problems);
                }
            }
        }
        return problems;
    }

    private static JsonObject parse(Map<String, byte[]> files, String path, List<String> problems) {
        byte[] data = files.get(path);
        if (data == null) {
            problems.add(path + ": missing");
            return null;
        }
        try {
            return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            problems.add(path + ": invalid JSON (" + e.getMessage() + ")");
            return null;
        }
    }

    private static void lintModel(Map<String, byte[]> files, String path, List<String> problems) {
        JsonObject model = parse(files, path, problems);
        if (model == null) {
            return;
        }
        JsonObject textures = model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();
        for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
            String ref = t.getValue().getAsString();
            if (!ref.startsWith("#") && !exists(files, ref, "textures", ".png")) {
                problems.add(path + ": texture " + ref + " not found");
            }
        }
        if (model.has("elements") && !model.has("parent")) {
            for (JsonElement el : model.getAsJsonArray("elements")) {
                JsonObject faces = el.getAsJsonObject().getAsJsonObject("faces");
                for (Map.Entry<String, JsonElement> f : faces.entrySet()) {
                    String tex = f.getValue().getAsJsonObject().get("texture").getAsString();
                    if (tex.startsWith("#") && !textures.has(tex.substring(1))) {
                        problems.add(path + ": face " + f.getKey() + " uses undefined " + tex);
                    }
                }
            }
        }
    }

    private static void walkItemModel(Map<String, byte[]> files, String path, JsonElement node, List<String> problems) {
        if (node == null || !node.isJsonObject()) {
            return;
        }
        JsonObject obj = node.getAsJsonObject();
        if (obj.has("type") && obj.get("type").getAsString().equals("minecraft:model")) {
            String ref = obj.get("model").getAsString();
            if (!exists(files, ref, "models", ".json")) {
                problems.add(path + ": model " + ref + " not found");
            }
        }
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (e.getValue().isJsonObject()) {
                walkItemModel(files, path, e.getValue(), problems);
            } else if (e.getValue().isJsonArray()) {
                for (JsonElement child : e.getValue().getAsJsonArray()) {
                    walkItemModel(files, path, child, problems);
                }
            }
        }
    }

    /** Vanilla (minecraft:) references are assumed to exist in the game itself. */
    private static boolean exists(Map<String, byte[]> files, String ref, String kind, String ext) {
        String ns = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : "minecraft";
        String p = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
        return ns.equals("minecraft") || files.containsKey("assets/" + ns + "/" + kind + "/" + p + ext);
    }
}
