package app.mittsu.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user WHERE `key` = 0") fun observe(): Flow<UserEntity?>
    @Query("SELECT * FROM user WHERE `key` = 0") suspend fun get(): UserEntity?
    @Upsert suspend fun upsert(user: UserEntity)
}

@Dao
interface SubjectDao {
    @Upsert suspend fun upsertAll(subjects: List<SubjectEntity>)
    @Query("SELECT COUNT(*) FROM subjects") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM subjects") fun observeCount(): Flow<Int>
    @Query("SELECT * FROM subjects WHERE id = :id") suspend fun get(id: Long): SubjectEntity?
    @Query("SELECT * FROM subjects WHERE id IN (:ids)") suspend fun getAll(ids: List<Long>): List<SubjectEntity>
    @Query("SELECT * FROM subjects WHERE level BETWEEN :minLevel AND :maxLevel AND hiddenAt IS NULL")
    suspend fun byLevels(minLevel: Int, maxLevel: Int): List<SubjectEntity>
}

@Dao
interface AssignmentDao {
    @Upsert suspend fun upsertAll(assignments: List<AssignmentEntity>)
    @Query("SELECT * FROM assignments WHERE hidden = 0") fun observeVisible(): Flow<List<AssignmentEntity>>
    @Query("SELECT * FROM assignments WHERE hidden = 0") suspend fun visible(): List<AssignmentEntity>
    @Query("SELECT COUNT(*) FROM assignments") suspend fun count(): Int
}

@Dao
interface ReviewStatisticDao {
    @Upsert suspend fun upsertAll(stats: List<ReviewStatisticEntity>)
    @Query("SELECT * FROM review_statistics WHERE hidden = 0") fun observeVisible(): Flow<List<ReviewStatisticEntity>>
}

@Dao
interface SrsSystemDao {
    @Upsert suspend fun upsertAll(systems: List<SrsSystemEntity>)
    @Query("SELECT * FROM srs_systems") suspend fun all(): List<SrsSystemEntity>
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE collection = :collection") suspend fun get(collection: String): SyncStateEntity?
    @Query("SELECT MIN(lastSyncedAt) FROM sync_state") fun observeOldestSync(): Flow<Long?>
    @Upsert suspend fun upsert(state: SyncStateEntity)
}
