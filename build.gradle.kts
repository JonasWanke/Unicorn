plugins {
    application
    kotlin("jvm") version "1.3.11"
}

application {
    mainClassName = "com.jonaswanke.aluminum.MainKt"
}

dependencies {
    compile(kotlin("stdlib-jdk8"))

    implementation("org.beryx:text-io:3.3.0")
    implementation("com.github.ajalt:clikt:1.6.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.2.1.201812262042-r")
    implementation("org.kohsuke:github-api:1.95")
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
