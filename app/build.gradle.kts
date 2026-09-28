plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.saygo.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.saygo.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 20
        versionName = "1.0.0"
        // Manual probes are opt-in and never replace the normal CI suite.
        val speechProbe = providers.gradleProperty("syntheticSpeechProbe").orNull == "true"
        val commandProbe = providers.gradleProperty("explicitCommandProbe").orNull == "true"
        require(!(speechProbe && commandProbe)) { "Select only one manual probe runner" }
        testInstrumentationRunner = when {
            speechProbe -> "dev.saygo.app.SyntheticSpeechProbe"
            commandProbe -> "dev.saygo.app.ExplicitCommandProbe"
            else -> "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    lint { abortOnError = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}






