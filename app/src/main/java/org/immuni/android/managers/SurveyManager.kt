package org.immuni.android.managers

import com.bendingspoons.base.storage.KVStorage
import com.bendingspoons.pico.Pico
import org.immuni.android.db.ImmuniDatabase
import org.immuni.android.db.entity.HealthProfileEntity
import org.immuni.android.models.HealthProfile
import org.immuni.android.models.User
import org.immuni.android.models.survey.CompositeAnswer
import org.immuni.android.models.survey.QuestionId
import org.immuni.android.models.survey.SimpleAnswer
import org.immuni.android.ui.log.model.FormModel
import org.immuni.android.util.toJson
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class SurveyManager(db: ImmuniDatabase) : KoinComponent {
    companion object {
        private const val additionalDelayKey = "additionalDelay"

        private fun answeredQuestionsKey(userId: String) = "answeredQuestions-$userId"

        fun getOrSetAdditionalDelay(storage: KVStorage): Int {
            val storedValue = storage.load<Int>(additionalDelayKey)
            if (storedValue != null) {
                return storedValue
            }

            // a delay between 15 minutes and 2 hours in seconds
            val newValue = Random(Date().time).nextInt(15 * 60, 2 * 60 * 60)
            storage.save(additionalDelayKey, newValue)
            return newValue
        }
    }

    private val additionalDelay: Int
    private val storage: KVStorage by inject()
    private val userManager: UserManager by inject()
    private val pico: Pico by inject()
    private val healthProfileDao = db.healthProfileDao()

    init {
        additionalDelay = getOrSetAdditionalDelay(storage)
    }

    private suspend fun saveHealthProfile(healthProfile: HealthProfile) {
        healthProfileDao.insert(
            HealthProfileEntity(
                userId = healthProfile.userId,
                surveyTimeMillis = healthProfile.surveyTimeMillis,
                healthProfileJson = toJson(healthProfile)
            )
        )
    }

    suspend fun lastHealthProfile(userId: String): HealthProfile? {
        return healthProfileDao.lastHealthProfileForUser(userId)?.healthProfile
    }

    suspend fun allHealthProfiles(userId: String): List<HealthProfile> {
        return healthProfileDao.allHealthProfilesForUser(userId).map { it.healthProfile }
    }

    private fun loadLastAnsweredQuestions(userId: String): Map<QuestionId, Long> {
        return storage.load(answeredQuestionsKey(userId), defValue = mapOf())
    }

    private fun saveAnseredQuestions(userId: String, questions: Set<QuestionId>) {
        val lastAnsweredQuestions = loadLastAnsweredQuestions(userId).toMutableMap()
        val date = todayAtMidnight()
        lastAnsweredQuestions.putAll(questions.map { it to date.time })
        storage.save(answeredQuestionsKey(userId), lastAnsweredQuestions)
    }

    private fun deleteLastAnsweredQuestions(userId: String) {
        storage.delete(answeredQuestionsKey(userId))
    }

    fun answeredQuestionsElapsedDays(userId: String): Map<QuestionId, Int> {
        val lastAnsweredQuestions = loadLastAnsweredQuestions(userId)
        val date = todayAtMidnight()
        return lastAnsweredQuestions.mapValues {
            val days = TimeUnit.MILLISECONDS.toDays(date.time - it.value).toInt()
            days
        }
    }

    suspend fun completeSurvey(userId: String, form: FormModel, surveyVersion: String): HealthProfile {
        val updatedHealthProfile = HealthProfile(
            userId = userId,
            healthState = form.healthState,
            triageProfileId = form.triageProfile,
            surveyVersion = surveyVersion,
            surveyTimeMillis = form.startDate.time,
            surveyAnswers = form.surveyAnswers.mapValues {
                it.value.map { answer ->
                    when (answer) {
                        is SimpleAnswer -> answer.index
                        is CompositeAnswer -> answer.componentIndexes
                    }
                }
            }
        )
        saveHealthProfile(updatedHealthProfile)
        saveAnseredQuestions(userId, form.answeredQuestions.toSet())

        return updatedHealthProfile
    }

    suspend fun isSurveyCompletedForUser(userId: String): Boolean {
        val lastHealthProfile = lastHealthProfile(userId)
        val lastSurveyDate = lastHealthProfile?.surveyDate ?: return false

        return lastSurveyDate > lastAvailableSurveyDate()
    }

    private fun todayAtMidnight(): Date {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun todaySurveyCalendar(): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            add(Calendar.SECOND, additionalDelay)
        }
    }

    fun nextSurveyDate(): Date {
        val gc = todaySurveyCalendar()
        val todaySurveyDate = gc.time
        if (todaySurveyDate > Date()) {
            return todaySurveyDate
        }
        gc.add(Calendar.DATE, 1)
        return gc.time
    }

    fun lastAvailableSurveyDate(): Date {
        val gc = todaySurveyCalendar()
        val todaySurveyDate = gc.time
        if (todaySurveyDate < Date()) {
            return todaySurveyDate
        }
        gc.add(Calendar.DATE, -1)
        return gc.time
    }

    suspend fun nextUserToLog(): User? {
        return userManager.users().find {
            !isSurveyCompletedForUser(it.id)
        }
    }

    suspend fun usersToLogCount(): Int {
        return userManager.users().count {
            !isSurveyCompletedForUser(it.id)
        }
    }

    suspend fun areAllSurveysLogged(): Boolean {
        return nextUserToLog() == null
    }

    suspend fun deleteUserData(userId: String) {
        userManager.deleteUser(userId)
        healthProfileDao.deleteAllForUser(userId)
    }

    suspend fun deleteDataOlderThan(days: Int) {
        val thresholdDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        healthProfileDao.deleteAllOlderThan(thresholdDate.time)
        userManager.users().map { user ->
            deleteLastAnsweredQuestions(user.id)
        }
    }
}