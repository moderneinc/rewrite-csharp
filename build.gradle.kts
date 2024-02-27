plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}
group = "org.openrewrite"
description = "Rewrite CSharp"

val latest = if (project.hasProperty("releasing")) {
    "latest.release"
} else {
    "latest.integration"
}

dependencies {
    annotationProcessor("org.projectlombok:lombok:latest.release")

    compileOnly("org.openrewrite:rewrite-core")
    compileOnly("org.openrewrite:rewrite-test")
    compileOnly("org.projectlombok:lombok:latest.release")
    compileOnly("com.google.code.findbugs:jsr305:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${latest}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("com.dylibso.chicory:runtime:latest.integration")
    implementation("com.dylibso.chicory:wasi:latest.integration")
    implementation("org.extism.sdk:extism:latest.integration")
//    implementation("org.extism:chicory-sdk:latest.integration")
    implementation("io.github.kawamuray.wasmtime:wasmtime-java:latest.integration")

    testImplementation("org.assertj:assertj-core:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testImplementation("org.junit-pioneer:junit-pioneer:2.0.0")
    testImplementation("org.openrewrite:rewrite-test")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
    testRuntimeOnly("org.openrewrite:rewrite-java-17")
}

tasks.compileJava {
    // The Java `Cleaner` API requires at least Java 9
    options.release = 17
}

//tasks.named<Copy>("processResources").configure {
//    from(file("wasm/bin/Debug/net8.0/wasi-wasm/AppBundle/wasm.wasm")) {
//        into(".")
//        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//    }
//}

// We don't care about publishing javadocs anywhere, so don't waste time building them
tasks.withType<Javadoc>().configureEach {
    enabled = false
}

tasks.named<Jar>("sourcesJar") {
    enabled = false
}

tasks.named<Jar>("javadocJar") {
    enabled = false
}

val emptySourceJar = tasks.create<Jar>("emptySourceJar") {
    file("README.md")
    archiveClassifier.set("sources")
}

val emptyJavadocJar = tasks.create<Jar>("emptyJavadocJar") {
    file("README.md")
    archiveClassifier.set("javadoc")
}

publishing {
    publications.named<MavenPublication>("nebula") {
        artifactId = project.name
        description = project.description

        artifacts.clear() // remove the regular JAR
        // Empty JARs are OK: https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources
        artifact(tasks.named("jar"))
        artifact(emptySourceJar)
        artifact(emptyJavadocJar)

        pom {
            name.set(project.name)
            description.set(project.description)
            url.set("https://moderne.io")
            licenses {
                license {
                    name.set("Creative Commons Attribution-NonCommercial 4.0")
                    url.set("https://creativecommons.org/licenses/by-nc-nd/4.0/deed.en")
                }
            }
            developers {
                developer {
                    name.set("Team Moderne")
                    email.set("support@moderne.io")
                    organization.set("Moderne, Inc.")
                    organizationUrl.set("https://moderne.io")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/moderneinc/rewrite-codemods.git")
                developerConnection.set("scm:git:ssh://github.com:moderneinc/rewrite-codemods.git")
                url.set("https://github.com/moderneinc/rewrite-codemods/tree/main")
            }
        }
    }
}
