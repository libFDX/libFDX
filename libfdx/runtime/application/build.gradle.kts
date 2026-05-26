plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

base {
    archivesName.set("application")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:runtime:files"))
    api(project(":libfdx:graphics:api"))
}
