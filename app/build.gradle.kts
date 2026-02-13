import java.util.Properties

plugins {
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    compileSdk = libs.versions.compile.sdk.get().toInt()
    namespace = "net.syncthing.lite"

    defaultConfig {
        applicationId = "com.github.catfriend1.syncthinglite"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = libs.versions.version.code.get().toInt()
        versionName = libs.versions.version.name.get()
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            val localProps = Properties()
            val localFile = rootProject.file("local.properties")
            if (localFile.exists()) {
                localFile.inputStream().use { localProps.load(it) }
            }
            
            fun propOrEnv(key: String): String? =
                localProps.getProperty(key) ?: System.getenv(key)
            
            storeFile = propOrEnv("SYNCTHING_RELEASE_STORE_FILE")?.let(::file)
            storePassword = propOrEnv("SIGNING_PASSWORD")
            keyAlias = propOrEnv("SYNCTHING_RELEASE_KEY_ALIAS")
            keyPassword = storePassword
        }
    }

    lint {
        abortOnError = true
        targetSdk = libs.versions.target.sdk.get().toInt()
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            signingConfig = null
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.runCatching { getByName("release") }
                .getOrNull()
                .takeIf { it?.storeFile != null }
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            excludes.add("META-INF/*")
        }
        resources {
            excludes.add("META-INF/*")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

}

dependencies {
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.aboutlibraries.core)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.collection.ktx)
    implementation(libs.core.ktx)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.legacy.preference.v14)
    implementation(libs.legacy.support.v4)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.preference.ktx)
    implementation(libs.recyclerview)
    implementation(libs.zxing.android.embedded)

    implementation(project(":syncthing-client"))
    implementation(project(":syncthing-repository-android"))
}

abstract class ValidateAppVersionCode : DefaultTask() {

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @TaskAction
    fun validate() {
        val name = versionName.get()
        val code = versionCode.get()

        val parts = name.split(".")
        if (parts.size != 4) {
            throw GradleException("Invalid versionName format: '$name'. Expected format 'major.minor.patch.wrapper'.")
        }

        val calculatedCode = parts[0].toInt() * 1_000_000 +
                             parts[1].toInt() * 10_000 +
                             parts[2].toInt() * 100 +
                             parts[3].toInt()

        if (calculatedCode != code) {
            throw GradleException("Version mismatch: Calculated versionCode ($calculatedCode) does not match declared versionCode ($code). Please review 'gradle/libs.versions.toml'.")
        }
    }
}

tasks.register<ValidateAppVersionCode>("validateAppVersionCode") {
    versionName.set(libs.versions.version.name)
    versionCode.set(libs.versions.version.code.map { it.toInt() })
}

project.afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
        dependsOn("validateAppVersionCode")
    }
}
