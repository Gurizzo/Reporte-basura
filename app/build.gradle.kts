import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma de release opcional: si existe keystore.properties (fuera de git) se usa;
// si no, el release se firma con la llave de debug para poder instalarlo igual.
val firma = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "uy.reportebasura"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "uy.reportebasura"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // normal: la app de siempre. demo: cámara, ubicación y WhatsApp simulados (se instala aparte).
    flavorDimensions += "modo"
    productFlavors {
        create("normal") {
            dimension = "modo"
            isDefault = true
        }
        create("demo") {
            dimension = "modo"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
    }

    signingConfigs {
        if (!firma.isEmpty) {
            create("propia") {
                storeFile = rootProject.file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("propia") ?: signingConfigs.getByName("debug")
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
        buildConfig = false
    }

    packaging {
        resources {
            // Metadatos de Kotlin que no hacen falta en tiempo de ejecución (no usamos reflexión).
            excludes += listOf("META-INF/**", "kotlin/**", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
        }
    }

    // No incluir el bloque de dependencias firmado por Google (peso extra sin uso fuera de Play).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Sin dependencias: solo el SDK de Android y la librería estándar de Kotlin (que R8 recorta).
dependencies {
}
