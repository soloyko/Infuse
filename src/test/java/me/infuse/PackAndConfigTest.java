package me.infuse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.infuse.effect.EffectType;
import me.infuse.util.Glyphs;
import me.infuse.util.Msg;
import me.infuse.util.Style;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the plugin's assumptions against the bundled resource pack and default config files. */
class PackAndConfigTest {
    private static final Path PACK = Path.of("resourcepack/assets/infuse");
    private static final File RES = new File("src/main/resources");

    @BeforeAll
    static void loadMessages() throws Exception {
        Field f = Msg.class.getDeclaredField("messages");
        f.setAccessible(true);
        f.set(null, YamlConfiguration.loadConfiguration(new File(RES, "messages.yml")));
    }

    private static JsonArray chars(String font) throws Exception {
        JsonObject json = JsonParser.parseString(Files.readString(PACK.resolve("font/" + font + ".json"))).getAsJsonObject();
        return json.getAsJsonArray("providers").get(0).getAsJsonObject().getAsJsonArray("chars");
    }

    @Test
    void effectIconGlyphsMatchFont() throws Exception {
        JsonArray rows = chars("effects");
        assertEquals(EffectType.values().length * 4, rows.size(), "one block of 4 rows per effect");
        for (EffectType t : EffectType.values()) {
            for (int variant = 0; variant < 4; variant++) {
                String row = rows.get(t.iconIndex() * 4 + variant).getAsString();
                for (int frame = 0; frame < Glyphs.FRAMES; frame++) {
                    char expected = row.charAt(frame);
                    char actual = Glyphs.effect(t, (variant & 1) != 0, (variant & 2) != 0, frame);
                    assertEquals(expected, actual, t + " variant " + variant + " frame " + frame);
                }
            }
        }
    }

    @Test
    void hitTrackerGlyphsMatchFont() throws Exception {
        JsonArray rows = chars("hit_tracker");
        for (EffectType t : EffectType.values()) {
            String row = rows.get(t.iconIndex()).getAsString();
            for (int col = 0; col < 14; col++) {
                assertEquals(row.charAt(col), Glyphs.hitTracker(t, col), t + " column " + col);
            }
        }
    }

    @Test
    void everyRecipeUsesRealItemsAndKnownEffects() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(RES, "recipes.yml"));
        ConfigurationSection root = yml.getConfigurationSection("recipes");
        assertNotNull(root);
        Set<EffectType> covered = new HashSet<>();
        Set<String> shapes = new HashSet<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection r = root.getConfigurationSection(id);
            EffectType type = EffectType.fromId(r.getString("effect", id));
            assertNotNull(type, "recipe " + id + " has an unknown effect");
            covered.add(type);
            List<String> shape = r.getStringList("shape");
            assertTrue(shape.size() >= 1 && shape.size() <= 3, id + " shape rows");
            ConfigurationSection ing = r.getConfigurationSection("ingredients");
            StringBuilder signature = new StringBuilder();
            for (String row : shape) {
                assertTrue(row.length() <= 3, id + " shape row too long: " + row);
                for (char c : row.toCharArray()) {
                    if (c == ' ') { signature.append('_'); continue; }
                    assertTrue(ing.contains(String.valueOf(c)), id + " uses undefined symbol " + c);
                    List<String> mats = ing.isList(String.valueOf(c)) ? ing.getStringList(String.valueOf(c))
                            : List.of(ing.getString(String.valueOf(c)));
                    for (String m : mats) {
                        Material mat = Material.matchMaterial(m);
                        assertNotNull(mat, id + ": unknown item " + m);
                        assertFalse(mat.isLegacy(), id + ": legacy material " + m);
                    }
                    signature.append(String.join("|", mats)).append(',');
                }
                signature.append('/');
            }
            assertTrue(shapes.add(signature.toString()), "recipe " + id + " duplicates another recipe's grid");
        }
        for (EffectType t : EffectType.values()) assertTrue(covered.contains(t), t + " has no recipe");
    }

    @Test
    void everyMessageParses() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(RES, "messages.yml"));
        var plain = PlainTextComponentSerializer.plainText();
        for (String key : yml.getKeys(false)) {
            String text = plain.serialize(Msg.get(key,
                    Msg.comp("effect", Style.effectName(EffectType.THIEF, true)), Msg.text("player", "Steve"),
                    Msg.text("time", "0:08"), Msg.text("target", "Alex"), Msg.text("coords", "0, 64, 0 (world)"),
                    Msg.text("count", 1), Msg.text("max", 4), Msg.text("potion", "speed"), Msg.text("text", "x"),
                    Msg.comp("slot", Msg.get("slot-primary")), Msg.text("health", "10"), Msg.text("amount", "2"),
                    Msg.comp("absorption", Msg.get("heart-absorption", Msg.text("amount", "2")))));
            assertFalse(text.contains("<caps>") || text.contains("</caps>"), key + " left a caps tag: " + text);
        }
    }

    @Test
    void configHasCooldownForEveryEffect() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(RES, "config.yml"));
        for (EffectType t : EffectType.values()) {
            assertTrue(cfg.contains("cooldowns." + t.id()), "missing cooldowns." + t.id());
        }
    }

    @Test
    void smallCaps() {
        assertEquals("ᴛʜɪᴇꜰ ᴇꜰꜰᴇᴄᴛ", Style.forceCaps("Thief Effect"));
        assertEquals("123 !", Style.forceCaps("123 !"));
    }
}
