plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "io.github.libfdx.graphics"

base {
    archivesName.set("g2d")
}

dependencies {
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:assets:manager"))
    api(project(":libfdx:assets:loaders"))
}
