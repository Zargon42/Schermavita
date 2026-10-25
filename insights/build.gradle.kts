plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies { api(project(":fencing")); api(project(":life")); testImplementation("junit:junit:4.13.2") }
