import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.3.20"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "com.jonaswanke.unicorn.MainKt"
}

dependencies {
    compile(kotlin("stdlib-jdk8"))

    implementation(kotlin("compiler-embeddable"))
    implementation(kotlin("script-util"))
    implementation(kotlin("script-runtime"))

    implementation("org.beryx:text-io:3.3.0")
    implementation("com.squareup.okhttp3:okhttp:3.12.1")

    implementation("net.swiftzer.semver:semver:1.1.0")
    implementation("com.github.ajalt:clikt:1.6.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.2.1.201812262042-r")
    implementation("org.kohsuke:github-api:1.95")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.7.1-1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.7.1-2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.7.1")

    implementation("org.slf4j:slf4j-nop:1.7.25")
}

repositories {
    jcenter()
}

sourceSets {
    getByName("main") {
        resources.srcDirs("res")
    }
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = application.mainClassName
    }
    from(configurations.runtime.map { if (it.isDirectory) it else zipTree(it) })
}

/*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

        plugins {
            application
            kotlin("jvm") version "1.3.11"
        }

application {
    mainClassName = ""
}

repositories {
    mavenCentral()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
}

sourceSets {

}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}*/
