package app.mittsu.reviews

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mittsu.MittsuApplication
import java.util.concurrent.TimeUnit

/** Asks for waiting reviews and lessons to be sent once a connection is available, even if the app is closed. */
fun interface UploadScheduler {
    fun schedule()
}

class WorkManagerUploadScheduler(private val context: Context) : UploadScheduler {
    override fun schedule() {
        try {
            val request = OneTimeWorkRequestBuilder<ReviewUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        } catch (e: IllegalStateException) {
            // WorkManager not initialised (e.g. in some test environments): the screen still retries.
            Log.w("MittsuReviews", "Could not schedule review upload")
        }
    }

    private companion object { const val WORK_NAME = "mittsu-review-upload" }
}

class ReviewUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MittsuApplication ?: return Result.failure()
        val reviews = app.container.reviewSubmitter.submitPending()
        val lessons = app.container.lessonStarter.submitPending()
        return when {
            reviews.stillPending == 0 && lessons.stillPending == 0 -> Result.success()
            // A token problem won't fix itself: the app shows it and the user reconnects.
            reviews.needsReconnect || lessons.needsReconnect -> Result.failure()
            runAttemptCount >= 8 -> Result.failure()
            else -> Result.retry()
        }
    }
}
