plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.agrestelink.clube"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.agrestelink.clube"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("CLUBE_VERSION_NAME") ?: "1.0"
        // endereço do worker do Clube na Cloudflare (nome do worker: agreste-clube)
        buildConfigField("String", "APP_URL", "\"https://agreste-clube.ademilton41.workers.dev/\"")
    }

    signingConfigs {
        create("teste") {
            storeFile = file("teste.keystore")
            storePassword = "agrestelink"
            keyAlias = "agreste"
            keyPassword = "agrestelink"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("teste")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
