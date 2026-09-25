package cz.autoskola.domain

enum class UiLanguage(val tag: String, val nativeName: String) { CS("cs", "Čeština"), RU("ru", "Русский"), UK("uk", "Українська");
    companion object { fun forTag(tag: String) = entries.find { it.tag == tag.substringBefore('-') } ?: CS }
}
enum class MaterialMode(val translationTag: String?, val label: String) { CS_ONLY(null, "Čeština"), CS_RU("ru", "Čeština + Русский"), CS_UK("uk", "Čeština + Українська") }
enum class LearningLevel { BEGINNER, INTERMEDIATE, EXAM }
enum class LicenceGroup(val code: String) { A("A"), B("B"), BE("BE"), C("C"), CE("CE"), D("D"), DE("DE") }
data class UserSettings(
    val uiLanguage: UiLanguage = UiLanguage.CS,
    val materialMode: MaterialMode = MaterialMode.CS_ONLY,
    val level: LearningLevel = LearningLevel.BEGINNER,
    val onboardingCompleted: Boolean = false,
    val licenceGroup: LicenceGroup = LicenceGroup.B
)
data class LanguagePolicy(val translationTag: String?, val showTranslation: Boolean, val canReveal: Boolean, val canLookup: Boolean)
fun UserSettings.policy(realExam: Boolean = false, revealed: Boolean = false): LanguagePolicy {
    if (realExam || level == LearningLevel.EXAM || materialMode == MaterialMode.CS_ONLY) return LanguagePolicy(null, false, false, false)
    return LanguagePolicy(materialMode.translationTag, level == LearningLevel.BEGINNER || revealed, level == LearningLevel.INTERMEDIATE, true)
}
val UserSettings.nativeCzechMode get() = materialMode == MaterialMode.CS_ONLY
fun MaterialMode.onboardingDefaults() = UserSettings(UiLanguage.forTag(translationTag ?: "cs"), this, LearningLevel.BEGINNER)
fun UiLanguage.defaultMaterialMode() = when(this) { UiLanguage.CS -> MaterialMode.CS_ONLY; UiLanguage.RU -> MaterialMode.CS_RU; UiLanguage.UK -> MaterialMode.CS_UK }
/** A legacy installation keeps its independent settings and does not get forced through onboarding. */
fun restoredSettings(ui: String?, material: String?, level: String?, completed: Boolean?, deviceLanguage: String, licenceGroup: String? = null): UserSettings {
    val legacy = ui != null || material != null || level != null || licenceGroup != null
    return UserSettings(
        UiLanguage.entries.find { it.name == ui } ?: if (legacy) UiLanguage.RU else UiLanguage.forTag(deviceLanguage),
        MaterialMode.entries.find { it.name == material } ?: if (legacy) MaterialMode.CS_RU else MaterialMode.CS_ONLY,
        LearningLevel.entries.find { it.name == level } ?: LearningLevel.BEGINNER,
        completed ?: legacy,
        LicenceGroup.entries.find { it.name == licenceGroup } ?: LicenceGroup.B
    )
}
enum class ErrorReason { RULE_UNKNOWN, CZECH_UNCLEAR, SIGN_CONFUSED, INATTENTION }
