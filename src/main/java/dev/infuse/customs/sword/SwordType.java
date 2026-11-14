package dev.infuse.customs.sword;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * The weapons: twenty-one swords and nineteen other weapons. The id is the item model name in the resource pack
 * ({@code customs:<id>}) and the key in config.yml; the name is the sword's legendary name.
 */
public enum SwordType {
    DRAGON("dragon", "Dragon Slaying Blade", "Dragon Sword", "🐉", 0xD8344A, 0xF5C451,
            "Forged to slay the last elder dragon, it drank the beast's soul instead. When you swing it, something old and furious breathes with you.",
            new AbilityInfo("Dragon Scales", "Resistance I and +50% knockback resistance while held."),
            new AbilityInfo("Dragon's Fury", "25% chance to scorch the target for +4 bonus damage."),
            new AbilityInfo("Dragon Breath", "Exhale a cone of dragonfire that shreds everything in front of you."),
            new AbilityInfo("Wyrm Dive", "Soar into the sky, then crash down in a shockwave. No fall damage.")),

    PHOENIX("phoenix", "Phoenix Grace", "Phoenix Sword", "🔥", 0xF28A1E, 0xFFE27A,
            "A feather of the first phoenix, beaten into a blade over a fire that never went out. Whoever carries it rises from their own ashes.",
            new AbilityInfo("Rebirth", "Fire Resistance while held. Once every 5 minutes, a killing blow revives you instead."),
            new AbilityInfo("Searing Edge", "Ignites targets. 15% chance to combust, burning everything nearby."),
            new AbilityInfo("Ember Volley", "Three exploding fireballs that set the ground they pass over on fire for a few seconds."),
            new AbilityInfo("Wall of Fire", "Raise a wall of fire across where you aim for 6 seconds: anything crossing it burns and is thrown back, and arrows burn up in it.")),

    FROST("frost", "Frost Blade", "Frost Sword", "❄", 0x9BE3F2, 0xFFFFFF,
            "Carved from ice that has not melted since the world was young. The wounds it leaves never quite stop freezing.",
            new AbilityInfo("Frostguard", "+4 armor, +2 toughness and immunity to freezing while held."),
            new AbilityInfo("Frostbite", "Slows and freezes the target."),
            new AbilityInfo("Glacial Spike", "Fire an ice spike that impales and encases the first enemy hit."),
            new AbilityInfo("Blizzard", "Summon a 6 second blizzard around you that freezes and batters enemies.")),

    STORM("storm", "Thunder Bringer", "Storm Sword", "⚡", 0x4DB2FF, 0xF5C842,
            "It hung on the highest peak for a thousand years, struck by every storm that passed. In the end, the storms began to answer it.",
            new AbilityInfo("Tailwind", "+25% movement speed and immunity to lightning while held."),
            new AbilityInfo("Static Charge", "12% chance to call lightning on the target for +5 damage."),
            new AbilityInfo("Thunder Strike", "Call a lightning bolt down wherever you're looking."),
            new AbilityInfo("Chain Lightning", "Lightning arcs between up to 6 nearby enemies.")),

    TIDE("tide", "Aquatic Sacred Blade", "Tide Sword", "🌊", 0x2EC4B6, 0xA8FFF3,
            "Lost with a drowned kingdom and given back by the sea. It still carries the weight of the deep, and the deep still follows it.",
            new AbilityInfo("Ocean's Blessing", "Conduit Power and Dolphin's Grace while held."),
            new AbilityInfo("Undertow", "20% chance to drag the target towards you and slow it."),
            new AbilityInfo("Riptide Surge", "Surge forward like a riptide, slamming through every enemy in your path."),
            new AbilityInfo("Ride the Tide", "Surf a wall of water for 3.5 seconds, steering with your crosshair. Enemies in your path are swept up and carried until it breaks.")),

    VERDANT("verdant", "Enigma", "Verdant Sword", "🌿", 0x2FBF71, 0xE8C64A,
            "Grown, not forged, from the heartwood of the world's oldest tree. Wherever it fights, roots break through the ground.",
            new AbilityInfo("Verdant Life", "Regeneration I while held. Mobs you slay drop double XP."),
            new AbilityInfo("Thorned Edge", "25% chance to poison the target."),
            new AbilityInfo("Nature's Grasp", "Roots erupt around you, damaging and rooting enemies in place."),
            new AbilityInfo("Verdant Bloom", "Heal, gain Absorption and cleanse all negative effects. Shared with teammates.")),

    PLAGUE("plague", "Plague Longsword", "Plague Sword", "☣", 0x8DC63F, 0x4A5A3A,
            "Quenched in the blight that emptied a hundred villages. Every scratch it leaves festers, and every festering wound spreads.",
            new AbilityInfo("Plaguebearer", "Immune to poison, wither, hunger and nausea while held. The plague you spread even rots the undead."),
            new AbilityInfo("Infection", "Infects the target with plague. Striking an infected target withers it for +2 damage."),
            new AbilityInfo("Miasma", "Hurl a lingering plague cloud that infects and weakens enemies inside."),
            new AbilityInfo("Pandemic", "Every infected enemy nearby erupts. The deeper the infection, the bigger the burst.")),

    CRIMSON("crimson", "Vampiric Needle", "Crimson Sword", "❤", 0xE0245E, 0x7A0F2A,
            "A duelist's needle that learned to drink. It gives every drop it draws back to the hand that holds it.",
            new AbilityInfo("Blood Reserve", "+3 max hearts while held."),
            new AbilityInfo("Bloodthirst", "Heal 15% of damage dealt. 25% chance to make the target bleed."),
            new AbilityInfo("Blood Crescent", "Launch a crimson slash that cuts through every enemy in its path."),
            new AbilityInfo("Blood Bond", "Chain up to four nearby enemies to you for 6 seconds: they can't escape the chains, every second drains them to heal you, and the chains tear them when they snap.")),

