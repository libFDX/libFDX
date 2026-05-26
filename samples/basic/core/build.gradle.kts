plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "io.github.libfdx.samples.basic"

base {
    archivesName.set("sample_basic_core")
}

dependencies {
    api(project(":libfdx:runtime:application"))
    implementation(project(":libfdx:graphics:api"))
    implementation(project(":libfdx:graphics:g2d"))
}
