// Infuse Ultimate — umbrella build for the whole SMP suite.
// Each module keeps its own build.gradle.kts; this root just gives them a common home
// and a single `./gradlew build` that produces every jar at once.
tasks.register("suite") {
    group = "infuse ultimate"
    description = "Build every module jar (Customs, Bloodbound, Infuse, InfuseCore)."
    dependsOn(
        ":customs:build",
        ":bloodbound:build",
        ":infuse-smp:build",
        ":infusecore:build",
    )
}
