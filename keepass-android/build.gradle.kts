plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.2.2"
}

dependencies {
    implementation(libs.keepass.jackson) {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.slf4j")
        exclude(group = "junit")
        exclude(group = "org.hamcrest")
    }
    implementation("org.apache.geronimo.specs:geronimo-stax-api_1.2_spec:1.2")
}

// Android 8 exposes an obsolete Commons Codec on the boot classpath. Keep the
// library's codec private so KeePass uses its tested version on every supported API.
// No crypto algorithms or KeePass implementation are modified.
tasks.shadowJar {
    relocate("org.apache.commons.codec", "com.yishenghuang.keyic.internal.codec")
    mergeServiceFiles()
    exclude("META-INF/versions/**", "module-info.class")
}
