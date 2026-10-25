plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(21) }
dependencies { api(project(":core")); testImplementation("junit:junit:4.13.2") }
