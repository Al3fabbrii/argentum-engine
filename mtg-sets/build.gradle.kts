plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":mtg-sdk"))
    implementation(libs.classgraph)
    implementation(libs.kotlinxSerialization)

    testImplementation(libs.kotestRunner)
    testImplementation(libs.kotestAssertions)
}

tasks.withType<Test> {
    systemProperty("verifyImageUris", System.getProperty("verifyImageUris") ?: "false")
}

// One-shot Scryfall sync — populates legalities.json from the live Scryfall API.
// Run with: ./gradlew :mtg-sets:syncLegality
tasks.register<JavaExec>("syncLegality") {
    description = "Fetch deck-format legality for every registered card from Scryfall."
    group = "build"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.wingedsheep.mtg.sets.legality.SyncLegalitiesKt")
    workingDir = rootProject.projectDir
}
