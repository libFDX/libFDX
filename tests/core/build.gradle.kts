plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "io.github.libfdx.tests"

base {
    archivesName.set("tests_core")
}

dependencies {
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:graphics:g2d"))
    api(project(":libfdx:graphics:g3d"))
}
