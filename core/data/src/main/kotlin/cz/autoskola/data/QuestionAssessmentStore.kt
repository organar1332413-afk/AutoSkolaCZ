package cz.autoskola.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import cz.autoskola.domain.QuestionAssessment
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.questionAssessmentStore by preferencesDataStore("question_assessments")

class QuestionAssessmentStore(context: Context) {
    private val store = context.applicationContext.questionAssessmentStore
    private val known = stringSetPreferencesKey("known")
    private val doubtful = stringSetPreferencesKey("doubtful")
    private val unknown = stringSetPreferencesKey("unknown")

    val assessments = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            buildMap {
                p[known].orEmpty().forEach { put(it, QuestionAssessment.KNOWN) }
                p[doubtful].orEmpty().forEach { put(it, QuestionAssessment.DOUBTFUL) }
                p[unknown].orEmpty().forEach { put(it, QuestionAssessment.UNKNOWN) }
            }
        }

    suspend fun set(questionId: String, value: QuestionAssessment) {
        require(questionId.isNotBlank() && questionId.length <= 200)
        store.edit { p ->
            p[known] = p[known].orEmpty() - questionId
            p[doubtful] = p[doubtful].orEmpty() - questionId
            p[unknown] = p[unknown].orEmpty() - questionId
            val key = when(value) {
                QuestionAssessment.KNOWN -> known
                QuestionAssessment.DOUBTFUL -> doubtful
                QuestionAssessment.UNKNOWN -> unknown
            }
            p[key] = p[key].orEmpty() + questionId
        }
    }
}
