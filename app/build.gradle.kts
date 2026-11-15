plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kontre.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kontre.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "1.8.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keyStoreFilePath = project.findProperty("KEYSTORE_PATH") as? String
                ?: System.getenv("KEYSTORE_PATH")
                ?: "reelshield-release.jks"
            val keyStorePassword = project.findProperty("KEYSTORE_PASSWORD") as? String
                ?: System.getenv("KEYSTORE_PASSWORD")
                ?: "reelshield123"
            val keyStoreAlias = project.findProperty("KEY_ALIAS") as? String
                ?: System.getenv("KEY_ALIAS")
                ?: "reelshield"
            val keyStoreKeyPassword = project.findProperty("KEY_PASSWORD") as? String
                ?: System.getenv("KEY_PASSWORD")
                ?: "reelshield123"

            storeFile = file(keyStoreFilePath)
            storePassword = keyStorePassword
            keyAlias = keyStoreAlias
            keyPassword = keyStoreKeyPassword
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (output != null && variant.buildType.name == "release") {
                output.outputFileName = "Kontre-v${variant.versionName}-release.apk"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.ui.tooling)
}

afterEvaluate {
    tasks.named("assembleRelease").configure {
        doLast {
            val releaseDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
            val apkFile = releaseDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("unaligned") }
            if (apkFile != null && apkFile.exists()) {
                val rootDir = rootProject.projectDir
                val parentDir = rootDir.parentFile

                val targetName = "Kontre-release.apk"
                val targetVersionedName = "Kontre-v${android.defaultConfig.versionName}-release.apk"

                apkFile.copyTo(File(rootDir, targetName), overwrite = true)
                apkFile.copyTo(File(rootDir, targetVersionedName), overwrite = true)
                if (parentDir != null && parentDir.exists()) {
                    apkFile.copyTo(File(parentDir, targetName), overwrite = true)
                    apkFile.copyTo(File(parentDir, targetVersionedName), overwrite = true)
                }
                println("=================================================================")
                println("Production Release APK generated and copied successfully:")
                println("  -> ${apkFile.absolutePath}")
                println("  -> ${File(rootDir, targetName).absolutePath}")
                if (parentDir != null && parentDir.exists()) {
                    println("  -> ${File(parentDir, targetName).absolutePath}")
                }
                println("=================================================================")
            }
        }
    }
}
