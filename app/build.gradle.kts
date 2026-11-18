import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.toque"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.toque"
        minSdk = 23          // Android 6.0, celui du Monsieur Cuisine Connect
        targetSdk = 28
        // Pour publier une mise à jour : augmenter les deux valeurs puis pousser sur GitHub
        // (la version est publiée automatiquement, voir .github/workflows/release.yml).
        versionCode = 8
        versionName = "0.5.1"
    }

    val signingFile = providers.environmentVariable("TOQUE_KEYSTORE")
    val personalSigning = if (signingFile.isPresent) {
        signingConfigs.create("personal") {
            storeFile = file(signingFile.get())
            storeType = "PKCS12"
            storePassword = providers.environmentVariable("TOQUE_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("TOQUE_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("TOQUE_KEY_PASSWORD").get()
        }
    } else null

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = personalSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(project(":core"))
}