    SOLAR("solar", "Righteous Relic", "Solar Sword", "☀", 0xF2C94C, 0xFFF6C9,
            "Blessed at dawn by the last of the sun-priests and kept in light ever since. The dead burn at the mere sight of it.",
            new AbilityInfo("Radiance", "Haste II while held. Nearby enemies are revealed with Glowing."),
            new AbilityInfo("Sunfire", "Ignites targets. Undead take +50% damage."),
            new AbilityInfo("Solar Beam", "Channel a beam of sunlight for 1.5 seconds that follows your aim, burning everything it sweeps across."),
            new AbilityInfo("Supernova", "Charge up, then explode in a blinding blast of solar fire.")),

    VOID("void", "Demon Lord's Sword", "Void Sword", "🌌", 0xA64DFF, 0x3A1A5C,
            "Pulled from the empty space between the stars. Light bends around its edge and never comes back out.",
            new AbilityInfo("Void Walker", "Night Vision while held. The void can't claim you while you carry this blade."),
            new AbilityInfo("Void Drain", "20% chance to wither the target and steal 2 health."),
            new AbilityInfo("Void Step", "Blink up to 15 blocks forward, tearing at anything where you land."),
            new AbilityInfo("Singularity", "Open a black hole that drags enemies in, then implodes.")),

    COSMIC("cosmic", "Edge Of The Astral Plane", "Cosmic Sword", "✨", 0xC54DFF, 0x4DE8FF,
            "Hammered from the core of a fallen star, still warm after ten thousand years. Gravity itself kneels to whoever holds it.",
            new AbilityInfo("Zero-G", "Lower gravity and no fall damage while held."),
            new AbilityInfo("Starstrike", "20% chance to launch the target into orbit with Levitation."),
            new AbilityInfo("Telekinesis", "Seize the enemy under your crosshair and drag it through the air with your aim for 3 seconds, smashing it into others and walls, then hurl it."),
            new AbilityInfo("Orbit", "Pull up to five nearby enemies into orbit around you for 5 seconds: they circle you as you move, smashing into anyone in the way, then fly off.")),

    CHRONO("chrono", "Chrono Blade", "Chrono Sword", "⌛", 0x7FE0D0, 0xE8C35A,
            "Its smith worked on it for a thousand years, then went back and finished it in an afternoon. Time moves however its wielder prefers.",
            new AbilityInfo("Quickened", "+25% attack speed while held."),
            new AbilityInfo("Time Lock", "15% chance to freeze the target in time for a moment."),
            new AbilityInfo("Stasis Field", "Freeze time in an area: enemies crawl and projectiles stop in mid-air."),
            new AbilityInfo("Rewind", "Return to where you were 4 seconds ago, with the health you had then.")),

    SHADOW("shadow", "Watcher Claymore", "Shadow Sword", "🌑", 0x7B6BE0, 0x1B1B3A,
            "The blade of a watcher who was never once seen. It is darkest exactly where you are not looking.",
            new AbilityInfo("Umbral Cloak", "Invisible while sneaking, plus faster movement while held."),
            new AbilityInfo("Backstab", "+60% damage when striking from behind. 20% chance to blind."),
            new AbilityInfo("Shadow Strike", "Teleport behind the enemy you're looking at and strike."),
            new AbilityInfo("Shadow Veil", "Vanish completely (armor too) for 6s. Your next hit deals +8 damage.")),

    SOUL("soul", "Awakened Lichblade", "Soul Sword", "💀", 0x6FD8F0, 0x1F3A4A,
            "A lich's blade, still humming with every soul it ever took. Some of them are eager to hunt for you.",
            new AbilityInfo("Soul Siphon", "Every kill heals 4 health and grants Absorption."),
            new AbilityInfo("Soul Rend", "20% chance to tear at the target's soul for +4 damage."),
            new AbilityInfo("Spectral Hounds", "Summon three spectral hounds that hunt your target for 15 seconds."),
            new AbilityInfo("Wail of the Damned", "Wither, weaken and scatter enemies around you; six screaming skulls then hunt them down.")),

    ECHO("echo", "Sculk Sword", "Echo Sword", "🔊", 0x22C2C7, 0x0B2A33,
            "Grown in the sculk beneath the Deep Dark, where silence is a weapon. It hears your heartbeat before you even swing.",
            new AbilityInfo("Child of the Deep", "Immune to Darkness. Sculk sensors and shriekers ignore you while held."),
            new AbilityInfo("Resonance", "20% chance to engulf the target in darkness."),
            new AbilityInfo("Sonic Boom", "Charge, then release a warden's sonic boom that pierces every enemy in a line."),
            new AbilityInfo("Shriek", "A sculk shriek darkens and reveals every enemy around you, and quickens you.")),

    // ---- weapons that aren't swords ----

    HAMMER("hammer", "Hearthflame", "Titan Hammer", "⚒", 0xE8822A, 0x6B6B6B, Material.MACE,
            "Forged in a dead volcano by giants who wanted mountains to kneel. Strike the ground with it, and the ground remembers.",
            new AbilityInfo("Unshakable", "Immune to knockback and +3 armor while held. Keeps the mace's smash attack."),
            new AbilityInfo("Quake", "20% chance to send out a shockwave that stuns everything around the target."),
            new AbilityInfo("Seismic Slam", "Slam the ground: a ring of erupting earth launches everything around you."),
            new AbilityInfo("Earthen Bulwark", "Raise stone pillars around you. Resistance II, and attackers are hurt and thrown back.")),

