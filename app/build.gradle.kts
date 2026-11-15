import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.lifeworkbench"
    compileSdk = 34

    // 固定调试签名：每次打包都用同一个签名，这样新版本才能直接覆盖安装旧版本，不用卸载
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    defaultConfig {
        applicationId = "com.example.lifeworkbench"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { 
        compose = true 
    }
    composeOptions { 
        kotlinCompilerExtensionVersion = "1.5.3" 
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // 阶段 2 需要 java.time（LocalDate 等）做日期导航与时区换算；minSdk 24 < 26，
        // 需核心库脱糖才能使用（工具链特性，非新框架）
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    // 迁移测试用：把 Room 导出的 Schema（app/schemas）并入单元测试 assets，
    // MigrationTestHelper 按数据库类名在 assets 里找 <版本>.json
    sourceSets {
        getByName("test") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
    // 测试失败时在控制台打印完整堆栈，便于定位
    tasks.withType<Test> {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

// Room Schema 导出位置（V1.1 阶段 1）：编译时把 Schema JSON 写到 app/schemas，供版本追溯与 Migration 测试使用
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    // 阶段 2：ViewModel（viewModelScope）与 Compose 里的 viewModel(factory) 依赖
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    // 核心库脱糖（java.time，见 compileOptions）
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // Room Database for local offline storage
    val room_version = "2.6.0"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // 单元测试：TaskStatus 状态映射（V1.1 阶段 0）
    testImplementation("junit:junit:4.13.2")
    // Room 内存数据库测试（Robolectric 真实 Android 项目环境）
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // 迁移测试：用真实旧 Schema 创建数据库并校验迁移
    testImplementation("androidx.room:room-testing:2.6.0")
    // Compose UI 测试（补充验收任务 1）：Robolectric 图形模式 + createAndroidComposeRule，
    // 版本与 Compose BOM 2023.03.00 一致（ui 1.4.0），androidx.test 1.5.x 配套
    testImplementation("androidx.compose.ui:ui-test-junit4:1.4.0")
    testImplementation("androidx.compose.ui:ui-test-manifest:1.4.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    // 插桩测试（补充验收任务 2）：只在模拟器/真机上运行，不走 testDebugUnitTest
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.4.0")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest:1.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
}
