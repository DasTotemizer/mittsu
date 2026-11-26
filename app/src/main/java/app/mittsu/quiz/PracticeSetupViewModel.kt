package app.mittsu.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mittsu.AppContainer
import app.mittsu.domain.model.SrsGroup
import app.mittsu.domain.model.SubjectType
import app.mittsu.repository.SubjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PracticeSetupState(
    val loaded: Boolean = false,
    val filters: PracticeFilters = PracticeFilters(),
    /** Learned subjects matching the filters right now. */
    val matching: Int = 0,
    val learned: Int = 0,
    /** Highest level among learned subjects, the top of the level slider. */
    val maxLevel: Int = 1,
)

/** Custom practice choices. Read-only: like all practice, it never talks to WaniKani. */
class PracticeSetupViewModel(
    private val subjects: SubjectRepository,
    private val filters: MutableStateFlow<PracticeFilters>,
) : ViewModel() {

    private data class Data(val pool: List<QuizSubject>, val info: Map<Long, PracticeInfo>)
    private val data = MutableStateFlow<Data?>(null)

    val state: StateFlow<PracticeSetupState> = combine(data, filters) { d, f ->
        if (d == null) PracticeSetupState(filters = f)
        else {
            val learned = d.pool.filter { it.id in d.info }
            PracticeSetupState(
                loaded = true,
                filters = f,
                matching = learned.count { f.matches(it, d.info[it.id]) },
                learned = learned.size,
                maxLevel = learned.maxOfOrNull { it.level } ?: 1,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeSetupState(filters = filters.value))

    fun onOpen() {
        viewModelScope.launch {
            val d = Data(subjects.quizPool(), subjects.practiceInfo())
            data.value = d
            // Keep the level range inside what has been learned.
            val top = d.pool.filter { it.id in d.info }.maxOfOrNull { it.level } ?: 1
            filters.value = filters.value.let { f ->
                f.copy(maxLevel = f.maxLevel.coerceAtMost(top).coerceAtLeast(1), minLevel = f.minLevel.coerceIn(1, top))
            }
        }
    }

    fun setLevels(min: Int, max: Int) { filters.value = filters.value.copy(minLevel = minOf(min, max), maxLevel = maxOf(min, max)) }

    /** Vocabulary and kana-only vocabulary are one choice for the user. */
    fun toggleType(type: SubjectType) {
        val group = if (type == SubjectType.VOCABULARY || type == SubjectType.KANA_VOCABULARY) setOf(SubjectType.VOCABULARY, SubjectType.KANA_VOCABULARY) else setOf(type)
        val f = filters.value
        val next = if (f.types.containsAll(group)) f.types - group else f.types + group
        if (next.isNotEmpty()) filters.value = f.copy(types = next)
    }

    fun toggleSrs(group: SrsGroup) {
        val f = filters.value
        val next = if (group in f.srsGroups) f.srsGroups - group else f.srsGroups + group
        if (next.isNotEmpty()) filters.value = f.copy(srsGroups = next)
    }

    fun setWeakOnly(on: Boolean) { filters.value = filters.value.copy(weakOnly = on) }
    fun setSize(size: Int) { filters.value = filters.value.copy(size = size) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PracticeSetupViewModel(container.subjectRepository, container.practiceFilters) }
        }
    }
}