    SCYTHE("scythe", "Requiem of the Ninth Abyss", "Reaper's Scythe", "⚰", 0x2BD9A8, 0x0E3B33, Material.NETHERITE_SWORD,
            "The ninth abyss sings through its curved blade. Every soul it reaps adds another voice to the choir.",
            new AbilityInfo("Death's Embrace", "Kills refresh Reap and grant a burst of speed."),
            new AbilityInfo("Rend", "+50% damage against targets below 35% health."),
            new AbilityInfo("Reap", "A wide sweeping arc that cuts every enemy in front of you and heals you for each."),
            new AbilityInfo("Doom", "Mark the enemy under your crosshair for 5 seconds. When the count ends a giant scythe takes 6 plus three quarters of all the damage it took while marked.")),

    SPEAR("spear", "Sunbreak", "Celestial Spear", "♆", 0xE9C46A, 0xC0392B, Material.NETHERITE_SPEAR,
            "Hurled from the heavens to end a war between gods, it fell for an age. It has been searching for a worthy arm ever since.",
            new AbilityInfo("Long Reach", "+1.5 blocks of attack reach while held. Keeps the spear's charge attack."),
            new AbilityInfo("Impale", "20% chance to pin the target in place for +3 damage."),
            new AbilityInfo("Skyhook", "Throw the spear on a chain: an enemy it hits is dragged to you, and a wall or ground it hits pulls you to it."),
            new AbilityInfo("Phalanx", "Fans of spears erupt from the ground in a line ahead, ending in a giant spear that launches enemies.")),

    CHAKRAM("chakram", "Royal Chakram", "Wind Chakram", "☸", 0x4CC38A, 0xE8C64A, Material.NETHERITE_SWORD,
            "A royal war-disc blessed by all four winds. Thrown true, it always finds its way home.",
            new AbilityInfo("Windborne", "Double jump while held. No fall damage after a double jump."),
            new AbilityInfo("Gust", "20% chance to blast the target away with a burst of wind."),
            new AbilityInfo("Halo Throw", "Throw the chakram: it slices through enemies on the way out and on the way back."),
            new AbilityInfo("Cyclone", "Three spectral chakrams orbit you, shredding enemies and deflecting arrows.")),

    AXE("axe", "Demon Lord's Great Axe", "Berserker Axe", "ᛉ", 0xC77DFF, 0x5A1E6E, Material.NETHERITE_AXE,
            "The axe that will end the world, carved with runes of doom. It grows hungrier the closer its wielder comes to death.",
            new AbilityInfo("Bloodlust", "Up to +50% melee damage the lower your health. Disables shields like any axe."),
            new AbilityInfo("Cleave", "Every hit also strikes enemies beside the target for 40% of the damage."),
            new AbilityInfo("Whirlwind", "Spin with the axe for 2.5 seconds, carving through everything around you."),
            new AbilityInfo("Warcry", "A giant spectral axe crashes down ahead. You gain Strength II and Resistance; enemies are weakened and slowed.")),

    STAFF("staff", "Arcanethyst", "Arcane Staff", "⚚", 0xB57EDC, 0xF3D9FF, Material.NETHERITE_SWORD,
            "Grown from a single amethyst that hummed with magic for ten thousand years. It still hums, and now it answers.",
            new AbilityInfo("Arcane Ward", "A 2-heart magic ward that recharges 12 seconds after it breaks."),
            new AbilityInfo("Arcane Spark", "25% chance for a spark to leap to another nearby enemy."),
            new AbilityInfo("Arcane Missiles", "Fire five homing amethyst missiles at the enemies in front of you."),
            new AbilityInfo("Crystal Sentinel", "Conjure a floating crystal where you aim: for 8 seconds it fires at nearby enemies and mends you and your teammates near it.")),

    TRIDENT("trident", "Ocean's Rage", "Abyssal Trident", "Ψ", 0x2EC4C4, 0x0B4F6C, Material.TRIDENT,
            "Raised from the trench where the old sea god sleeps. Wherever it is thrown, the storm follows.",
            new AbilityInfo("Stormborn", "Always returns when thrown, and calls down lightning where it hits, in any weather. Water Breathing while held."),
            new AbilityInfo("Undertow", "20% chance to drag the target under: +3 damage and Slowness III."),
            new AbilityInfo("Abyssal Bubble", "Trap the enemy under your crosshair in a bubble of seawater: it floats helpless and drowning for 3 seconds, then the bubble bursts and slams it down."),
            new AbilityInfo("Maelstrom", "A whirlpool drags enemies around and in for four seconds, then erupts as a geyser.")),

    SHURIKEN("shuriken", "Amethyst Shuriken", "Throwing Star", "✴", 0xE040FB, 0x7B1FA2, Material.NETHERITE_SWORD,
            "Four petals of living amethyst, folded by a clan that was never seen. It blooms only in the dark, and never twice in the same place.",
            new AbilityInfo("Light Feet", "Speed I while held."),
            new AbilityInfo("Petal Cut", "25% chance to fling a spare shuriken at another nearby enemy."),
            new AbilityInfo("Star Fan", "Throw five spinning shuriken in a fan; each one ricochets to a second enemy."),
            new AbilityInfo("Thousand Cuts", "Flash between up to six nearby enemies, cutting each one, then return to where you started.")),

    HALBERD("halberd", "Jade Halberd", "Imperial Halberd", "⚜", 0x3FBF7F, 0xE8C35A, Material.NETHERITE_SPEAR,
            "Carried by the emperor's last guard, who held the gate alone for a hundred days. The jade remembers every enemy that fell before it.",
            new AbilityInfo("Imperial Guard", "+1 block of reach and +4 armor while held. Keeps the spear's charge attack."),
            new AbilityInfo("Jade Cleave", "25% chance to sweep everything in front of you for half the damage."),
            new AbilityInfo("Vault", "Plant the halberd and vault over the enemies ahead, kicking those you pass over and cleaving everything where you land."),
            new AbilityInfo("Jade Dragon", "Release a jade dragon you steer with your crosshair for 4.5 seconds, biting and tossing every enemy it passes through.")),

