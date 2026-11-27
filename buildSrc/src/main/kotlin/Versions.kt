import org.gradle.jvm.toolchain.JavaLanguageVersion

object Versions {
    const val APP_CODE = 10
    const val APP_NAME = "0.1.9"

    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 28
    const val BUILD_TOOLS = "37.0.0"

    val JAVA = JavaLanguageVersion.of(17)
}