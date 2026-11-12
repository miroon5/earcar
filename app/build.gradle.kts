import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// TTS/STT 서버 모드용 자격 증명은 local.properties에서 읽는다 (저장소에 커밋 금지)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.team.firstcar"
    compileSdk = 35
    useLibrary("android.car") // CarUxRestrictions, CarPropertyManager

    defaultConfig {
        applicationId = "com.team.firstcar"
        minSdk = 28 // 차량 보안 요건(사이닝 키 로테이션). 낮추지 말 것
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${localProps.getProperty("pleos.clientId", "")}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${localProps.getProperty("pleos.clientSecret", "")}\"")
    }

    signingConfigs {
        // 제출용 release APK 서명. keystore 정보는 local.properties에 둔다.
        create("release") {
            val path = localProps.getProperty("release.storeFile")
            if (path != null) {
                storeFile = file(path)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // 이 앱은 Vehicle SDK(조회)만 쓴다. 내비, 위치, 음성 SDK는 쓰지 않는다
    implementation(libs.pleos.vehicle)
    // 공식 샘플(pleos-sample-vehicle-app)과 같은 방식으로 도메인 API를 리플렉션으로 호출한다
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Transformer, Comparator, ProfileJson 단위 테스트 (Android 없이 JVM에서 실행)
    testImplementation(libs.junit)
    testImplementation(libs.org.json) // android.jar의 org.json 스텁 대신 실제 구현
}
