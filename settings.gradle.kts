rootProject.name = "InfuseUltimate"

include(":customs", ":bloodbound", ":infuse-smp", ":infusecore")
project(":customs").projectDir = file("modules/customs")
project(":bloodbound").projectDir = file("modules/bloodbound")
project(":infuse-smp").projectDir = file("modules/infuse-smp")
project(":infusecore").projectDir = file("infusecore")
