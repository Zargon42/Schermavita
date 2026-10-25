plugins { id("com.android.library"); kotlin("android") }
android { namespace="dev.riposte.health"; compileSdk=36; defaultConfig { minSdk=28 }; compileOptions { sourceCompatibility=JavaVersion.VERSION_21; targetCompatibility=JavaVersion.VERSION_21 }; kotlinOptions { jvmTarget="21" } }
dependencies { api(project(":life")); api("androidx.health.connect:connect-client:1.1.0"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2") }
