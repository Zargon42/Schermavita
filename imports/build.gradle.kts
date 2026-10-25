plugins { id("com.android.library"); kotlin("android"); kotlin("plugin.serialization") }
android { namespace="dev.riposte.imports"; compileSdk=36; defaultConfig { minSdk=28 }; compileOptions { sourceCompatibility=JavaVersion.VERSION_21; targetCompatibility=JavaVersion.VERSION_21 }; kotlinOptions { jvmTarget="21" } }
dependencies { api(project(":fencing")); api(project(":life")); implementation("com.google.mlkit:text-recognition:16.0.1"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2"); testImplementation("junit:junit:4.13.2") }
