package app.mittsu.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mittsu.AppContainer
import app.mittsu.domain.model.DashboardSummary
import app.mittsu.repository.DashboardRepository
import app.mittsu.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Ready(val summary: DashboardSummary) : DashboardUiState
}

class DashboardViewModel(
    private val repository: DashboardRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** The user's own target and review mode always come from settings, not the data source. */
    val state: StateFlow<DashboardUiState> = combine(
        repository.summary(),
        settingsRepository.settings,
    ) { summary, settings ->
        DashboardUiState.Ready(
            summary.copy(dailyTarget = settings.dailyTarget, reviewMode = settings.reviewMode),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(container.dashboardRepository, container.settingsRepository) }
        }
    }
}
