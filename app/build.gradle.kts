plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.rwth_aachen.phyphox"
    testNamespace = "de.rwth_aachen.phyphoxTest"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.rwth_aachen.phyphox"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        versionName = "1.2.0"
        //  format  WXXYYZZ, where WW is major, XX is minor, YY is patch, and ZZ is build
        versionCode = 1020009 //1.02.00-09

        val locales = listOf(
            "en",
            "cs",
            "de",
            "el",
            "es",
            "fr",
            "hi",
            "it",
            "ja",
            "ka",
            "nl",
            "pl",
            "pt",
            "ru",
            "sr",
            "b+sr+Latn",
            "tr",
            "vi",
            "zh-rCN",
            "zh-rTW"
        )
        buildConfigField(
            "String[]",
            "LOCALE_ARRAY",
            "new String[]{\"" + locales.joinToString("\",\"") + "\"}"
        )
        resourceConfigurations.addAll(locales)

        vectorDrawables {
            useSupportLibrary = true
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        multiDexEnabled = true
    }

    buildTypes {
        getByName("release") {
            lint {
                disable.add("MissingTranslation")
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    flavorDimensions.add("permissions")
    productFlavors {
        create("screenshot") {
            dimension = "permissions"
        }
        create("regular") {
            dimension = "permissions"
        }
    }

    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    ndkVersion = "28.0.13004108"
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.androidx.multidex)
    implementation(libs.google.material)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.recyclerview.selection)
    implementation(libs.androidx.recyclerview)
    implementation(libs.bundles.camerax)// CameraX Bundle
    implementation(libs.commons.io)
    implementation(libs.zxing.android.embedded)//https://github.com/journeyapps/zxing-android-embedded/blob/master/CHANGES.md
    implementation(libs.apache.poi)
    implementation(libs.jlhttp)
    implementation(libs.caverock.androidsvg)//https://bigbadaboom.github.io/androidsvg/release_notes.html
    implementation(libs.paho.mqtt.android)

    add("androidTestScreenshotImplementation", libs.junit)
    add("androidTestScreenshotImplementation", libs.fastlane.screengrab)
    add("androidTestScreenshotImplementation", libs.androidx.test.rules)
    add("androidTestScreenshotImplementation", libs.androidx.test.ext.junit)
    add("androidTestScreenshotImplementation", libs.androidx.test.espresso.core)

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
}
