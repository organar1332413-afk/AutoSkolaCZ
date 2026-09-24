package cz.autoskola.domain
import org.junit.Assert.*
import org.junit.Test
class SettingsTest {
    @Test fun onboardingPresetsAreCoherent() {
        assertEquals(UiLanguage.CS,MaterialMode.CS_ONLY.onboardingDefaults().uiLanguage)
        assertEquals(UiLanguage.RU,MaterialMode.CS_RU.onboardingDefaults().uiLanguage)
        assertEquals(UiLanguage.UK,MaterialMode.CS_UK.onboardingDefaults().uiLanguage)
        MaterialMode.entries.forEach { assertEquals(LearningLevel.BEGINNER,it.onboardingDefaults().level);assertFalse(it.onboardingDefaults().onboardingCompleted) }
    }
    @Test fun everyInterfaceCanUseEveryMaterialMode() {
        UiLanguage.entries.forEach { ui->MaterialMode.entries.forEach { material->
            val s=UserSettings(ui,material,LearningLevel.BEGINNER,true)
            assertEquals(material.translationTag,s.policy().translationTag)
            assertEquals(material!=MaterialMode.CS_ONLY,s.policy().showTranslation)
            assertEquals(material==MaterialMode.CS_ONLY,s.nativeCzechMode)
        } }
    }
    @Test fun interfaceSwitchDoesNotChangeLearningChoices() {
        val s=UserSettings(UiLanguage.UK,MaterialMode.CS_RU,LearningLevel.INTERMEDIATE,true)
        assertEquals(s.policy(revealed=true),s.copy(uiLanguage=UiLanguage.CS).policy(revealed=true))
    }
    @Test fun newInstallUsesDeviceLanguageAndRequiresOnboarding() {
        val cs=restoredSettings(null,null,null,null,"cs-CZ");assertEquals(UiLanguage.CS,cs.uiLanguage);assertEquals(LicenceGroup.B,cs.licenceGroup);assertFalse(cs.onboardingCompleted)
        assertEquals(UiLanguage.UK,restoredSettings(null,null,null,null,"uk-UA").uiLanguage)
        assertEquals(UiLanguage.CS,restoredSettings(null,null,null,null,"en").uiLanguage)
    }
    @Test fun legacySettingsKeepIndependentChoicesWithoutReset() {
        val s=restoredSettings("RU","CS_UK","INTERMEDIATE",null,"cs")
        assertEquals(UiLanguage.RU,s.uiLanguage);assertEquals(MaterialMode.CS_UK,s.materialMode);assertEquals(LearningLevel.INTERMEDIATE,s.level);assertTrue(s.onboardingCompleted)
    }
    @Test fun explicitOnboardingResetWinsOverLegacyDetection() { assertFalse(restoredSettings("CS","CS_ONLY","BEGINNER",false,"cs").onboardingCompleted) }
    @Test fun corruptSettingsHaveSafeDefaults() {
        val s=restoredSettings("unknown","unknown","unknown",true,"uk","unknown")
        assertEquals(UiLanguage.RU,s.uiLanguage);assertEquals(MaterialMode.CS_RU,s.materialMode);assertEquals(LearningLevel.BEGINNER,s.level);assertEquals(LicenceGroup.B,s.licenceGroup)
    }
    @Test fun licenceGroupRestoresIndependently() {
        val s=restoredSettings("UK","CS_UK","INTERMEDIATE",true,"uk","CE")
        assertEquals(LicenceGroup.CE,s.licenceGroup)
        assertEquals(MaterialMode.CS_UK,s.materialMode)
    }
    @Test fun realExamNeverEnablesAnyLanguageAssistance() {
        UiLanguage.entries.forEach { ui->MaterialMode.entries.forEach { mode->LearningLevel.entries.forEach { level->
            val policy=UserSettings(ui,mode,level,true).policy(revealed=true,realExam=true)
            assertFalse(policy.canLookup);assertFalse(policy.canReveal);assertFalse(policy.showTranslation)
        } } }
    }
}
