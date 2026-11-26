package app.mittsu.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Local cache of the connected WaniKani account. WaniKani stays the source of truth: this
 * database is only ever filled from API responses, and is wiped on disconnect.
 */
@Database(
    entities = [
        UserEntity::class, SubjectEntity::class, AssignmentEntity::class,
        ReviewStatisticEntity::class, SrsSystemEntity::class, SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MittsuDatabase : RoomDatabase() {
    abstract fun users(): UserDao
    abstract fun subjects(): SubjectDao
    abstract fun assignments(): AssignmentDao
    abstract fun reviewStatistics(): ReviewStatisticDao
    abstract fun srsSystems(): SrsSystemDao
    abstract fun syncState(): SyncStateDao

    companion object {
        fun create(context: Context): MittsuDatabase =
            Room.databaseBuilder(context.applicationContext, MittsuDatabase::class.java, "mittsu.db").build()
    }
}
