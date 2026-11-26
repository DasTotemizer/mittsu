package app.mittsu.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mittsu.domain.model.Difficulty
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val reviewMode: ReviewManagementMode = ReviewManagementMode.DAILY_BALANCE,
    val dailyTarget: Int = DEFAULT_DAILY_TARGET,
) {
    companion object {
        const val DEFAULT_DAILY_TARGET = 50
        val TARGET_RANGE = 10..150
    }
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTheme(theme: ThemePreference)
    suspend fun setDifficulty(difficulty: Difficulty)
    suspend fun setReviewMode(mode: ReviewManagementMode)
    suspend fun setDailyTarget(target: Int)
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "mittsu_settings")

/** Non-secret preferences. The API token is NOT stored here; see the secure store (Phase 2). */
class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val difficulty = stringPreferencesKey("difficulty")
        val reviewMode = stringPreferencesKey("review_mode")
        val dailyTarget = intPreferencesKey("daily_target")
    }

    override val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            theme = p[Keys.theme].toEnum(ThemePreference.SYSTEM),
            difficulty = p[Keys.difficulty].toEnum(Difficulty.NORMAL),
            reviewMode = p[Keys.reviewMode].toEnum(ReviewManagementMode.DAILY_BALANCE),
            dailyTarget = (p[Keys.dailyTarget] ?: AppSettings.DEFAULT_DAILY_TARGET)
                .coerceIn(AppSettings.TARGET_RANGE),
        )
    }

    override suspend fun setTheme(theme: ThemePreference) { store.edit { it[Keys.theme] = theme.name } }
    override suspend fun setDifficulty(difficulty: Difficulty) { store.edit { it[Keys.difficulty] = difficulty.name } }
    override suspend fun setReviewMode(mode: ReviewManagementMode) { store.edit { it[Keys.reviewMode] = mode.name } }
    override suspend fun setDailyTarget(target: Int) {
        store.edit { it[Keys.dailyTarget] = target.coerceIn(AppSettings.TARGET_RANGE) }
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
