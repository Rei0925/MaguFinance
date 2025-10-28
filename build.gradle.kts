plugins {
    kotlin("jvm") version "2.2.0"
    id("com.gradleup.shadow") version "8.3.0"
    id("org.openrewrite.rewrite") version "7.11.0"
    kotlin("plugin.serialization") version "1.9.0"
    kotlin("kapt") version "1.9.0" // 必要なら
    application
}

group = "com.github.rei0925.magufinance"
version = "1.0.4"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))

    // Discord Bot (JDA)
    implementation("net.dv8tion:JDA:6.1.0")

    // Kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // グラフ (XChart)
    implementation("org.knowm.xchart:xchart:3.8.6")

    // ログ（JDAがSLF4J依存してる）
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("org.slf4j:slf4j-api:2.0.9")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.13")

    // kapt用（必要な場合）
    kapt("com.google.dagger:dagger-compiler:2.48") // 例: Dagger使う場合

    //env
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    //タブ補完
    implementation("org.jline:jline:3.26.0")

    //DB
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")

    implementation("org.reflections:reflections:0.10.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    //KotlinCLI
    implementation("com.github.Rei0925:KotlinCLI:1.0.3")
    //yml
    implementation("org.yaml:snakeyaml:2.2")
}

application {
    mainClass.set("com.github.rei0925.magufinance.MainKt") // MainKt を指定
}

rewrite {
    activeRecipe("net.dv8tion.MigrateComponentsV2")
}

tasks {
    shadowJar {
        archiveClassifier.set("") // fat jar を直接 xxx.jar に
        manifest {
            attributes["Main-Class"] = "com.github.rei0925.magufinance.MainKt"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}