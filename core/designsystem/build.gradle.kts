plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android {
    namespace = "cz.autoskola.design"
    compileSdk = 35
    defaultConfig {  minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:domain"))
implementation(platform(libs.compose.bom))
implementation(libs.compose.ui)
implementation(libs.compose.foundation)
implementation(libs.compose.material3)
}
