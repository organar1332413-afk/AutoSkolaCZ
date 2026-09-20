plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.ksp); alias(libs.plugins.kotlin.serialization) }
android {
    namespace = "cz.autoskola.data"
    compileSdk = 35
    defaultConfig {  minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    testOptions { unitTests.isIncludeAndroidResources=true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":core:domain"))
implementation(libs.room.runtime)
implementation(libs.room.ktx)
ksp(libs.room.compiler)
implementation(libs.datastore)
implementation(libs.coroutines.android)
implementation(libs.serialization)
testImplementation(libs.junit)
testImplementation(libs.robolectric)
androidTestImplementation(libs.androidx.test)
androidTestImplementation(libs.androidx.runner)
}
