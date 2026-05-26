plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "io.github.libfdx.vulkan"

base {
    archivesName.set("vulkan_desktop")
}

dependencies {
    api(project(":libfdx:extensions:graphics:vulkan:core"))
    runtimeOnly(libs.lwjgl.vulkan)
}