    WARGLAIVE("warglaive", "Watching Warglaive", "Lunar Warglaive", "☾", 0x8FA8FF, 0x2B2F6B, Material.NETHERITE_SWORD,
            "Forged on the one night the moon swallowed the sun. It has been hungry for light ever since.",
            new AbilityInfo("Moonlit", "Night Vision while held. Melee hits deal +25% damage at night or in the dark."),
            new AbilityInfo("Crescent Echo", "20% chance for a spectral crescent to hit the target again a moment later."),
            new AbilityInfo("Lunar Dash", "Dash forward through your enemies, leaving a trail of silver crescents."),
            new AbilityInfo("Moongate", "Open two moon gates for 8 seconds, one beside you and one where you aim. Anything that goes into one comes out of the other: you, enemies, even arrows.")),

    SAWBLADE("sawblade", "Cybernetic Chainsaw Blade", "Cyber Sawblade", "⚙", 0xFF7A1A, 0x29E0FF, Material.NETHERITE_SWORD,
            "A prototype from a future that never happened. Nobody remembers who built it, but everyone remembers the noise.",
            new AbilityInfo("Overclock", "Hits in quick succession build heat. At 5 heat your next hit vents for +6 damage."),
            new AbilityInfo("Arc Spark", "25% chance for electricity to jump to two nearby enemies."),
            new AbilityInfo("Grinder", "Rev the blade for two seconds, shredding everything in front of you."),
            new AbilityInfo("EMP Overload", "Release an electromagnetic pulse: enemies are stunned and revealed, and you speed up.")),

    RAPIER("rapier", "Royal Rapier", "Duelist's Rapier", "♛", 0xF5E6B8, 0xD4A017, Material.NETHERITE_SWORD,
            "The queen's own blade, which never lost a duel. It asks only that you strike first, and strike true.",
            new AbilityInfo("Finesse", "+25% attack speed and +10% movement speed while held."),
            new AbilityInfo("Precision", "+50% damage against enemies at full health."),
            new AbilityInfo("Flèche", "Lunge forward and run the first enemy in your path through."),
            new AbilityInfo("Riposte", "Take a parry stance: the next attack against you is blocked and answered with a flurry of thrusts.")),

    KHOPESH("khopesh", "Pharaoh's Treasure", "Pharaoh's Khopesh", "☥", 0xE3B341, 0x1FA3A3, Material.NETHERITE_SWORD,
            "Buried with a pharaoh who refused to stay buried. When it is raised, the desert rises with it.",
            new AbilityInfo("Sun-Blessed", "Regeneration I in daylight while held."),
            new AbilityInfo("Pharaoh's Curse", "20% chance to curse the target: Weakness, Slowness and +20% damage from you for 5 seconds."),
            new AbilityInfo("Sandstorm", "Send out a sand tornado that drifts wherever your crosshair points for 4 seconds, lifting, blinding and grinding enemies."),
            new AbilityInfo("Tomb of Sands", "Seal nearby enemies in sandstone tombs, then collapse them.")),

    // ---- swords added in 2.3 ----
    KATANA("katana", "Katana", "Ronin's Katana", "刀", 0xE6E9F0, 0xD32F2F,
            "A masterless blade that waits in its scabbard like held breath. It is drawn once, and once is enough.",
            new AbilityInfo("Iaido", "Your first hit after 3 seconds without attacking is a drawn cut: +60% damage."),
            new AbilityInfo("Bleeding Edge", "25% chance to open a wound that bleeds for 4 seconds."),
            new AbilityInfo("Draw Cut", "Flash up to 10 blocks forward. A heartbeat later, every enemy you passed is cut at once."),
            new AbilityInfo("Moonlit Duel", "Challenge the enemy under your crosshair: a ring of moonlight traps you both for 8 seconds, and your hits on it deal +40% damage.")),

    NEEDLE("needle", "Hornet's Needle", "Hive Needle", "✢", 0xF5C518, 0x3A3A3A,
            "Carved from the queen's own stinger. Wherever it goes, the hive hums along behind it.",
            new AbilityInfo("Gossamer", "Hold sneak in the air to drift down slowly."),
            new AbilityInfo("Venom", "Every hit adds venom (up to 6 stacks): poison that lasts longer with each one."),
            new AbilityInfo("Swarm Form", "Burst into a swarm of hornets for 3 seconds: fly wherever you look, untouchable, stinging everything you pass through."),
            new AbilityInfo("Hive Mind", "Enemies within 12 blocks carrying your venom turn on each other for 6 seconds.")),

    CANDLE("candle", "Waxweaver", "Waxen Blade", "✵", 0xFFE3A3, 0xFF8C2A,
            "Poured from the candles of a chapel that kept watch for a hundred years. Its flame never gutters.",
            new AbilityInfo("Warm Glow", "Night Vision and Fire Resistance while held."),
            new AbilityInfo("Hot Wax", "30% chance to splash hot wax: Slowness that deepens with every splash, up to III."),
            new AbilityInfo("Wickfire", "Light a fuse to the enemy under your crosshair: a spark races along the ground after it and explodes when it catches up."),
            new AbilityInfo("Melt", "Coat every enemy within 8 blocks in wax for 8 seconds. Your next hit on a waxed enemy sets all of them alight at once.")),

