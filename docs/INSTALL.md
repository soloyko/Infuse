===========================================================================
  INFUSE ULTIMATE 1.0.0  —  install guide
  Paper / Spigot 1.21.11
===========================================================================

This bundle combines four plugins under one roof, tied together by InfuseCore:

  * Customs            53 legendary 3D weapons, forged at altars   (/customs)
  * Bloodbound         blood-powered PvP weapon kits               (/bloodbound, /bb)
  * Infuse SMP         infusable effects & sparks + /trust         (/infuse)
  * Limitless Arsenal  24 energy-cast relic weapons                (/coolability)
  * InfuseCore         the hub that unifies them all               (/iu)

---------------------------------------------------------------------------
1. INSTALL THE PLUGINS
---------------------------------------------------------------------------
Copy EVERY jar from this bundle's `plugins/` folder into your server's
`plugins/` folder:

    plugins/Customs-2.9.0.jar
    plugins/Bloodbound-1.0.0.jar
    plugins/Infuse-1.0.0.jar
    plugins/Limitless-Paper-1.21.11.jar
    plugins/InfuseCore-1.0.0.jar      <-- the hub; load any subset you like

You can run any subset — InfuseCore detects which modules are present and
only shows those. Start (or restart) the server once. In console you'll see
the Infuse Ultimate banner listing which modules came online.

---------------------------------------------------------------------------
2. THE RESOURCE PACK  (3D weapon models + effect HUD)
---------------------------------------------------------------------------
`Infuse-Ultimate-Pack-1.0.0.zip` is the ONE merged pack for the whole suite
(Customs' 3D weapon models + Infuse's HUD). You only need this one pack.

Players can install it themselves, OR the server can push it automatically:

  a) Upload Infuse-Ultimate-Pack-1.0.0.zip somewhere with a DIRECT download
     link (a GitHub release asset works well).
  b) Edit plugins/InfuseCore/config.yml:

        resource-pack:
          enabled: true
          url: "https://.../Infuse-Ultimate-Pack-1.0.0.zip"
          sha1: "<the SHA-1 printed by the build, or run sha1sum on the zip>"
          required: false
          on-join: true

  c) /iu reload   (or restart). Players now get the pack on join, and can
     re-request it any time with /iu pack.

Limitless relics are particle-and-command based and need no pack.

---------------------------------------------------------------------------
3. USING IT
---------------------------------------------------------------------------
  /iu            open the hub menu — one icon per installed module
  /iu status     text list of modules, versions and their commands
  /iu pack       (re)apply the resource pack
  /iu help       command help

Each module keeps its own commands, config and data folder exactly as
before — InfuseCore adds a unifying layer on top and changes none of them.
Per-module settings live in:

    plugins/Customs/config.yml
    plugins/Bloodbound/config.yml
    plugins/Infuse/config.yml
    plugins/InfuseCore/config.yml

Enjoy. — Infuse Ultimate
