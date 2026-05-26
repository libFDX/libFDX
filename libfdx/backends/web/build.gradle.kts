plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

base {
    archivesName.set("backend_web")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:graphics:api"))

    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
}