    CLAYMORE("claymore", "Brimstone Claymore", "Volcanic Claymore", "♨", 0xFF5A1F, 0x2B1B17,
            "Hewn from the heart of a mountain that would not stop burning. It is heavy, and it is angry.",
            new AbilityInfo("Magma Blood", "Fire Resistance while held. Standing in lava heals you."),
            new AbilityInfo("Molten Splash", "25% chance to splash molten rock: the target and enemies beside it catch fire."),
            new AbilityInfo("Geyser Leap", "A lava geyser bursts under you and hurls you into the sky, leaving a molten pool that burns enemies for 5 seconds."),
            new AbilityInfo("Obsidian Skin", "Encase yourself in obsidian for 6 seconds: 60% less damage, no knockback, attackers burn. Then it shatters into cutting shards.")),

    MIRROR("mirror", "Silverine Blade", "Silver Mirror Blade", "◈", 0xC9D6E3, 0x8FB8FF,
            "Polished until it forgot which side of itself was real. Neither do the people who fight it.",
            new AbilityInfo("Glass Skin", "15% chance for an attack to pass through a mirror image and miss you entirely."),
            new AbilityInfo("Shatter", "20% chance for mirror shards to burst from the target and cut enemies around it."),
            new AbilityInfo("Mirror Images", "Two mirror copies of you step out for 8 seconds and fight at your side. Enemies go after them, and they burst into glass when broken."),
            new AbilityInfo("Reflection", "For 3 seconds every hit you take is turned back on whoever dealt it, and you take none of it.")),

    CUTLASS("cutlass", "Sabre", "Corsair's Cutlass", "⚓", 0xC9A227, 0x1F4E79,
            "Taken from a captain who swore the sea itself owed him a debt. The sea is still paying.",
            new AbilityInfo("Plunder", "Kills with the cutlass have a chance to drop gold, and Dolphin's Grace while held."),
            new AbilityInfo("Cutthroat", "20% chance for a backhand cut: half damage again and Slowness."),
            new AbilityInfo("Powder Keg", "Roll out a powder keg where you aim. Hit it to blow it up, or it goes off by itself after 6 seconds."),
            new AbilityInfo("Kraken's Grasp", "Tentacles burst out of the ground where you aim, seize up to three enemies, slam them into the ground twice and fling them away.")),

    // ---- swords added in 2.5 ----
    RIBBON("ribbon", "Ribbon Cleaver", "Puppeteer's Cleaver", "☙", 0xE8446A, 0xF2F2F2,
            "Its ribbons were cut from the strings of a puppet theatre that burned with its audience inside. They still remember how to make things dance.",
            new AbilityInfo("Graceful", "Speed I while sprinting."),
            new AbilityInfo("Entangle", "20% chance for a ribbon to wrap the target: Slowness III for 2 seconds."),
            new AbilityInfo("Marionette", "Tie ribbons to the enemy under your crosshair for 5 seconds: every step you take, it takes too, wherever that leads it."),
            new AbilityInfo("Tangle", "Tie up to five nearby enemies together for 6 seconds: they can't pull apart, and half the damage any of them takes goes to the others.")),

    STARS("stars", "Star's Edge", "Constellation Blade", "✧", 0x5CF2C2, 0x6A4BD6,
            "Cut from the night sky on the one evening it held still. Wherever it strikes, a new star is born.",
            new AbilityInfo("Stargazer", "Your hits deal +20% damage at night."),
            new AbilityInfo("Star Mark", "Every hit sets a star over the target for 8 seconds."),
            new AbilityInfo("Constellation", "Link every starred enemy within 16 blocks with starlight, then pull them all into a collapsing star. Bigger constellations hit harder."),
            new AbilityInfo("Astral Projection", "Step out of your body for 5 seconds and fly as a spirit nothing can hurt, starring every enemy you pass, then snap back to your body.")),

    ASHURA("ashura", "Ashura's Blade", "Six-Armed Blade", "✹", 0xE5482C, 0xFFC94A,
            "Wielded by a war god with six arms and no mercy. Hold it and you will feel the other five.",
            new AbilityInfo("Wrath", "+1 damage for each enemy within 5 blocks of you, up to +5."),
            new AbilityInfo("Echo Arm", "25% chance for a spectral arm to strike the target again for half damage."),
            new AbilityInfo("Thousand Hands", "Four spectral arms rise behind you for 8 seconds. Every time you hit, each arm strikes an enemy near you."),
            new AbilityInfo("Wrathful Form", "Grow to twice your size for 8 seconds: more reach, +5 damage, no knockback, and every step shakes the ground.")),

    DEATHKNIGHT("deathknight", "Death Knight's Sword", "Runed Greatsword", "♞", 0x5DA9E9, 0x0D1B2A,
            "Sworn to a king long dead, and still keeping its oath. The fallen get back up when it asks them to.",
            new AbilityInfo("Frost Presence", "Enemies that strike you are slowed."),
            new AbilityInfo("Death Coil", "20% chance to drain 2 health from the target into you."),
            new AbilityInfo("Raise Dead", "Raise up to three skeleton warriors from creatures slain near you (or from the ground). They fight for you for 20 seconds."),
            new AbilityInfo("Deathcharger", "Summon a spectral skeleton horse and ride it for 12 seconds, trampling every enemy in your path.")),

    SPLITTER("splitter", "Creation Splitter", "Worldsplitter", "☍", 0xC2183A, 0x1A0A0A,
            "The blade that cut the first day from the first night. The world has never quite healed where it passes.",
            new AbilityInfo("Fault Line", "Every fifth hit splits the air itself and deals double damage."),
            new AbilityInfo("Fracture", "20% chance for space to crack beneath the target and burst."),
            new AbilityInfo("Sunder", "Cut a 12-block rift along your aim: enemies are thrown out to either side, and anyone on the line takes heavy damage."),
            new AbilityInfo("Banish", "Erase the enemy under your crosshair from the world for 3 seconds, then drop it back out of a rift above where it stood.")),

