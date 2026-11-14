# Research: YouTube analysis tools, Paper plugin dev, and what makes an SMP plugin fun

Compiled 2026-11-14 from GitHub repository searches. Star counts are from that date.

---

## 1. Tools for understanding YouTube videos

Use these to pull transcripts from popular Minecraft SMP videos and study how they explain and hype their mechanics.

| Repo | ★ | What it's for |
|---|---|---|
| [yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp) | 207k | The standard downloader. `yt-dlp --skip-download --write-auto-subs --sub-langs en --write-info-json URL` gets captions, chapters, description, and view/like counts without downloading the video. |
| [jdepoix/youtube-transcript-api](https://github.com/jdepoix/youtube-transcript-api) | 8.6k | Python library that returns a video's transcript as timestamped text, with no API key. The quickest route to text you can feed an LLM. |
| [SYSTRAN/faster-whisper](https://github.com/SYSTRAN/faster-whisper) | 26.7k | Local speech-to-text for videos with missing or poor auto-captions. Runs about 4× faster than OpenAI Whisper. |
| [jimmc414/onefilellm](https://github.com/jimmc414/onefilellm) | 2.0k | Combines a YouTube transcript, a GitHub repo, or docs into one prompt-ready text file. Handy for "here's the video + here's my code". |
| [zarazhangrui/youtube-digest](https://github.com/zarazhangrui/youtube-digest) | 1.8k | Transcript → AI overview and explanations. A useful reference for a summarizing pipeline. |
| [nexmoe/VidBee](https://github.com/nexmoe/VidBee) | 11k | Desktop app: download, transcribe locally with speaker labels, then summarize. Good if you don't want to code. |

**Suggested workflow:** use `yt-dlp` to grab subs and metadata for 10–20 popular SMP videos. If captions are bad, run `faster-whisper`. Then have an LLM tag each video's mechanics, the hook in its first 30 seconds, and the moments where the creator reacts most strongly. Those moments show which mechanics read well on camera.

---

## 2. Minecraft (Paper) plugin development: the essentials

### Server and API
- [PaperMC/Paper](https://github.com/PaperMC/Paper) (12.8k★): target Paper, not Spigot. Its API is better (Adventure text components, async chunk loading, Brigadier commands, data components).
- [PaperMC/Folia](https://github.com/PaperMC/Folia) (4.7k★): regionized multithreading. Supporting it from the start is a selling point, but it changes how you schedule tasks.

### Project templates (Gradle + run-paper)
- [spruceworks/plugin-template](https://github.com/spruceworks/plugin-template): Gradle Kotlin DSL, Java 25, Paper 26.2, run-paper, shadowJar, bStats. Very current.
- [milkdrinkers/Minecraft-Plugin-Template](https://github.com/milkdrinkers/Minecraft-Plugin-Template), [flytegg/paper-plugin-template](https://github.com/flytegg/paper-plugin-template) (Kotlin), [stephendotgg/mc-kotlin-template](https://github.com/stephendotgg/mc-kotlin-template) (paperweight + run-paper).
- `./gradlew runServer` from run-paper starts a test server with your plugin in one command. It's the biggest speedup to the dev loop.

### Libraries worth using
| Library | ★ | Why |
|---|---|---|
| [CommandAPI/CommandAPI](https://github.com/CommandAPI/CommandAPI) | 649 | Brigadier commands with client-side tab-completion and argument validation. (Paper's own `LifecycleEvents.COMMANDS` Brigadier API also works.) |
| [TriumphTeam/triumph-gui](https://github.com/TriumphTeam/triumph-gui) | 463 | Clean inventory GUIs (paginated, scrolling). |
| [toxicity188/BetterModel](https://github.com/toxicity188/BetterModel) | 361 | Free, modern Blockbench model engine: animated custom mobs and bosses with no client mods. |
| [Xiao-MoMi/craft-engine](https://github.com/Xiao-MoMi/craft-engine) | 312 | Custom items, blocks, and recipes through resource packs. Supports Folia. |
| [GrimAnticheat/Grim](https://github.com/GrimAnticheat/Grim) | 1.8k | Not a dependency, but test that your movement abilities (dashes, launches) don't get flagged. |

### Large open-source plugins to learn architecture from
- [mcMMO-Dev/mcMMO](https://github.com/mcMMO-Dev/mcMMO) (1.1k★): skills, XP, and progression. A mature reference for data storage and config.
- [Slimefun/Slimefun4](https://github.com/Slimefun/Slimefun4) (1.1k★): "feels like a modpack". Shows how to build a large custom item/block registry and an addon API.
- [ProjectKorra/ProjectKorra](https://github.com/ProjectKorra/ProjectKorra) (111★): the best reference for an **ability system**, with cooldowns, combos, an ability lifecycle (`progress()` each tick), and an addon API. See also [xNuminousx/Ability-Tutorial](https://github.com/xNuminousx/Ability-Tutorial).
- [Dueris/OriginsPaper](https://github.com/Dueris/OriginsPaper): data-driven powers and origins on Paper.
- [kangarko/Boss](https://github.com/kangarko/Boss): custom entities with pathfinders and skills.

### SMP-specific references
- [ZetaPlugins/LifeStealZ](https://github.com/ZetaPlugins/LifeStealZ) (41★): the most-starred LifeSteal SMP plugin. A good model of a small, focused, configurable core loop.
- [TeamHeather/Cataclysm](https://github.com/TeamHeather/Cataclysm): plugin for a real YouTube SMP.
- [Nu11ified/GlitchSMP](https://github.com/Nu11ified/GlitchSMP): "glitches" as gameplay mechanics.
- [shantek/UltimateBingo](https://github.com/shantek/UltimateBingo): event mini-game for SMPs.
- Some SMP repos found in search contain **decompiled jars** of other people's plugins. Use them for ideas only and don't copy their code (licensing).

### Curated lists
- [Bytekron/Awesome-Minecraft](https://github.com/Bytekron/Awesome-Minecraft): server software, proxies, plugins, tools.

---

## 3. What makes an SMP plugin *fun*

Patterns common to the SMP plugins and YouTube series that took off (LifeSteal, gems/trims/token SMPs, Glitch SMP):

1. **One core idea you can explain in a sentence.** "Kill players to steal their hearts." "Drink infusions to get powers." If the pitch needs a paragraph, it won't spread.
2. **Loss is real.** Players drop hearts, gems, or powers when they die. Taking something from another player creates rivalries, revenge arcs, and stories, which is what SMP content runs on.
3. **Scarcity and uniqueness.** Items that only one player can own (a "legendary" infusion, a one-of-one weapon) make that player a target and give everyone else a goal.
4. **Visible power.** Particles, sounds, action-bar cooldowns, and custom models. Viewers and other players should be able to *see* who has what ability. If it can't be seen on camera, it doesn't exist.
5. **Short active abilities plus passive identity.** One or two keybind abilities (swap-hand, sneak + right-click) on cooldowns of roughly 30 seconds to 2 minutes, plus a passive that changes how you play (fire immunity, night vision, extra hearts).
6. **Counterplay.** Every strong ability needs an answer: a cooldown window, a counter-item, or an environment that weakens it. Without counters, the strongest player snowballs and the server dies.
7. **Progression plus a reset valve.** Upgrades (tier 1 → 2) give long-term goals. A recovery mechanic (a craftable revive beacon or heart) stops eliminated players from quitting.
8. **Server-wide events.** Admin-triggered moments such as a boss, a border shrink, a "purge" hour, or a duel arena with a broadcast. These create the episodes.
9. **Low friction.** It works on vanilla clients (resource pack optional or auto-sent), has no lag spikes, and can be configured by the admin running the SMP.

## 4. What makes an SMP plugin *unique*

Ways to stand out from the many LifeSteal-style clones:

- **Interactions between powers.** Combos when two players' powers combine (fire + wind = firestorm) push people to form alliances.
- **Powers with a drawback.** For example, a speed infusion that also drains hunger. Trade-offs make choices interesting.
- **A world that responds.** The more a power is used, the more it changes the world (a corrupted biome, a global debuff on the holder), creating server-wide stakes.
- **Social mechanics.** Bounties, trust/betrayal contracts, shared team powers, or a "the strongest player glows" broadcast.
- **Rotating seasons.** New infusions or rules each season, so the server keeps feeling fresh and gives creators a reason to make another series.
- **Admin tooling built for creators.** Commands to spawn an event, give or take powers, and run a cinematic countdown. SMP hosts are your real customers.

---

## 5. Suggested next steps

1. Pick 10 reference SMP videos and run the transcript workflow in §1.
2. Write a one-sentence pitch for your plugin and list its core mechanics. For each power or item, note its active effect, passive effect, drawback, counter, and visual.
3. Start from a Paper + run-paper template, then add CommandAPI and triumph-gui.
4. Build the core loop first (how players gain, lose, and take things from each other) before adding extra content.
