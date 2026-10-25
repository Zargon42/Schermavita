import java.util.Properties
plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.serialization"); kotlin("kapt"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace="dev.riposte.app"; compileSdk=36
    defaultConfig { applicationId="dev.riposte.personal"; minSdk=28; targetSdk=36; versionCode=8; versionName="0.6.2"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
    buildFeatures { compose=true; buildConfig=true }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_21; targetCompatibility=JavaVersion.VERSION_21 }
    kotlinOptions { jvmTarget="21" }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
    val secretsFile=rootProject.file(".tools/signing/signing.properties")
    if(secretsFile.exists()) {
        val secrets=Properties().apply {secretsFile.inputStream().use {load(it)}}
        signingConfigs.create("personal") {storeFile=rootProject.file(".tools/signing/personal.jks");storePassword=secrets.getProperty("storePassword");keyAlias=secrets.getProperty("keyAlias");keyPassword=secrets.getProperty("storePassword")}
    }
    buildTypes { release { isMinifyEnabled=false; ndk {abiFilters.add("arm64-v8a")}; if(secretsFile.exists()) signingConfig=signingConfigs.getByName("personal") } }
    testOptions { unitTests.isReturnDefaultValues=true }
}
dependencies {
    implementation(project(":core")); implementation(project(":fencing")); implementation(project(":life")); implementation(project(":insights")); implementation(project(":imports")); implementation(project(":health"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1"); implementation("androidx.room:room-ktx:2.7.1"); kapt("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