    // ---- the faction weapons of Arc II (not forged: each member is given their faction's) ----
    HEROSWORD("herosword", "Hero Sword", "Blade of the Order", "⚔", 0x4DA3FF, 0xF2C94C,
            "Carried at the head of every charge the Order of the Blade has ever made. It has never been carried at the back of one.",
            new AbilityInfo("Vanguard", "Resistance I while held."),
            new AbilityInfo("Shieldbreaker", "25% chance to break the target's guard: Weakness and Slowness for 3 seconds."),
            new AbilityInfo("Heroic Charge", "Charge forward; the first enemy you hit is knocked flat and stunned."),
            new AbilityInfo("Rally", "Raise the blade: you and your faction around you gain Strength, Speed and a quick heal.")),

    MJOLNIR("mjolnir", "Mjolnir", "Hammer of the House", "⚒", 0xE0A030, 0x6B4A2B, Material.MACE,
            "The House of the Hammer's oldest heirloom. Storms gather wherever it is raised in anger.",
            new AbilityInfo("Stormborn", "Immune to lightning while held."),
            new AbilityInfo("Thunderclap", "20% chance for lightning to strike the target for +4 damage."),
            new AbilityInfo("Stormcall", "Call lightning down on the enemy under your crosshair; it arcs to other enemies nearby."),
            new AbilityInfo("Bulwark Roar", "You and your faction around you gain Resistance and Absorption, and nearby enemies are thrown back.")),

    GODSPEAR("godspear", "Fallen God's Spear", "Spear of the Covenant", "♆", 0xD83A3A, 0x2A0E0E, Material.NETHERITE_SPEAR,
            "The Covenant of the Spear says a god threw it at the world and missed. They have never let anyone else miss since.",
            new AbilityInfo("Longreach", "+1.5 blocks of reach while held."),
            new AbilityInfo("Skewer", "20% chance to pin the target in place for a second."),
            new AbilityInfo("Godspear Lunge", "Lunge a long way forward, skewering the first enemy and carrying it along."),
            new AbilityInfo("War Banner", "Plant a banner for 15 seconds: your faction near it regenerates and hits harder, enemies near it are weakened.")),

    // ---- other weapons added in 2.3 ----
    CLAWS("claws", "Soul Claws", "Spectral Claws", "Ж", 0x2EE6D6, 0x0B3B4A, Material.NETHERITE_SWORD,
            "Torn from the ghost of a beast that hunted the same forest for a thousand years. It is still hungry.",
            new AbilityInfo("Predator", "Speed I while held. Wounded enemies near you glow."),
            new AbilityInfo("Frenzy", "Every hit builds Frenzy for 3 seconds, up to 5 stacks: Haste and +1 damage per stack."),
            new AbilityInfo("Pounce", "Leap at the enemy under your crosshair, pin it down and rake it three times."),
            new AbilityInfo("The Hunt", "Mark the enemy under your crosshair as prey for 12 seconds: a trail leads you to it, you run faster towards it and hit it harder. Kill it to heal fully.")),

    DAGGERS("daggers", "Thousand Demon Daggers", "Cursed Daggers", "†", 0x9B30FF, 0x1A0526, Material.NETHERITE_SWORD,
            "Twin blades forged in a pact neither smith survived. They always want a little more of you.",
            new AbilityInfo("Twin Fangs", "Every third hit strikes twice."),
            new AbilityInfo("Hex", "Hits stack a hex on the target (up to 5): it takes 6% more damage from you per stack."),
            new AbilityInfo("Blood Pact", "Pay 3 hearts: for 6 seconds your hits deal +5 damage and heal you for half of what they deal."),
            new AbilityInfo("Soul Swap", "Swap places with the enemy under your crosshair. It is left weakened and blinded, and you gain a burst of speed.")),

    MACE("mace", "Divine Punisher", "Holy Mace", "✠", 0xFFD966, 0xFFFFFF, Material.MACE,
            "Blessed on the steps of a cathedral and never once unblessed. The dead fear it; the living should.",
            new AbilityInfo("Dawnward", "Undead take +50% damage from your hits, and you take 20% less damage from undead."),
            new AbilityInfo("Smite", "20% chance for a pillar of light to strike the target for +4 damage."),
            new AbilityInfo("Sanctuary", "Raise a dome of light for 6 seconds: enemies are pushed out, projectiles stop at its edge and allies inside regenerate."),
            new AbilityInfo("Martyr's Vow", "For 8 seconds you can't drop below half a heart. When the vow ends you heal half the damage you took.")),

    GLAIVE("glaive", "Forest Guardian's Glaive", "Guardian's Glaive", "❦", 0x2FBF9F, 0x8A6A3B, Material.NETHERITE_SPEAR,
            "Carried by the old forest's last guardian, who planted it in the earth and became a tree around it.",
            new AbilityInfo("Forest Kin", "Regeneration I while standing on grass, moss or leaves."),
            new AbilityInfo("Thornwisp", "20% chance for a wisp of thorns to latch on, dealing damage every second for 4 seconds."),
            new AbilityInfo("Leaf Glide", "Spring into the air and glide for 4 seconds, steering with your crosshair. Dive into an enemy to strike it."),
            new AbilityInfo("Turning Seasons", "The seasons turn around you for 8 seconds: spring heals you, summer burns enemies, autumn blinds them, winter freezes them.")),

    MAUL("maul", "Power Fuse Hammer", "Fusion Maul", "Ω", 0x2AD4C4, 0xFF9F1C, Material.MACE,
            "A forge-engine with a handle. The fuse in its head has been lit since the day it was built.",
            new AbilityInfo("Polarity", "Your hits magnetize the target for 5 seconds."),
            new AbilityInfo("Static", "25% chance for a spark to arc to every magnetized enemy within 6 blocks."),
            new AbilityInfo("Repulsor Field", "For 5 seconds a magnetic field surrounds you: enemies who come close are thrown back and projectiles are turned away."),
            new AbilityInfo("Overcharge", "Charge the maul for 2 seconds. Your next hit within 8 seconds unleashes it: +14 damage, chaining to every magnetized enemy.")),

