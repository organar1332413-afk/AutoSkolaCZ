-- Design DDL generated from the same definitions as the Room entities.
-- NOT a Room export: actual Room schema JSON is produced by KSP at build time.
PRAGMA foreign_keys=ON;
CREATE TABLE "DatabaseVersion" (
  "id" TEXT NOT NULL,
  "databaseVersion" TEXT NOT NULL,
  "publicationDate" TEXT,
  "source" TEXT NOT NULL,
  "retrievedAt" TEXT NOT NULL,
  "importDate" INTEGER NOT NULL,
  "sample" INTEGER NOT NULL,
  "completeForB" INTEGER NOT NULL,
  "packageSha256" TEXT NOT NULL,
  PRIMARY KEY ("id")
);
CREATE TABLE "ActiveContent" (
  "slot" INTEGER NOT NULL,
  "versionId" TEXT NOT NULL,
  PRIMARY KEY ("slot"),
  FOREIGN KEY ("versionId") REFERENCES "DatabaseVersion" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_ActiveContent_versionId" ON "ActiveContent" ("versionId");
CREATE TABLE "QuestionCategory" (
  "id" TEXT NOT NULL,
  "nameCs" TEXT NOT NULL,
  PRIMARY KEY ("id")
);
CREATE TABLE "QuestionCategoryTranslation" (
  "categoryId" TEXT NOT NULL,
  "locale" TEXT NOT NULL,
  "name" TEXT NOT NULL,
  PRIMARY KEY ("categoryId", "locale"),
  FOREIGN KEY ("categoryId") REFERENCES "QuestionCategory" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_QuestionCategoryTranslation_categoryId" ON "QuestionCategoryTranslation" ("categoryId");
CREATE TABLE "Question" (
  "id" TEXT NOT NULL,
  "officialId" TEXT NOT NULL,
  PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "index_Question_officialId" ON "Question" ("officialId");
CREATE TABLE "QuestionRevision" (
  "id" TEXT NOT NULL,
  "questionId" TEXT NOT NULL,
  "versionId" TEXT NOT NULL,
  "categoryId" TEXT NOT NULL,
  "textCs" TEXT NOT NULL,
  "points" INTEGER,
  "source" TEXT NOT NULL,
  PRIMARY KEY ("id"),
  FOREIGN KEY ("questionId") REFERENCES "Question" ("id") ON DELETE NO ACTION,
  FOREIGN KEY ("versionId") REFERENCES "DatabaseVersion" ("id") ON DELETE NO ACTION,
  FOREIGN KEY ("categoryId") REFERENCES "QuestionCategory" ("id") ON DELETE NO ACTION
);
CREATE UNIQUE INDEX "index_QuestionRevision_versionId_questionId" ON "QuestionRevision" ("versionId", "questionId");
CREATE INDEX "index_QuestionRevision_questionId" ON "QuestionRevision" ("questionId");
CREATE INDEX "index_QuestionRevision_versionId" ON "QuestionRevision" ("versionId");
CREATE INDEX "index_QuestionRevision_categoryId" ON "QuestionRevision" ("categoryId");
CREATE TABLE "QuestionLicenceGroup" (
  "revisionId" TEXT NOT NULL,
  "licenceGroup" TEXT NOT NULL,
  PRIMARY KEY ("revisionId", "licenceGroup"),
  FOREIGN KEY ("revisionId") REFERENCES "QuestionRevision" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_QuestionLicenceGroup_revisionId" ON "QuestionLicenceGroup" ("revisionId");
CREATE TABLE "Answer" (
  "revisionId" TEXT NOT NULL,
  "code" TEXT NOT NULL,
  "textCs" TEXT NOT NULL,
  "correct" INTEGER NOT NULL,
  "position" INTEGER NOT NULL,
  PRIMARY KEY ("revisionId", "code"),
  FOREIGN KEY ("revisionId") REFERENCES "QuestionRevision" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_Answer_revisionId" ON "Answer" ("revisionId");
CREATE TABLE "QuestionTranslation" (
  "revisionId" TEXT NOT NULL,
  "locale" TEXT NOT NULL,
  "text" TEXT NOT NULL,
  "explanation" TEXT NOT NULL,
  "reviewStatus" TEXT NOT NULL,
  PRIMARY KEY ("revisionId", "locale"),
  FOREIGN KEY ("revisionId") REFERENCES "QuestionRevision" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_QuestionTranslation_revisionId" ON "QuestionTranslation" ("revisionId");
CREATE TABLE "AnswerTranslation" (
  "revisionId" TEXT NOT NULL,
  "answerCode" TEXT NOT NULL,
  "locale" TEXT NOT NULL,
  "text" TEXT NOT NULL,
  PRIMARY KEY ("revisionId", "answerCode", "locale"),
  FOREIGN KEY ("revisionId", "answerCode") REFERENCES "Answer" ("revisionId", "code") ON DELETE NO ACTION
);
CREATE INDEX "index_AnswerTranslation_revisionId_answerCode" ON "AnswerTranslation" ("revisionId", "answerCode");
CREATE TABLE "QuestionMedia" (
  "id" TEXT NOT NULL,
  "revisionId" TEXT NOT NULL,
  "answerCode" TEXT,
  "path" TEXT NOT NULL,
  "sha256" TEXT NOT NULL,
  "mimeType" TEXT NOT NULL,
  "position" INTEGER NOT NULL,
  PRIMARY KEY ("id"),
  FOREIGN KEY ("revisionId") REFERENCES "QuestionRevision" ("id") ON DELETE NO ACTION,
  FOREIGN KEY ("revisionId", "answerCode") REFERENCES "Answer" ("revisionId", "code") ON DELETE NO ACTION
);
CREATE INDEX "index_QuestionMedia_revisionId" ON "QuestionMedia" ("revisionId");
CREATE INDEX "index_QuestionMedia_revisionId_answerCode" ON "QuestionMedia" ("revisionId", "answerCode");
CREATE TABLE "TrafficSign" (
  "id" TEXT NOT NULL,
  "officialCode" TEXT NOT NULL,
  "nameCs" TEXT NOT NULL,
  "descriptionCs" TEXT NOT NULL,
  "imagePath" TEXT NOT NULL,
  "source" TEXT NOT NULL,
  "publicationDate" TEXT,
  "reviewStatus" TEXT NOT NULL,
  PRIMARY KEY ("id")
);
CREATE TABLE "TrafficSignTranslation" (
  "signId" TEXT NOT NULL,
  "locale" TEXT NOT NULL,
  "name" TEXT NOT NULL,
  "explanation" TEXT NOT NULL,
  PRIMARY KEY ("signId", "locale"),
  FOREIGN KEY ("signId") REFERENCES "TrafficSign" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_TrafficSignTranslation_signId" ON "TrafficSignTranslation" ("signId");
CREATE TABLE "Lesson" (
  "id" TEXT NOT NULL,
  "topic" TEXT NOT NULL,
  "titleCs" TEXT NOT NULL,
  "source" TEXT NOT NULL,
  "contentVersion" TEXT NOT NULL,
  "reviewStatus" TEXT NOT NULL,
  "estimatedMinutes" INTEGER NOT NULL,
  PRIMARY KEY ("id")
);
CREATE TABLE "LessonTranslation" (
  "lessonId" TEXT NOT NULL,
  "locale" TEXT NOT NULL,
  "title" TEXT NOT NULL,
  PRIMARY KEY ("lessonId", "locale"),
  FOREIGN KEY ("lessonId") REFERENCES "Lesson" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_LessonTranslation_lessonId" ON "LessonTranslation" ("lessonId");
CREATE TABLE "LessonBlock" (
  "id" TEXT NOT NULL,
  "lessonId" TEXT NOT NULL,
  "position" INTEGER NOT NULL,
  "kind" TEXT NOT NULL,
  "textCs" TEXT NOT NULL,
  "mediaPath" TEXT,
  PRIMARY KEY ("id"),
  FOREIGN KEY ("lessonId") REFERENCES "Lesson" ("id") ON DELETE NO ACTION
);
CREATE UNIQUE INDEX "index_LessonBlock_lessonId_position" ON "LessonBlock" ("lessonId", "position");
CREATE INDEX "index_LessonBlock_lessonId" ON "LessonBlock" ("lessonId");
CREATE TABLE "LessonBlockTranslation" (
  "blockId" TEXT NOT NULL,
  "locale" TEXT NOT NULL,
  "text" TEXT NOT NULL,
  PRIMARY KEY ("blockId", "locale"),
  FOREIGN KEY ("blockId") REFERENCES "LessonBlock" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_LessonBlockTranslation_blockId" ON "LessonBlockTranslation" ("blockId");
CREATE TABLE "LessonQuestion" (
  "lessonId" TEXT NOT NULL,
  "questionId" TEXT NOT NULL,
  "position" INTEGER NOT NULL,
  PRIMARY KEY ("lessonId", "questionId"),
  FOREIGN KEY ("lessonId") REFERENCES "Lesson" ("id") ON DELETE NO ACTION,
  FOREIGN KEY ("questionId") REFERENCES "Question" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_LessonQuestion_lessonId" ON "LessonQuestion" ("lessonId");
CREATE INDEX "index_LessonQuestion_questionId" ON "LessonQuestion" ("questionId");
CREATE TABLE "DictionaryWord" (
  "id" TEXT NOT NULL,
  "lemma" TEXT NOT NULL,
  "context" TEXT NOT NULL,
  "exampleCs" TEXT NOT NULL,
  "reviewStatus" TEXT NOT NULL,
  PRIMARY KEY ("id")
);
CREATE TABLE "DictionaryForm" (
  "wordId" TEXT NOT NULL,
  "form" TEXT NOT NULL,
  PRIMARY KEY ("wordId", "form"),
  FOREIGN KEY ("wordId") REFERENCES "DictionaryWord" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_DictionaryForm_wordId" ON "DictionaryForm" ("wordId");
CREATE TABLE "DictionaryTranslation" (
  "wordId" TEXT NOT NULL,
  "locale" TEXT NOT NULL,
  "translation" TEXT NOT NULL,
  "meaning" TEXT NOT NULL,
  "exampleTranslation" TEXT NOT NULL,
  PRIMARY KEY ("wordId", "locale"),
  FOREIGN KEY ("wordId") REFERENCES "DictionaryWord" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_DictionaryTranslation_wordId" ON "DictionaryTranslation" ("wordId");
CREATE TABLE "SavedWord" (
  "wordId" TEXT NOT NULL,
  "addedAt" INTEGER NOT NULL,
  "repetitions" INTEGER NOT NULL,
  "correctCount" INTEGER NOT NULL,
  "nextReviewAt" INTEGER,
  PRIMARY KEY ("wordId"),
  FOREIGN KEY ("wordId") REFERENCES "DictionaryWord" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_SavedWord_wordId" ON "SavedWord" ("wordId");
CREATE TABLE "FavoriteQuestion" (
  "questionId" TEXT NOT NULL,
  "addedAt" INTEGER NOT NULL,
  PRIMARY KEY ("questionId"),
  FOREIGN KEY ("questionId") REFERENCES "Question" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_FavoriteQuestion_questionId" ON "FavoriteQuestion" ("questionId");
CREATE TABLE "QuestionAttempt" (
  "id" TEXT NOT NULL,
  "revisionId" TEXT NOT NULL,
  "answerCode" TEXT NOT NULL,
  "createdAt" INTEGER NOT NULL,
  "correct" INTEGER NOT NULL,
  "errorReason" TEXT,
  "materialMode" TEXT NOT NULL,
  "level" TEXT NOT NULL,
  PRIMARY KEY ("id"),
  FOREIGN KEY ("revisionId", "answerCode") REFERENCES "Answer" ("revisionId", "code") ON DELETE NO ACTION
);
CREATE INDEX "index_QuestionAttempt_revisionId_answerCode" ON "QuestionAttempt" ("revisionId", "answerCode");
CREATE TABLE "ExamAttempt" (
  "id" TEXT NOT NULL,
  "versionId" TEXT NOT NULL,
  "blueprintVersion" TEXT NOT NULL,
  "startedAt" INTEGER NOT NULL,
  "deadlineAt" INTEGER NOT NULL,
  "completedAt" INTEGER,
  "score" INTEGER,
  "mode" TEXT NOT NULL,
  PRIMARY KEY ("id"),
  FOREIGN KEY ("versionId") REFERENCES "DatabaseVersion" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_ExamAttempt_versionId" ON "ExamAttempt" ("versionId");
CREATE TABLE "ExamAnswer" (
  "examId" TEXT NOT NULL,
  "position" INTEGER NOT NULL,
  "revisionId" TEXT NOT NULL,
  "answerCode" TEXT,
  "awardedPoints" INTEGER,
  PRIMARY KEY ("examId", "position"),
  FOREIGN KEY ("examId") REFERENCES "ExamAttempt" ("id") ON DELETE NO ACTION,
  FOREIGN KEY ("revisionId") REFERENCES "QuestionRevision" ("id") ON DELETE NO ACTION,
  FOREIGN KEY ("revisionId", "answerCode") REFERENCES "Answer" ("revisionId", "code") ON DELETE NO ACTION
);
CREATE UNIQUE INDEX "index_ExamAnswer_examId_revisionId" ON "ExamAnswer" ("examId", "revisionId");
CREATE INDEX "index_ExamAnswer_examId" ON "ExamAnswer" ("examId");
CREATE INDEX "index_ExamAnswer_revisionId" ON "ExamAnswer" ("revisionId");
CREATE INDEX "index_ExamAnswer_revisionId_answerCode" ON "ExamAnswer" ("revisionId", "answerCode");
CREATE TABLE "LearningProgress" (
  "lessonId" TEXT NOT NULL,
  "blockPosition" INTEGER NOT NULL,
  "completedAt" INTEGER,
  "updatedAt" INTEGER NOT NULL,
  PRIMARY KEY ("lessonId"),
  FOREIGN KEY ("lessonId") REFERENCES "Lesson" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_LearningProgress_lessonId" ON "LearningProgress" ("lessonId");
CREATE TABLE "QuestionReview" (
  "questionId" TEXT NOT NULL,
  "lastRevisionId" TEXT NOT NULL,
  "correctStreak" INTEGER NOT NULL,
  "lastCorrectAt" INTEGER,
  "nextReviewAt" INTEGER NOT NULL,
  "masteredAt" INTEGER,
  PRIMARY KEY ("questionId"),
  FOREIGN KEY ("questionId") REFERENCES "Question" ("id") ON DELETE NO ACTION,
  FOREIGN KEY ("lastRevisionId") REFERENCES "QuestionRevision" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_QuestionReview_questionId" ON "QuestionReview" ("questionId");
CREATE INDEX "index_QuestionReview_lastRevisionId" ON "QuestionReview" ("lastRevisionId");
CREATE TABLE "WordReview" (
  "id" TEXT NOT NULL,
  "wordId" TEXT NOT NULL,
  "createdAt" INTEGER NOT NULL,
  "correct" INTEGER NOT NULL,
  PRIMARY KEY ("id"),
  FOREIGN KEY ("wordId") REFERENCES "DictionaryWord" ("id") ON DELETE NO ACTION
);
CREATE INDEX "index_WordReview_wordId" ON "WordReview" ("wordId");
