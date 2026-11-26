package app.mittsu

import android.app.Application

class MittsuApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        // Send any real reviews and lessons finished while offline or before the app was last closed.
        if (container.accountRepository.isConnected) {
            container.reviewRepository.sendSoon()
            container.lessonRepository.sendSoon()
        }
    }
}