    MANTIS("mantis", "Cyber Mantis Blade", "Cyber Arm-Blade", "⌬", 0xFF7A00, 0x1C1C1C, Material.NETHERITE_SWORD,
            "Military hardware with an insect's patience. It folds into your forearm and unfolds into someone else.",
            new AbilityInfo("Servo Legs", "Step straight up whole blocks, and falls of under 12 blocks don't hurt."),
            new AbilityInfo("Serrated", "25% chance to shred the target's armor: it takes 30% more damage from you for 4 seconds."),
            new AbilityInfo("Blade Extension", "Extend the blade for 6 seconds: +3 blocks of reach, and every swing cuts everything in front of you."),
            new AbilityInfo("Ambush", "Crouch in wait for up to 5 seconds, almost invisible. The first enemy to come within 6 blocks is snatched to you and struck four times."));

    public static final String MODEL_NAMESPACE = "customs";

    /** Ids from older versions, so swords players already own keep working. */
    private static final Map<String, SwordType> LEGACY_IDS = Map.of("inferno", PHOENIX, "emerald", VERDANT);

    /** Names from before 2.3 (weapons now carry their model's name), so old commands still work. */
    private static final Map<String, SwordType> OLD_NAMES = Map.ofEntries(Map.entry("wyrmsbane", DRAGON), Map.entry("cinderwing", PHOENIX), Map.entry("everwinter", FROST), Map.entry("stormcaller", STORM), Map.entry("tidebreaker", TIDE), Map.entry("elderbloom", VERDANT), Map.entry("pestilence", PLAGUE), Map.entry("sanguine", CRIMSON), Map.entry("solaris", SOLAR), Map.entry("voidreaver", VOID), Map.entry("starfall", COSMIC), Map.entry("aeon", CHRONO), Map.entry("umbra", SHADOW), Map.entry("gravecaller", SOUL), Map.entry("deepwhisper", ECHO), Map.entry("earthshaker", HAMMER), Map.entry("requiem", SCYTHE), Map.entry("skypiercer", SPEAR), Map.entry("zephyr", CHAKRAM), Map.entry("ragnarok", AXE), Map.entry("arcanum", STAFF), Map.entry("leviathan", TRIDENT), Map.entry("nightbloom", SHURIKEN), Map.entry("jadewarden", HALBERD), Map.entry("eclipse", WARGLAIVE), Map.entry("ripsaw", SAWBLADE), Map.entry("valiance", RAPIER), Map.entry("sandreaver", KHOPESH), Map.entry("hollowmoon", KATANA), Map.entry("swarmsting", NEEDLE), Map.entry("vigil", CANDLE), Map.entry("cinderpeak", CLAYMORE), Map.entry("mirrorwake", MIRROR), Map.entry("saltfang", CUTLASS), Map.entry("ferox", CLAWS), Map.entry("malice", DAGGERS), Map.entry("verdict", MACE), Map.entry("wildheart", GLAIVE), Map.entry("magnetar", MAUL), Map.entry("raptorial", MANTIS));

    private final String id;
    private final Material base;
    private final String swordName;
    private final String title;
    private final String emoji;
    private final TextColor color;
    private final TextColor accent;
    private final String bio;
    private final AbilityInfo passive;
    private final AbilityInfo onHit;
    private final AbilityInfo primary;
    private final AbilityInfo secondary;

    SwordType(String id, String swordName, String title, String emoji, int color, int accent, String bio,
              AbilityInfo passive, AbilityInfo onHit, AbilityInfo primary, AbilityInfo secondary) {
        this(id, swordName, title, emoji, color, accent, null, bio, passive, onHit, primary, secondary);
    }

    SwordType(String id, String swordName, String title, String emoji, int color, int accent, Material base, String bio,
              AbilityInfo passive, AbilityInfo onHit, AbilityInfo primary, AbilityInfo secondary) {
        this.id = id;
        this.base = base;
        this.swordName = swordName;
        this.title = title;
        this.emoji = emoji;
        this.color = TextColor.color(color);
        this.accent = TextColor.color(accent);
        this.bio = bio;
        this.passive = passive;
        this.onHit = onHit;
        this.primary = primary;
        this.secondary = secondary;
    }

    public String id() {
        return id;
    }

    /** The vanilla item it is built on (decides vanilla behaviour such as the mace smash or spear lunge). */
    public Material baseItem() {
        return base == null ? Material.NETHERITE_SWORD : base;
    }

    /** The element of this weapon's magic: decides how casting its abilities looks. */
    public dev.infuse.customs.ability.Fx3D.Element element() {
        return switch (this) {
            case DRAGON, PHOENIX, SOLAR, CANDLE, CLAYMORE -> dev.infuse.customs.ability.Fx3D.Element.FIRE;
            case FROST -> dev.infuse.customs.ability.Fx3D.Element.FROST;
            case STORM, SAWBLADE, MAUL, MANTIS, MJOLNIR -> dev.infuse.customs.ability.Fx3D.Element.STORM;
            case TIDE, TRIDENT, CUTLASS -> dev.infuse.customs.ability.Fx3D.Element.WATER;
            case VERDANT, PLAGUE, GLAIVE, NEEDLE, RIBBON -> dev.infuse.customs.ability.Fx3D.Element.NATURE;
            case VOID, COSMIC, CHRONO, STAFF, WARGLAIVE, KHOPESH, MIRROR, MACE, STARS, SPLITTER -> dev.infuse.customs.ability.Fx3D.Element.ARCANE;
            case SHADOW, SOUL, ECHO, SCYTHE, CLAWS, DEATHKNIGHT -> dev.infuse.customs.ability.Fx3D.Element.SHADOW;
            case CRIMSON, AXE, DAGGERS, ASHURA, GODSPEAR -> dev.infuse.customs.ability.Fx3D.Element.BLOOD;
            default -> dev.infuse.customs.ability.Fx3D.Element.MARTIAL;
        };
    }

