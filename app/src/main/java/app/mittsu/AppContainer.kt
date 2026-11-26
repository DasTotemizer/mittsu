package app.mittsu

import android.content.Context
import app.mittsu.repository.DashboardRepository
import app.mittsu.repository.SampleDashboardRepository
import app.mittsu.settings.DataStoreSettingsRepository
import app.mittsu.settings.SettingsRepository

/**
 * Manual dependency container. Keeps wiring explicit and test-friendly without an
 * annotation processor. Phase 2 adds the WaniKani client, Room database and token store here.
 */
interface AppContainer {
    val settingsRepository: SettingsRepository
    val dashboardRepository: DashboardRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
    override val dashboardRepository: DashboardRepository = SampleDashboardRepository()
}
