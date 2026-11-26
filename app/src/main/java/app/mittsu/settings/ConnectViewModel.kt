package app.mittsu.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mittsu.AppContainer
import app.mittsu.domain.model.SyncStatus
import app.mittsu.domain.model.WaniKaniUser
import app.mittsu.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val token: String = "",
    val busy: Boolean = false,
    val step: String? = null,
    val error: String? = null,
    val connectedUser: WaniKaniUser? = null,
    val finished: Boolean = false,
)

class ConnectViewModel(private val account: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            account.syncStatus.collect { s ->
                if (s is SyncStatus.Running) _state.update { it.copy(step = s.step) }
            }
        }
    }

    fun onTokenChange(value: String) = _state.update { it.copy(token = value, error = null) }

    fun connect() {
        if (_state.value.busy) return
        val token = _state.value.token
        _state.update { it.copy(busy = true, error = null, step = "Checking your token") }
        viewModelScope.launch {
            account.connect(token).fold(
                onSuccess = { user ->
                    // The token is no longer needed in UI state once it is stored securely.
                    _state.update { it.copy(token = "", connectedUser = user, step = "Downloading your WaniKani data") }
                    val sync = account.sync(force = true)
                    _state.update {
                        it.copy(
                            busy = false,
                            finished = sync.isSuccess,
                            error = sync.exceptionOrNull()?.let { e ->
                                "Connected, but the first download failed: ${e.message} You can retry from the dashboard."
                            },
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(busy = false, step = null, error = e.message) } },
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConnectViewModel(container.accountRepository) }
        }
    }
}
