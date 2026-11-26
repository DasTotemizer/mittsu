package app.mittsu.network

import app.mittsu.BuildConfig

/** Fixed facts about the official WaniKani API v2. The client itself arrives in Phase 2. */
object WaniKaniConfig {
    const val BASE_URL: String = BuildConfig.WANIKANI_API_BASE_URL
    const val REVISION_HEADER = "Wanikani-Revision"
    const val REVISION: String = BuildConfig.WANIKANI_API_REVISION

    /** Official account settings page, where Vacation Mode is managed. Opened in a Custom Tab. */
    const val ACCOUNT_SETTINGS_URL = "https://www.wanikani.com/settings/account"

    /** Where a user creates a personal access token (read + reviews:create + assignments:start). */
    const val TOKEN_SETTINGS_URL = "https://www.wanikani.com/settings/personal_access_tokens"
}
