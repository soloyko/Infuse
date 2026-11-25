package dev.lambro.blade.util;

/** Converts plain text into the small-caps unicode "font" (ᴛɪɴʏ ᴄᴀᴘꜱ). */
public final class TinyCaps {

    private static final String FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String[] TO = {
            "ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ",
            "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ꜱ", "ᴛ", "ᴜ", "ᴠ", "ᴡ", "x", "ʏ", "ᴢ"
    };

    private TinyCaps() {}

    public static String of(String input) {
        if (input == null) return "";
        StringBuilder out = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            int idx = FROM.indexOf(Character.toLowerCase(c));
            out.append(idx >= 0 ? TO[idx] : String.valueOf(c));
        }
        return out.toString();
    }
}
