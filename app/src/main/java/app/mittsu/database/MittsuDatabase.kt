package app.mittsu.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Local cache of the connected WaniKani account. WaniKani stays the source of truth: the cache
 * tables are only ever filled from API responses. The one exception is `pending_reviews`, the
 * outbox of finished real reviews waiting to be sent. Everything is wiped on disconnect.
 */
@Database(
    entities = [
        UserEntity::class, SubjectEntity::class, AssignmentEntity::class,
        ReviewStatisticEntity::class, SrsSystemEntity::class, SyncStateEntity::class,
        PendingReviewEntity::class, ActiveSessionEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class MittsuDatabase : RoomDatabase() {
    abstract fun users(): UserDao
    abstract fun subjects(): SubjectDao
    abstract fun assignments(): AssignmentDao
    abstract fun reviewStatistics(): ReviewStatisticDao
    abstract fun srsSystems(): SrsSystemDao
    abstract fun syncState(): SyncStateDao
    abstract fun pendingReviews(): PendingReviewDao
    abstract fun activeSession(): ActiveSessionDao

    companion object {
        fun create(context: Context): MittsuDatabase =
            Room.databaseBuilder(context.applicationContext, MittsuDatabase::class.java, "mittsu.db").build()
    }
}
