plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(21) }
dependencies { api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1"); testImplementation("junit:junit:4.13.2") }
