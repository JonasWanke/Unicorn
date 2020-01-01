buildscript {
    ext.kotlinVersion = "1.3.61"

    repositories {
        mavenCentral()
        jcenter()
        google()
    }
    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
    }
}
plugins {
    id "org.jetbrains.kotlin.jvm" version "$kotlinVersion"
<#if isApplication??>
    id "application"
    id "com.github.johnrengelman.shadow" version "5.2.0"
</#if>
    id "com.github.ben-manes.versions" version "0.27.0"
}
<#if isLibrary??>

group = "${packageName}"
version = "${project.version}"
</#if>


repositories {
    mavenCentral()
    jcenter()
    google()
}
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).all {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
}
<#if isApplication??>

application {
    mainClassName = "${packageName}.MainKt"
}
</#if>
