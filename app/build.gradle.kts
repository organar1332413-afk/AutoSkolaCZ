plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android {
    namespace = "cz.autoskola.app"
    compileSdk = 35
    defaultConfig { applicationId = "cz.autoskola.study"; targetSdk = 35; versionCode = 2; versionName = "0.2.0-stage2"; minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    testOptions { unitTests.isIncludeAndroidResources=true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs.getByName("debug") { storeFile=file("signing/development.keystore");storePassword="android";keyAlias="androiddebugkey";keyPassword="android" }
    buildTypes {
        debug { isDebuggable = true; applicationIdSuffix = ".debug" }
        release { isDebuggable = false; isMinifyEnabled = false }
    }
    bundle { language { enableSplit = false } }
}
kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    implementation(project(":core:domain"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.compose)
    implementation(libs.compose.icons)
    implementation(libs.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
}
