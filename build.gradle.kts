plugins {
    java
    id("org.jetbrains.intellij.platform")
}

group = "dev.niteesh.cliagentbridge"
version = "0.1.1"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    intellijPlatform {
        val localIde = providers.environmentVariable("JETBRAINS_IDE_PATH").orNull
        if (localIde != null) local(localIde) else intellijIdea("2026.2.3")
    }
}
