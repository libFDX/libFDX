plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

group = "io.github.libfdx.gl"

base {
    archivesName.set("gl_web")
}

dependencies {
    api(project(":libfdx:extensions:graphics:gl:core"))

    implementation(libs.teavm.jso)
    implementation(libs.teavm.jso.apis)
    implementation(libs.teavm.jso.impl)
}
