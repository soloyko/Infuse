package dev.blackclover.smp.magic;

/** A spell: identity, role, balance numbers (overridable in config), a short description and its action. */
public record Spell(String id, String name, int unlockLevel, Role role, double cost, double cooldownSeconds,
                    double damage, String description, SpellAction action) {

    /** What a spell is for; every grimoire has an Attack first and an Ultimate last. */
    public enum Role {
        ATTACK("ᴀᴛᴛᴀᴄᴋ", 0xFF6B6B),
        MOBILITY("ᴍᴏʙɪʟɪᴛʏ", 0x6BD5FF),
        DEFENSE("ᴅᴇꜰᴇɴꜱᴇ", 0x7CFF8A),
        CONTROL("ᴄᴏɴᴛʀᴏʟ", 0xC08BFF),
        SUPPORT("ꜱᴜᴘᴘᴏʀᴛ", 0xFFE36B),
        ULTIMATE("ᴜʟᴛɪᴍᴀᴛᴇ", 0xFFB020);

        private final String label;
        private final int rgb;

        Role(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }

        public String label() {
            return label;
        }

        public int rgb() {
            return rgb;
        }
    }

    public Spell withStats(double cost, double cooldownSeconds, double damage) {
        return new Spell(id, name, unlockLevel, role, cost, cooldownSeconds, damage, description, action);
    }
}
