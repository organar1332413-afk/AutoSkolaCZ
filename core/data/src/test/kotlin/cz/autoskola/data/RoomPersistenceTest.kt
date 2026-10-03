package cz.autoskola.data
import androidx.room.Room
import cz.autoskola.data.db.AutoSkolaDatabase
import cz.autoskola.data.db.entity.*
import cz.autoskola.data.importer.ContentImporter
import cz.autoskola.data.importer.InstalledMedia
import cz.autoskola.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class RoomPersistenceTest {
    private lateinit var db:AutoSkolaDatabase
    private lateinit var importer:ContentImporter
    private lateinit var root:File
    private fun sample()=javaClass.getResourceAsStream("/sample-v1.json")!!.use { it.readBytes() }
    @Before fun setup() { val context=RuntimeEnvironment.getApplication();db=Room.inMemoryDatabaseBuilder(context,AutoSkolaDatabase::class.java).allowMainThreadQueries().build();root=File(context.cacheDir,"room-test-${System.nanoTime()}");importer=ContentImporter(db,root) }
    @After fun close() { db.close();root.deleteRecursively() }
    @Test fun importIsIdempotentAndFailedUpdateDoesNotReplaceActiveBank()=runBlocking {
        val bytes=sample();val version=importer.importPackage(bytes);importer.importPackage(bytes)
        assertEquals(3,db.content().questions().first().size)
        var rejected=false
        try { importer.importPackage(bytes.decodeToString().replace("RP0604222","RP0606185").encodeToByteArray()) } catch(_:IllegalArgumentException) { rejected=true }
        assertTrue(rejected);assertEquals(version,db.content().activeVersion().first()!!.id)
        rejected=false
        try { importer.importPackage(bytes.decodeToString().replace("Транспортное средство","Транспорт").encodeToByteArray()) } catch(_:IllegalArgumentException) { rejected=true }
        assertTrue(rejected)
    }
    @Test fun dataStoreKeepsInterfaceIndependentAndCompletesOnboardingAtomically()=runBlocking {
        val store=SettingsStore(RuntimeEnvironment.getApplication())
        store.completeOnboarding(UserSettings(UiLanguage.CS,MaterialMode.CS_UK,LearningLevel.INTERMEDIATE,true))
        var current=store.settings.first();assertTrue(current.onboardingCompleted);assertEquals(UiLanguage.CS,current.uiLanguage);assertEquals(MaterialMode.CS_UK,current.materialMode)
        store.setUiLanguage(UiLanguage.RU);current=store.settings.first();assertEquals(MaterialMode.CS_UK,current.materialMode);assertEquals(LearningLevel.INTERMEDIATE,current.level)
        store.setMaterialMode(MaterialMode.CS_ONLY);current=store.settings.first();assertEquals(UiLanguage.RU,current.uiLanguage);assertEquals(MaterialMode.CS_ONLY,current.materialMode)
    }
    @Test fun attemptsAreIdempotentAndReasonsPersist()=runBlocking {
        importer.importPackage(sample());val repo=LearningRepository(db);val q=db.content().questions().first().first();val wrong=db.learning().answers(q.id).first { !it.correct }.code
        assertFalse(repo.answer("attempt",q.id,wrong,UserSettings(),0));repo.answer("attempt",q.id,wrong,UserSettings(),0)
        repo.reason("attempt",ErrorReason.CZECH_UNCLEAR)
        val saved=repo.snapshot.first();assertEquals(1,saved.attempts.size);assertEquals(ErrorReason.CZECH_UNCLEAR,saved.attempts.single().reason);assertTrue(saved.attempts.single().sample)
        assertEquals(0,saved.reviews[q.questionId]!!.streak)
    }
    @Test fun invalidAnswerRollsBackWithoutAttemptOrProgress()=runBlocking {
        importer.importPackage(sample());val repo=LearningRepository(db);val q=db.content().questions().first().first()
        var rejected=false;try { repo.answer("bad",q.id,"NOT_AN_ANSWER",UserSettings(),0) } catch(_:Exception) { rejected=true }
        assertTrue(rejected);assertTrue(repo.snapshot.first().attempts.isEmpty());assertTrue(repo.snapshot.first().reviews.isEmpty())
    }
    @Test fun favoritesPersistAndCanBeRemoved()=runBlocking {
        importer.importPackage(sample());val repo=LearningRepository(db);val id=db.content().questions().first().first().questionId
        repo.favorite(id,true);repo.favorite(id,true);assertEquals(setOf(id),repo.snapshot.first().favorites)
        repo.favorite(id,false);assertTrue(repo.snapshot.first().favorites.isEmpty())
    }
    @Test fun wordReviewsUpdateBothHistoryAndCounters()=runBlocking {
        db.words().insertWords(listOf(DictionaryWordEntity("word","TEST","TEST","TEST","draft")))
        val study=RoomStudyRepository(db);study.saveWord("word");study.saveWord("word")
        val repo=LearningRepository(db);repo.wordReview("word",true);repo.wordReview("word",false)
        val word=study.words("cs").first().single();assertEquals(2,word.repetitions);assertEquals(1,word.correctCount)
        repo.removeWord("word");assertFalse(study.words("cs").first().single().saved)
    }
    @Test fun savedWordsSurviveDatabaseReopenAndDuplicateSavesKeepReviewCounters() = runBlocking {
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
        val name = "saved-words-${System.nanoTime()}.db"
        var disk = Room.databaseBuilder(context, AutoSkolaDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            disk.words().insertWords(listOf(DictionaryWordEntity("ridic", "řidič", "road_traffic", "", "draft")))
            val study = RoomStudyRepository(disk)
            study.saveWord("ridic")
            LearningRepository(disk).wordReview("ridic", true)
            study.saveWord("ridic")
            study.saveUnknownWord("Chodci")
            study.saveUnknownWord("chodci")
            disk.close()
            disk = Room.databaseBuilder(context, AutoSkolaDatabase::class.java, name).allowMainThreadQueries().build()
            val reopened = RoomStudyRepository(disk).words("ru").first()
            assertEquals(2, reopened.size)
            assertTrue(reopened.all { it.saved })
            assertEquals(1, reopened.single { it.id == "ridic" }.repetitions)
            assertEquals(1, reopened.single { it.id == "ridic" }.correctCount)
            assertNull(reopened.single { it.lemma == "chodci" }.translation)
            assertEquals(2, disk.words().saved().first().size)
        } finally { disk.close(); context.deleteDatabase(name) }
    }
    @Test fun lookupTipDismissalUsesExistingSettingsDataStore() = runBlocking {
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
        val first = SettingsStore(context)
        first.dismissLookupTip()
        assertTrue(SettingsStore(context).lookupTipSeen.first())
    }
    @Test fun unknownWordCanBeSavedWithoutInventedTranslation()=runBlocking {
        val study=RoomStudyRepository(db);study.saveUnknownWord("Chodce");study.saveUnknownWord("chodce")
        val word=study.words("ru").first().single();assertTrue(word.saved);assertEquals("chodce",word.lemma);assertNull(word.translation);assertEquals("",word.exampleCs)
    }
    @Test fun questionAssessmentPersistsAndReplacesPreviousValue()=runBlocking {
        val store=QuestionAssessmentStore(RuntimeEnvironment.getApplication())
        store.set("assessment-test",QuestionAssessment.DOUBTFUL)
        assertEquals(QuestionAssessment.DOUBTFUL,store.assessments.first()["assessment-test"])
        store.set("assessment-test",QuestionAssessment.KNOWN)
        assertEquals(QuestionAssessment.KNOWN,store.assessments.first()["assessment-test"])
    }
    @Test fun lessonProgressAndTranslationUseExistingTables()=runBlocking {
        db.openHelper.writableDatabase.execSQL("INSERT INTO Lesson VALUES('lesson','demo','Český název','TEST','test','draft',5)")
        db.openHelper.writableDatabase.execSQL("INSERT INTO LessonTranslation VALUES('lesson','uk','Назва')")
        val repo=LearningRepository(db);repo.progress("lesson",3,true)
        assertNotNull(repo.snapshot.first().progress.single().completedAt)
        assertEquals("Назва",repo.lessons("uk").first().single().title)
        assertNull(repo.lessons(null).first().single().title)
    }
    private fun testBank():QuestionPackage {
        val points=mapOf("rules" to 2,"safe_driving" to 2,"signs" to 1,"situations" to 4,"vehicle" to 1,"related" to 2,"first_aid" to 1)
        return QuestionPackage(ContentManifest(databaseVersion="TEST_ONLY",publicationDate="2025-01-01",source="https://etesty.md.gov.cz/TEST_ONLY",retrievedAt="2025-01-01T00:00:00Z",sample=false,completeForB=true),ExamConfigurationProvider.forGroup(LicenceGroup.B).sections.associate { it.category to it.questionCount }.flatMap { (category,n)->(0 until n).map { index->OfficialQuestion("TEST_${category}_$index",category,"TEST ONLY",points[category],listOf("B"),listOf(OfficialAnswer("A","TEST A",true),OfficialAnswer("B","TEST B",false)),source="https://etesty.md.gov.cz/TEST_ONLY") } })
    }
    @Test fun examResumesPinnedRevisionsAfterBankUpdateAndRecordsUnansweredMistakes()=runBlocking {
        val pack=testBank();importer.importPackage(Json.encodeToString(QuestionPackage.serializer(),pack).encodeToByteArray())
        var clock=100L;val exams=ExamRepository(db) { clock };val (id,session)=exams.start(LicenceGroup.B)
        SettingsStore(RuntimeEnvironment.getApplication()).setLicenceGroup(LicenceGroup.C)
        assertEquals(LicenceGroup.B,session.licenceGroup)
        val cards=exams.cards(session);assertEquals(25,cards.size);assertEquals(session.items.map { it.revisionId },cards.map { it.revisionId })
        val q=session.items.first();exams.answer(id,q.revisionId,"A")
        val newer=pack.copy(manifest=pack.manifest.copy(databaseVersion="TEST_NEW"));importer.importPackage(Json.encodeToString(QuestionPackage.serializer(),newer).encodeToByteArray())
        val resumed=exams.unfinished()!!.second;assertEquals("TEST_ONLY",resumed.version);assertEquals("A",resumed.answers[q.revisionId]);assertEquals(LicenceGroup.B,resumed.licenceGroup)
        assertEquals(session.items.map { it.revisionId },resumed.items.map { it.revisionId })
        clock=session.deadlineAt+1;val ended=exams.resume(id);assertNotNull(ended.completedAt);assertEquals(q.points,ExamEngine.result(ended).score)
        assertNull(exams.unfinished());val snapshot=LearningRepository(db).snapshot.first();assertEquals(25,snapshot.attempts.size)
        assertEquals(1,snapshot.examHistory.size);assertEquals(q.points,snapshot.examHistory.single().score);assertEquals(LicenceGroup.B,snapshot.examHistory.single().licenceGroup)
        exams.finish(id);assertEquals(25,LearningRepository(db).snapshot.first().attempts.size)
    }
    @Test fun v2BankStartsOnlyForItsVerifiedGroupAndPersistsSnapshot()=runBlocking {
        val base=testBank();val source=base.manifest.source
        val pack=base.copy(manifest=base.manifest.copy(formatVersion=2,completeForB=false,
            groupReadiness=listOf(GroupReadiness("C",ExamConfigurationProvider.CURRENT_VERSION,true,true,true,source))),
            questions=base.questions.map { it.copy(licenceGroups=listOf("C"),eligibility=listOf(QuestionEligibility("C",source))) })
        importer.importPackage(ContentPackageCodec.encodeV2(pack).encodeToByteArray())
        val exams=ExamRepository(db) { 100L }
        assertEquals(ExamAvailability.ELIGIBILITY_INCOMPLETE,exams.availability(LicenceGroup.B))
        assertEquals(ExamAvailability.READY,exams.availability(LicenceGroup.C))
        var rejected=false
        try { exams.start(LicenceGroup.B) } catch(_:IllegalArgumentException) { rejected=true }
        assertTrue(rejected)
        val (id,session)=exams.start(LicenceGroup.C)
        assertEquals(LicenceGroup.C,session.licenceGroup)
        assertEquals("C",db.learning().exam(id)!!.licenceGroup)
        assertEquals(session,exams.resume(id))
        val finished=exams.finish(id)
        assertEquals(finished,exams.finish(id))
        val history=LearningRepository(db).snapshot.first().examHistory.single()
        assertEquals(LicenceGroup.C,history.licenceGroup)
        assertEquals(session.maxPoints,history.maxPoints)
        assertEquals(session.passPoints,history.passPoints)
    }
    @Test fun sampleExamStartIsRejectedWithoutPartialRows()=runBlocking {
        val bytes=sample();importer.importPackage(bytes);var rejected=false
        try { ExamRepository(db).start(LicenceGroup.B, Json.decodeFromString(bytes.decodeToString())) } catch(_:IllegalArgumentException) { rejected=true }
        assertTrue(rejected);assertNull(db.learning().unfinishedExam())
    }

    @Test fun examAttemptsAndErrorsAreJoinedToTheirPersistedGroups()=runBlocking {
        val base=testBank();val source=base.manifest.source
        val pack=base.copy(manifest=base.manifest.copy(formatVersion=2,completeForB=false,
            groupReadiness=listOf("B","C").map { GroupReadiness(it,ExamConfigurationProvider.CURRENT_VERSION,true,true,true,source) }),
            questions=base.questions.map { it.copy(licenceGroups=listOf("B","C"),
                eligibility=listOf("B","C").map { group->QuestionEligibility(group,source) }) })
        importer.importPackage(ContentPackageCodec.encodeV2(pack).encodeToByteArray())
        val exams=ExamRepository(db) { 100L }
        val (bId,bSession)=exams.start(LicenceGroup.B)
        bSession.items.forEach { exams.answer(bId,it.revisionId,it.correctCode) }
        exams.finish(bId)
        val (cId,cSession)=exams.start(LicenceGroup.C)
        cSession.items.forEach { item->exams.answer(cId,item.revisionId,item.answerCodes.single { it!=item.correctCode }) }
        exams.finish(cId)
        LearningRepository(db).reason("exam:$cId:${cSession.items.first().officialId}",ErrorReason.CZECH_UNCLEAR)
        val state=LearningRepository(db).snapshot.first()
        assertEquals(25,state.attempts.count { it.examLicenceGroup==LicenceGroup.B })
        assertEquals(25,state.attempts.count { it.examLicenceGroup==LicenceGroup.C })
        val b=state.examStatistics(LicenceGroup.B);val c=state.examStatistics(LicenceGroup.C)
        assertEquals(100,b.correctPercent);assertEquals(0,c.correctPercent)
        assertTrue(b.weakTopics.isEmpty());assertTrue(c.weakTopics.isNotEmpty())
        assertEquals(0,b.errors(ErrorReason.CZECH_UNCLEAR));assertEquals(1,c.errors(ErrorReason.CZECH_UNCLEAR))
        assertEquals(100,b.passRate);assertEquals(0,c.passRate)
        assertEquals(100.0,b.averagePercent!!,0.01);assertEquals(0.0,c.averagePercent!!,0.01)
        assertEquals(cId,state.lastExamFor(LicenceGroup.C)?.id)
    }

    @Test fun corruptedPersistedGroupAndBlueprintRejectResumeAndHistory()=runBlocking {
        val pack=testBank();importer.importPackage(Json.encodeToString(QuestionPackage.serializer(),pack).encodeToByteArray())
        val exams=ExamRepository(db) { 100L }
        val (id,_)=exams.start(LicenceGroup.B)
        db.openHelper.writableDatabase.execSQL("UPDATE ExamAttempt SET licenceGroup='XYZ' WHERE id=?", arrayOf(id))
        try { exams.resume(id);fail("Corrupted group was accepted") } catch(e:IllegalStateException) {
            assertTrue(e.message!!.contains("Corrupted exam licence group"))
        }
        db.openHelper.writableDatabase.execSQL("UPDATE ExamAttempt SET licenceGroup='B', blueprintVersion='unsupported' WHERE id=?", arrayOf(id))
        try { exams.resume(id);fail("Unsupported blueprint was accepted") } catch(_:IllegalArgumentException) { }
        db.openHelper.writableDatabase.execSQL("UPDATE ExamAttempt SET blueprintVersion='B-stage2-v1' WHERE id=?", arrayOf(id))
        exams.finish(id)
        db.openHelper.writableDatabase.execSQL("UPDATE ExamAttempt SET licenceGroup='XYZ' WHERE id=?", arrayOf(id))
        try { LearningRepository(db).snapshot.first();fail("Corrupted history was relabelled") } catch(e:IllegalStateException) {
            assertTrue(e.message!!.contains("Corrupted exam licence group"))
        }
    }

    @Test fun identicalImportRepairsMissingAndCorruptMediaWithoutChangingRoomRows()=runBlocking {
        val wirePath="media/sign.png"
        val original="fixture image bytes".encodeToByteArray()
        val mediaHash=MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it.toInt() and 255) }
        val sourceRoot=File(root,"source")
        File(sourceRoot,wirePath).apply { parentFile!!.mkdirs();writeBytes(original) }
        val pack=testBank().let { base->base.copy(questions=base.questions.map {
            it.copy(media=listOf(MediaReference(wirePath,mediaHash,"image/png")))
        }) }
        val bytes=Json.encodeToString(QuestionPackage.serializer(),pack).encodeToByteArray()
        val version=importer.importPackage(bytes,sourceRoot)
        val installedHash=db.content().version(version)!!.packageSha256
        val stored=InstalledMedia.storedPath(installedHash,wirePath)
        assertEquals(wirePath,InstalledMedia.wirePath(installedHash,stored))
        assertEquals(setOf(stored),db.content().media().first().map { it.path }.toSet())
        val installed=InstalledMedia.file(root,installedHash,wirePath)
        val exams=ExamRepository(db,root) { 100L }
        assertEquals(ExamAvailability.READY,exams.availability(LicenceGroup.B)) // Room -> wire -> installed file
        val (id,session)=exams.start(LicenceGroup.B)
        assertEquals(stored,exams.cards(session).first().media.single().path)
        exams.finish(id)
        val beforeQuestions=db.content().questions().first().size
        val beforeMedia=db.content().media().first().size
        val beforeAttempts=db.learning().completedExams().first().size
        installed.delete()
        assertEquals(ExamAvailability.MEDIA_INCOMPLETE,exams.availability(LicenceGroup.B))
        try { importer.importPackage(bytes);fail("Missing media without a source was accepted") }
        catch(_:IllegalArgumentException) { }
        assertEquals(version,importer.importPackage(bytes,sourceRoot))
        assertArrayEquals(original,installed.readBytes())
        installed.writeText("corrupted")
        assertEquals(ExamAvailability.MEDIA_INCOMPLETE,exams.availability(LicenceGroup.B))
        assertEquals(version,importer.importPackage(bytes,sourceRoot))
        assertArrayEquals(original,installed.readBytes())
        assertEquals(ExamAvailability.READY,exams.availability(LicenceGroup.B))
        assertEquals(beforeQuestions,db.content().questions().first().size)
        assertEquals(beforeMedia,db.content().media().first().size)
        assertEquals(beforeAttempts,db.learning().completedExams().first().size)
        assertEquals(version,db.content().activeVersion().first()!!.id)
    }
}