    /** How melee hits with this weapon are drawn. */
    public dev.infuse.customs.ability.Fx3D.MeleeStyle meleeStyle() {
        return switch (this) {
            case SCYTHE, AXE, WARGLAIVE, KHOPESH, CLAYMORE, GLAIVE, DEATHKNIGHT, SPLITTER, RIBBON -> dev.infuse.customs.ability.Fx3D.MeleeStyle.HEAVY;
            case SPEAR, RAPIER, HALBERD, TRIDENT, NEEDLE, GODSPEAR -> dev.infuse.customs.ability.Fx3D.MeleeStyle.THRUST;
            case HAMMER, MACE, MAUL, MJOLNIR -> dev.infuse.customs.ability.Fx3D.MeleeStyle.IMPACT;
            case CHAKRAM, SHURIKEN, SAWBLADE -> dev.infuse.customs.ability.Fx3D.MeleeStyle.SPIN;
            default -> dev.infuse.customs.ability.Fx3D.MeleeStyle.SLASH;
        };
    }

    /** The arc this weapon belongs to, or null for the faction weapons (which are never forged). */
    public @Nullable Arc arc() {
        return isFactionWeapon() ? null : Arc.of(this);
    }

    /** The three faction weapons of Arc II: given to faction members, never forged. */
    public boolean isFactionWeapon() {
        return this == HEROSWORD || this == MJOLNIR || this == GODSPEAR;
    }

    /** True for the swords, false for the other weapons. */
    public boolean isSword() {
        return base == null;
    }

    /** The weapon's name, e.g. "Dragon Slaying Blade". */
    public String swordName() {
        return swordName;
    }

    /** What kind of sword it is, e.g. "Dragon Sword". */
    public String title() {
        return title;
    }

    /** Emoji + name, e.g. "🐉 Dragon Slaying Blade". */
    public String displayName() {
        return emoji + " " + swordName;
    }

    public String emoji() {
        return emoji;
    }

    /** Main theme colour (matches the model). */
    public TextColor color() {
        return color;
    }

    /** Secondary colour used for gradients. */
    public TextColor accent() {
        return accent;
    }

    /** One or two sentences of lore. */
    public String bio() {
        return bio;
    }

    public AbilityInfo passive() {
        return passive;
    }

    public AbilityInfo onHit() {
        return onHit;
    }

    public AbilityInfo ability(AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY ? primary : secondary;
    }

    /** Used in death messages: "Steve was {verb} by Alex". */
    public String deathVerb() {
        return switch (this) {
            case DRAGON -> "torn apart";
            case PHOENIX -> "reduced to ashes";
            case FROST -> "frozen solid";
            case STORM -> "struck down";
            case TIDE -> "dragged into the depths";
            case VERDANT -> "reclaimed by nature";
            case PLAGUE -> "consumed by the plague";
            case CRIMSON -> "drained dry";
            case SOLAR -> "burned to a crisp";
            case VOID -> "erased from existence";
            case COSMIC -> "sent into orbit";
            case CHRONO -> "erased from the timeline";
            case SHADOW -> "assassinated";
            case SOUL -> "claimed by the grave";
            case ECHO -> "silenced";
            case HAMMER -> "crushed into the earth";
            case SCYTHE -> "reaped";
            case SPEAR -> "impaled";
            case CHAKRAM -> "carried off by the wind";
            case AXE -> "cleaved in two";
            case TRIDENT -> "skewered by the tide";
            case SHURIKEN -> "cut to ribbons";
            case HALBERD -> "felled at the gate";
            case WARGLAIVE -> "eclipsed";
            case SAWBLADE -> "shredded";
            case RAPIER -> "outdueled";
            case KHOPESH -> "buried in the sands";
            case STAFF -> "unmade by arcane fire";
            case KATANA -> "cut down in a single draw";
            case NEEDLE -> "stung to death";
            case CANDLE -> "snuffed out";
            case CLAYMORE -> "buried in magma";
            case MIRROR -> "shattered";
            case CUTLASS -> "sent to the bottom of the sea";
            case CLAWS -> "hunted down";
            case DAGGERS -> "cursed to death";
            case MACE -> "judged";
            case GLAIVE -> "returned to the forest";
            case MAUL -> "crushed under the field";
            case MANTIS -> "taken by surprise";
            case RIBBON -> "danced to death";
            case STARS -> "written into the stars";
            case ASHURA -> "torn apart by six arms";
            case DEATHKNIGHT -> "raised as a thrall";
            case SPLITTER -> "split from reality";
            case HEROSWORD -> "struck down in single combat";
            case MJOLNIR -> "thunderstruck";
            case GODSPEAR -> "skewered on a god's spear";
        };
    }

    public NamespacedKey itemModel() {
        return new NamespacedKey(MODEL_NAMESPACE, id);
    }

    /** Finds a sword by id ("dragon"), legendary name ("wyrmsbane") or an older id ("inferno"). */
    public static @Nullable SwordType byId(@Nullable String input) {
        if (input == null) {
            return null;
        }
        String needle = input.toLowerCase(Locale.ROOT).replace("_sword", "").replace("sword", "")
                .replaceAll("[^a-z0-9]", "");
        String plain = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (SwordType type : values()) {
            String name = type.swordName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (type.id.equals(needle) || name.equals(plain) || name.replace("sword", "").equals(needle)) {
                return type;
            }
        }
        SwordType legacy = LEGACY_IDS.get(needle);
        return legacy != null ? legacy : OLD_NAMES.get(plain);
    }
}
