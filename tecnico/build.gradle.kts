plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.agrestelink.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.agrestelink.tecnico"
        minSdk = 24
        targetSdk = 34
        // o GitHub preenche estes números a cada build
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0"
        buildConfigField("String", "APP_URL", "\"https://agreste-tecnico.ademilton41.workers.dev/\"")
    }

    signingConfigs {
        create("teste") {
            // chave de TESTE (fica no projeto). Para a Play Store vamos criar uma chave própria e secreta.
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
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
