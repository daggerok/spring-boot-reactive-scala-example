import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
  idea
  scala
  id("io.spring.dependency-management") version "1.0.7.RELEASE"
  id("org.springframework.boot") version "2.2.0.BUILD-SNAPSHOT"
  id("com.avast.gradle.docker-compose").version("0.9.1")
  id("com.github.ben-manes.versions") version "0.21.0"
  id("com.moowork.node") version "1.3.1"
}

val javaVersion = JavaVersion.VERSION_1_8
val scalaVersion: String by project
val scalaMajorVersion: String by project
val springBootVersion: String by project
val reactiveStreamsVersion: String by project
val akkaStreamVersion: String by project
val junitJupiterVersion: String by project
val gradleWrapperVersion: String by project
val targetNodeVersion: String by project
val targetNpmVersion: String by project

extra["scala.version"] = scalaVersion
extra["junit-jupiter.version"] = junitJupiterVersion

tasks.withType<Wrapper>().configureEach {
  gradleVersion = gradleWrapperVersion
  distributionType = Wrapper.DistributionType.BIN
}

java {
  sourceCompatibility = javaVersion
  targetCompatibility = javaVersion
}

sourceSets {
  main {
    java.srcDir("src/main/scala")
  }
  test {
    java.srcDir("src/test/scala")
  }
}

repositories {
  mavenCentral()
  maven(url = "https://repo.spring.io/snapshot")
  maven(url = "https://repo.spring.io/milestone")
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
    mavenBom("org.reactivestreams:reactive-streams:$reactiveStreamsVersion")
  }
}

dependencies {
  implementation("com.typesafe.akka:akka-stream_$scalaMajorVersion:$akkaStreamVersion")
  implementation("org.scala-lang:scala-library:$scalaVersion")
  implementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  runtimeOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")

  testImplementation("junit:junit")
  testImplementation(platform("org.junit:junit-bom:$junitJupiterVersion"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
  testRuntime("org.junit.platform:junit-platform-launcher")
}

tasks.withType<BootJar>().configureEach {
  launchScript()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    showExceptions = true
    showStandardStreams = true
    events(PASSED, SKIPPED, FAILED)
  }
}

node {
  download = true
  version = targetNodeVersion
  npmVersion = targetNpmVersion
}

tasks.create("start")
tasks["start"].dependsOn("npm_start")
tasks["npm_start"].dependsOn("npm_i")
tasks["build"].dependsOn("npm_run_build")
tasks["npm_run_build"].dependsOn("npm_install")

val dockerPs: Task = tasks.create<Exec>("dockerPs") {
  shouldRunAfter("clean", "assemble")
  executable = "docker"
  args("ps", "-a", "-f", "name=${project.name}")
}

tasks["composeUp"].dependsOn("assemble")
tasks["composeUp"].shouldRunAfter("clean", "assemble")
dockerCompose {
  isRequiredBy(dockerPs)
}

// gradle dependencyUpdates -Drevision=release --parallel
tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
  resolutionStrategy {
    componentSelection {
      all {
        val rejected = listOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea", "SNAPSHOT")
            .map { qualifier -> Regex("(?i).*[.-]$qualifier[.\\d-+]*") }
            .any { it.matches(candidate.version) }
        if (rejected) reject("Release candidate")
      }
    }
  }
}

tasks {
  getByName("clean") {
    doLast {
      delete(
          project.buildDir,
          "${project.projectDir}/.vuepress/dist"
      )
    }
  }
}

tasks.create<Zip>("sources") {
  group = "Archive"
  description = "Archives sources in a zip archive"
  dependsOn("clean")
  shouldRunAfter("clean")
  from(".vuepress") {
    into(".vuepress")
  }
  from("src") {
    into("src")
  }
  from(
      ".gitignore",
      "build.gradle.kts",
      "docker-compose.yaml",
      "gradle.properties",
      "LICENSE",
      "package.json",
      "package-lock.json",
      "README.md",
      "settings.gradle.kts"
  )
  archiveFileName.set("${project.buildDir}/sources-${project.version}.zip")
}

defaultTasks("build")
