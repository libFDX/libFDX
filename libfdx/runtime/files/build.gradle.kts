plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "io.github.libfdx.files"

base {
    archivesName.set("files")
}

dependencies {
    api(project(":libfdx:foundation:core"))
}
