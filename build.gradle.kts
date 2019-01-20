plugins {
    application
    kotlin("jvm") version "1.3.11"
}

application {
    mainClassName = "com.jonaswanke.aluminum.MainKt"
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
}

repositories {
    jcenter()
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
