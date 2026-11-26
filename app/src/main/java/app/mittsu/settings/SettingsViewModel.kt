package app.mittsu.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mittsu.AppContainer
import app.mittsu.domain.model.Difficulty
import app.mittsu.domain.model.ReviewManagementMode
import app.mittsu.domain.model.ThemePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(value: ThemePreference) = launchSet { repository.setTheme(value) }
    fun setDifficulty(value: Difficulty) = launchSet { repository.setDifficulty(value) }
    fun setReviewMode(value: ReviewManagementMode) = launchSet { repository.setReviewMode(value) }
    fun setDailyTarget(value: Int) = launchSet { repository.setDailyTarget(value) }

    private fun launchSet(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository) }
        }
    }
}
