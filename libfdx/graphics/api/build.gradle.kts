plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

base {
    archivesName.set("graphics_api")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:runtime:display"))
}
