package app.mittsu

import android.content.Context
import app.mittsu.data.EncryptedTokenStore
import app.mittsu.data.TokenStore
import app.mittsu.database.MittsuDatabase
import app.mittsu.network.WaniKaniClient
import app.mittsu.repository.AccountDashboardRepository
import app.mittsu.repository.AccountRepository
import app.mittsu.repository.DashboardRepository
import app.mittsu.repository.SampleDashboardRepository
import app.mittsu.repository.SubjectRepository
import app.mittsu.repository.WaniKaniAccountRepository
import app.mittsu.lessons.LessonRepository
import app.mittsu.lessons.LessonStarter
import app.mittsu.reviews.ReviewRepository
import app.mittsu.reviews.ReviewSubmitter
import app.mittsu.reviews.WorkManagerUploadScheduler
import app.mittsu.settings.DataStoreSettingsRepository
import app.mittsu.settings.SettingsRepository

/**
 * Manual dependency container. Keeps wiring explicit and test-friendly without an
 * annotation processor.
 */
interface AppContainer {
    val settingsRepository: SettingsRepository
    val accountRepository: AccountRepository
    val dashboardRepository: DashboardRepository
    val subjectRepository: SubjectRepository
    /** The only path to POST /reviews. Practice code is never given this. */
    val reviewSubmitter: ReviewSubmitter
    val reviewRepository: ReviewRepository
    /** The only path to PUT /assignments/{id}/start. */
    val lessonStarter: LessonStarter
    val lessonRepository: LessonRepository
    /** The custom practice choices, kept while the app is open. */
    val practiceFilters: kotlinx.coroutines.flow.MutableStateFlow<app.mittsu.quiz.PracticeFilters>
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val tokenStore: TokenStore = EncryptedTokenStore.create(context)
    private val database: MittsuDatabase = MittsuDatabase.create(context)
    private val client: WaniKaniClient = WaniKaniClient.create(token = tokenStore::get)

    override val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
    override val accountRepository: AccountRepository = WaniKaniAccountRepository(tokenStore, client, database)
    override val dashboardRepository: DashboardRepository =
        AccountDashboardRepository(accountRepository, database, SampleDashboardRepository())
    override val subjectRepository: SubjectRepository = SubjectRepository(database)
    override val reviewSubmitter: ReviewSubmitter = ReviewSubmitter(client, database)
    private val uploadScheduler = WorkManagerUploadScheduler(context.applicationContext)
    override val reviewRepository: ReviewRepository = ReviewRepository(database, reviewSubmitter, uploadScheduler)
    override val lessonStarter: LessonStarter = LessonStarter(client, database)
    override val lessonRepository: LessonRepository = LessonRepository(database, lessonStarter, uploadScheduler)
    override val practiceFilters = kotlinx.coroutines.flow.MutableStateFlow(app.mittsu.quiz.PracticeFilters())
}
