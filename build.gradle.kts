plugins {
    kotlin("jvm") version "2.2.0"
    id("com.gradleup.shadow") version "8.3.0"
    kotlin("plugin.serialization") version "1.9.0"
    kotlin("kapt") version "1.9.0" // 必要なら
    application
}

group = "com.github.rei0925"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // Discord Bot (JDA)
    implementation("net.dv8tion:JDA:5.0.0-beta.20")

    // Kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // グラフ (XChart)
    implementation("org.knowm.xchart:xchart:3.8.6")

    // ログ（JDAがSLF4J依存してる）
    implementation("org.slf4j:slf4j-simple:2.0.7")

    // kapt用（必要な場合）
    kapt("com.google.dagger:dagger-compiler:2.48") // 例: Dagger使う場合

    //env
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    //タブ補完
    implementation("org.jline:jline:3.26.0")
}

application {
    mainClass.set("com.github.rei0925.MainKt") // MainKt を指定
}

tasks {
    shadowJar {
        archiveClassifier.set("") // fat jar を直接 xxx.jar に
        manifest {
            attributes["Main-Class"] = "com.github.rei0925.MainKt"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}