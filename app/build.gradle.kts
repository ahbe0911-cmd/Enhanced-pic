plugins { id("com.android.application") }
android {
    namespace = "com.example.aienhance"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.aienhance"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
