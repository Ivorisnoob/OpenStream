plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself; declaring the plugin here only pins the Kotlin version it uses.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}