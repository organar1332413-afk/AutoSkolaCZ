package cz.autoskola.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve every v1 revision and answer. The legacy completeForB claim is B only. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ExamAttempt ADD COLUMN licenceGroup TEXT NOT NULL DEFAULT 'B'")
        db.execSQL("ALTER TABLE ExamAttempt ADD COLUMN questionCount INTEGER NOT NULL DEFAULT 25")
        db.execSQL("ALTER TABLE ExamAttempt ADD COLUMN maxPoints INTEGER NOT NULL DEFAULT 50")
        db.execSQL("ALTER TABLE ExamAttempt ADD COLUMN passPoints INTEGER NOT NULL DEFAULT 43")
        db.execSQL("CREATE TABLE IF NOT EXISTS ContentGroupReadiness (versionId TEXT NOT NULL, licenceGroup TEXT NOT NULL, blueprintVersion TEXT NOT NULL, eligibilityComplete INTEGER NOT NULL, contentComplete INTEGER NOT NULL, mediaComplete INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(versionId, licenceGroup), FOREIGN KEY(versionId) REFERENCES DatabaseVersion(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ContentGroupReadiness_versionId ON ContentGroupReadiness(versionId)")
        db.execSQL("INSERT INTO ContentGroupReadiness(versionId, licenceGroup, blueprintVersion, eligibilityComplete, contentComplete, mediaComplete, source) SELECT id, 'B', 'B-stage2-v1', 1, 1, 1, source FROM DatabaseVersion WHERE sample=0 AND completeForB=1")
    }
}
