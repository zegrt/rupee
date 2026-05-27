import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
    id("com.google.devtools.ksp") version "2.1.20-1.0.32"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Release signing config is read from local.properties — that file is gitignored, so the
// keystore + passwords never get committed. Generate the keystore with:
//   keytool -genkey -v -keystore rupee-release.jks -keyalg RSA -keysize 2048 \
//           -validity 10000 -alias rupee
// Then add to local.properties at the repo root:
//   rupee.signing.storeFile=/absolute/path/to/rupee-release.jks
//   rupee.signing.storePassword=...
//   rupee.signing.keyAlias=rupee
//   rupee.signing.keyPassword=...
// Missing values mean release will build unsigned (still useful for CI smoke).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStoreFile = localProps.getProperty("rupee.signing.storeFile")
val releaseStorePassword = localProps.getProperty("rupee.signing.storePassword")
val releaseKeyAlias = localProps.getProperty("rupee.signing.keyAlias")
val releaseKeyPassword = localProps.getProperty("rupee.signing.keyPassword")
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() &&
    file(releaseStoreFile).exists() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.zegrt.rupee"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zegrt.rupee"
        minSdk = 29
        targetSdk = 35
        versionCode = 44
        versionName = "0.15.0-alpha.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // AGP 8.8 + Kotlin 2.1 lifted the NonNullableMutableLiveDataDetector crash that
    // forced us to skip release lint on the AGP 8.7.3 / Kotlin 2.0.21 stack. Lint now
    // runs on both buildTypes again; abortOnError stays false so warnings don't
    // fail builds during the dep refresh.
    lint {
        abortOnError = false
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.apply {
                outputFileName = "rupee-${variant.versionName}-${variant.buildType.name}.apk"
            }
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.04.01")

    implementation(bom)
    androidTestImplementation(bom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    ksp("androidx.room:room-compiler:2.7.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    // T2 — dump-replay harness parses production dumps.jsonl files. Android's
    // org.json stubs throw at JVM test time; the real org.json artifact
    // overrides them. Tiny dep, test scope only.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // T1 — instrumented DB tests use Room's in-memory builder. The
    // -testing artifact specifically gives us MigrationTestHelper for
    // testing the v8 → v9 → v10 → v11 migration chain against fixture
    // data. See backlog T1 in docs/rupee-backlog.md.
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
}
