plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    id("rust-setup")
}

group = "xyz.bluspring.unitytranslate"
version = "${rootProject.property("library_version")}"

base {
    archivesName.set("application-audio-capture")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    api(libs.bundles.kotlin)
    api(libs.slf4j.api)
    testRuntimeOnly(libs.slf4j.simple)
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

val rust = natives("ApplicationAudioCapture") {
    path = projectDir.toPath().resolve("jni")

    platform("windows", "x64")

    // TODO: fix for every other platform
//    platform("windows", "arm64")
//    platform("windows", "arm64ec")

//    platform("linux", "x86_64")
//    platform("linux", "armv7")
//    platform("linux", "armv7s")
//    platform("linux", "arm64")

//    platform("mac", "x86_64")
//    platform("mac", "arm64")
}

publishing {
    repositories {
        maven("https://mvn.devos.one/releases") {
            credentials {
                username = System.getenv()["MAVEN_USER"]
                password = System.getenv()["MAVEN_PASS"]
            }
        }
    }

    publications {
        register("maven", MavenPublication::class) {
            groupId = "xyz.bluspring.unitytranslate"
            artifactId = "application-audio-capture"
            version = "${rootProject.version}"
            from(components.getByName("java"))

            for (platform in rust.platformTaskNames) {
                artifact(tasks.getByName("nativesJar$platform"))
            }
        }
    }
}
