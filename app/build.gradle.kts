plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "de.rwth_aachen.phyphox"
    testNamespace = "de.rwth_aachen.phyphoxTest"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.rwth_aachen.phyphox"
        minSdk = 21
        targetSdk = 35

        versionName = "1.2.0"
        //  format  WXXYYZZ, where WW is major, XX is minor, YY is patch, and ZZ is build
        versionCode = 1020009 //1.02.00-09

        val locales = listOf("en", "cs", "de", "el", "es", "fr", "hi", "it", "ja", "ka", "nl", "pl", "pt", "ru", "sr", "b+sr+Latn", "tr", "vi", "zh-rCN", "zh-rTW")
        buildConfigField("String[]", "LOCALE_ARRAY", "new String[]{\"" + locales.joinToString("\",\"") + "\"}")
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

    implementation("androidx.multidex:multidex:2.0.1")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.appcompat:appcompat-resources:1.7.1")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.fragment:fragment:1.8.8")
    implementation("androidx.fragment:fragment-ktx:1.8.8")

    implementation("androidx.viewpager:viewpager:1.1.0")
    implementation("org.apache.commons:commons-io:1.3.2")

    //https://github.com/journeyapps/zxing-android-embedded/blob/master/CHANGES.md
    implementation("com.journeyapps:zxing-android-embedded:3.5.0")

    implementation("org.apache.poi:poi:3.13")

    implementation("net.freeutils:jlhttp:3.1")

    //https://bigbadaboom.github.io/androidsvg/release_notes.html
    implementation("com.caverock:androidsvg:1.4")

    implementation("androidx.recyclerview:recyclerview-selection:1.2.0")
    testImplementation("junit:junit:4.13.2")

    //Automated screenshot generation
//    val androidTestScreenshotImplementation by configurations.maybeCreate
    add("androidTestScreenshotImplementation","junit:junit:4.13.2")
    add("androidTestScreenshotImplementation","tools.fastlane:screengrab:2.1.1")
    add("androidTestScreenshotImplementation","androidx.test:rules:1.6.1")
    add("androidTestScreenshotImplementation","androidx.test.ext:junit:1.2.1")
    add("androidTestScreenshotImplementation","androidx.test.espresso:espresso-core:3.6.1")

    testImplementation("com.google.truth:truth:1.0.1")

    implementation("com.github.hannesa2:paho.mqtt.android:4.4")

    val camerax_version = "1.4.2"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
