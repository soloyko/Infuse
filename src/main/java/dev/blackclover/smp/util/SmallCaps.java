package dev.blackclover.smp.util;

/** Converts text to Unicode small capitals (ꜱᴍᴀʟʟ ᴄᴀᴘꜱ), leaving MiniMessage tags like {@code <gray>} untouched. */
public final class SmallCaps {

    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private SmallCaps() {
    }

    public static String of(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inTag = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            }
            if (!inTag) {
                char lower = Character.toLowerCase(c);
                if (lower >= 'a' && lower <= 'z') {
                    out.append(SMALL.charAt(lower - 'a'));
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
