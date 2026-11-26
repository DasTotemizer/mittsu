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
import app.mittsu.repository.WaniKaniAccountRepository
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
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val tokenStore: TokenStore = EncryptedTokenStore.create(context)
    private val database: MittsuDatabase = MittsuDatabase.create(context)
    private val client: WaniKaniClient = WaniKaniClient.create(token = tokenStore::get)

    override val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
    override val accountRepository: AccountRepository = WaniKaniAccountRepository(tokenStore, client, database)
    override val dashboardRepository: DashboardRepository =
        AccountDashboardRepository(accountRepository, database, SampleDashboardRepository())
}
