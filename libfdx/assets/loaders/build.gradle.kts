plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "io.github.libfdx.assets"

base {
    archivesName.set("asset_loaders")
}

dependencies {
    api(project(":libfdx:assets:manager"))
}
