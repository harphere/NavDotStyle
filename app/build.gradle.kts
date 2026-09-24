plugins { id("com.android.application") }

android {
    namespace = "com.chet.navdotstyle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chet.navdotstyle"
        minSdk = 35
        targetSdk = 36
        versionCode = 111
        versionName = "1.1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging.resources.merges += "META-INF/xposed/*"
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
    implementation("io.github.libxposed:service:101.0.0")
}
