import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "sr.leo.karoo_squadrats"
    compileSdk = 36

    defaultConfig {
        applicationId = "sr.leo.karoo_squadrats"
        minSdk = 23
        targetSdk = 36
        versionCode = providers.environmentVariable("VERSION_CODE").map { it.toInt() }.getOrElse(1)
        versionName = providers.environmentVariable("VERSION_NAME").getOrElse("dev")

        // Empty by default (see debug/local build types below) so a release build never picks
        // up a token from a developer's local.properties.
        buildConfigField("String", "DEFAULT_USER_TOKEN", "\"\"")

        // Karoo identifies extensions by this id (see res/xml/extension_info.xml), not by
        // Android package name. Non-release variants need their own id (must match the
        // per-variant extension_info.xml override) or Karoo routes map callbacks to
        // whichever app registered "karoo-squadrats" first, ignoring the others entirely.
        buildConfigField("String", "EXTENSION_ID", "\"karoo-squadrats\"")
    }

    signingConfigs {
        val keystorePath = providers.environmentVariable("KEYSTORE_PATH")
        if (keystorePath.isPresent) {
            create("release") {
                storeFile = file(keystorePath.get())
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "EXTENSION_ID", "\"karoo-squadrats-debug\"")
            // Dev convenience: pre-fill the Squadrats token on a fresh install so it doesn't
            // need to be re-typed after every reinstall. Set `squadrats.token` in the
            // gitignored local.properties; leave unset to keep this empty as usual.
            buildConfigField("String", "DEFAULT_USER_TOKEN", "\"${localProps.getProperty("squadrats.token", "")}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
        // Non-debug build, signed with the local debug keystore, installable
        // side-by-side with the official release build for manual testing.
        create("local") {
            initWith(getByName("release"))
            applicationIdSuffix = ".local"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "EXTENSION_ID", "\"karoo-squadrats-local\"")
            buildConfigField("String", "DEFAULT_USER_TOKEN", "\"${localProps.getProperty("squadrats.token", "")}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
}

tasks.register("generateManifest") {
    description = "Generates manifest.json for Karoo Companion App sideloading"
    group = "build"
    doLast {
        val manifestFile = file("$projectDir/manifest.json")
        val versionName = android.defaultConfig.versionName
        val versionCode = android.defaultConfig.versionCode
        val manifest = """
            {
              "label": "Squadrats Overlay",
              "packageName": "sr.leo.karoo_squadrats",
              "latestApkUrl": "https://github.com/leoschweizer/karoo-squadrats/releases/latest/download/karoo-squadrats-release.apk",
              "latestVersion": "$versionName",
              "latestVersionCode": $versionCode,
              "developer": "github.com/leoschweizer",
              "description": "Displays uncollected Squadrats as colored grid outlines on the map during rides.",
              "tags": ["map"],
              "iconUrl": "https://github.com/leoschweizer/karoo-squadrats/releases/download/v$versionName/karoo-squadrats.png"
            }

        """.trimIndent()
        manifestFile.writeText(manifest)
        println("Generated manifest.json with version $versionName ($versionCode)")
    }
}

afterEvaluate {
    tasks.named("assembleRelease") {
        dependsOn("generateManifest")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("io.hammerhead:karoo-ext:1.1.8")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.11.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
