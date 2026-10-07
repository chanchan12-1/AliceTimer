plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "cn.alice.mockgame"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "test-only" }
    flavorDimensions += "server"
    productFlavors {
        create("cn") { dimension = "server"; applicationId = "com.RoamingStar.BlueArchive"; resValue("string", "app_name", "测试空壳 · 蔚蓝档案") }
        create("jp") { dimension = "server"; applicationId = "com.YostarJP.BlueArchive"; resValue("string", "app_name", "测试空壳 · ブルーアーカイブ") }
        create("global") { dimension = "server"; applicationId = "com.nexon.bluearchive"; resValue("string", "app_name", "测试空壳 · Blue Archive") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
