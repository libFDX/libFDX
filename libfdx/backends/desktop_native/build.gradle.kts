plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

base {
    archivesName.set("backend_desktop_native")
}

dependencies {
    api(project(":libfdx:foundation:core"))
    api(project(":libfdx:runtime:application"))
    api(project(":libfdx:runtime:display"))
    api(project(":libfdx:graphics:api"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(libs.teavm.interop)
}
