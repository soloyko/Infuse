package dev.blackclover.smp.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmallCapsTest {

    @Test
    void lettersBecomeSmallCaps() {
        assertEquals("ꜰɪʀᴇ ɢʀɪᴍᴏɪʀᴇ", SmallCaps.of("Fire Grimoire"));
        assertEquals("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ", SmallCaps.of("abcdefghijklmnopqrstuvwxyz"));
    }

    @Test
    void digitsAndPunctuationStay() {
        assertEquals("ʟᴠ 20 · 3.5ꜱ!", SmallCaps.of("Lv 20 · 3.5s!"));
    }

    @Test
    void miniMessageTagsAreUntouched() {
        assertEquals("<gray>ᴍᴀɴᴀ</gray> <spell> <c>ᴏᴋ</c>", SmallCaps.of("<gray>Mana</gray> <spell> <c>ok</c>"));
    }
}
