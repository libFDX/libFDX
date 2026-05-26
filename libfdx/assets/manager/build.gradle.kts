plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "io.github.libfdx.assets"

base {
    archivesName.set("asset_manager")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:runtime:files"))
}
