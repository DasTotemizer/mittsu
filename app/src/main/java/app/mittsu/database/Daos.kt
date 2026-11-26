package app.mittsu.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
    @Query("SELECT * FROM assignments WHERE id = :id") suspend fun get(id: Long): AssignmentEntity?
}

@Dao
interface ReviewStatisticDao {
    @Upsert suspend fun upsertAll(stats: List<ReviewStatisticEntity>)
    @Query("SELECT * FROM review_statistics WHERE hidden = 0") fun observeVisible(): Flow<List<ReviewStatisticEntity>>
    @Query("SELECT * FROM review_statistics WHERE hidden = 0") suspend fun visible(): List<ReviewStatisticEntity>
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

@Dao
interface PendingReviewDao {
    /** Returns -1 when this review is already recorded, so it is never stored or sent twice. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(review: PendingReviewEntity): Long
    @Query("SELECT * FROM pending_reviews WHERE status = 'PENDING' ORDER BY completedAt, id") suspend fun pending(): List<PendingReviewEntity>
    @Query("SELECT * FROM pending_reviews WHERE id = :id") suspend fun get(id: Long): PendingReviewEntity?
    @Query("SELECT * FROM pending_reviews WHERE completedAt >= :since ORDER BY completedAt") fun observeSince(since: Long): Flow<List<PendingReviewEntity>>
    @Query("SELECT * FROM pending_reviews WHERE status = 'PENDING' OR (status = 'REJECTED' AND visible = 1) ORDER BY completedAt")
    fun observeOutstanding(): Flow<List<PendingReviewEntity>>
    @Query("SELECT * FROM pending_reviews") suspend fun all(): List<PendingReviewEntity>
    @Query("UPDATE pending_reviews SET status = 'SUBMITTED', submittedAt = :at, message = NULL, attempts = attempts + 1 WHERE id = :id")
    suspend fun markSubmitted(id: Long, at: Long)
    @Query("UPDATE pending_reviews SET status = 'REJECTED', message = :message, attempts = attempts + 1 WHERE id = :id")
    suspend fun markRejected(id: Long, message: String)
    @Query("UPDATE pending_reviews SET attempts = attempts + 1, message = :message WHERE id = :id")
    suspend fun markAttemptFailed(id: Long, message: String)
    @Query("UPDATE pending_reviews SET visible = 0 WHERE status = 'REJECTED'") suspend fun dismissRejected()
    /** Keeps the table small: finished rows older than [before] are no longer needed. */
    @Query("DELETE FROM pending_reviews WHERE status != 'PENDING' AND completedAt < :before") suspend fun pruneBefore(before: Long)
}

@Dao
interface ActiveSessionDao {
    @Query("SELECT * FROM active_session WHERE `key` = 0") suspend fun get(): ActiveSessionEntity?
    @Query("SELECT * FROM active_session WHERE `key` = 0") fun observe(): Flow<ActiveSessionEntity?>
    @Upsert suspend fun save(session: ActiveSessionEntity)
    @Query("DELETE FROM active_session") suspend fun clear()
}

@Dao
interface PendingLessonDao {
    /** Returns -1 when this lesson is already recorded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(lesson: PendingLessonEntity): Long
    @Query("SELECT * FROM pending_lesson_starts WHERE status = 'PENDING' ORDER BY completedAt") suspend fun pending(): List<PendingLessonEntity>
    @Query("SELECT * FROM pending_lesson_starts WHERE assignmentId = :id") suspend fun get(id: Long): PendingLessonEntity?
    @Query("SELECT * FROM pending_lesson_starts") suspend fun all(): List<PendingLessonEntity>
    @Query("SELECT * FROM pending_lesson_starts WHERE status = 'PENDING' OR (status = 'REJECTED' AND visible = 1) ORDER BY completedAt")
    fun observeOutstanding(): Flow<List<PendingLessonEntity>>
    @Query("SELECT * FROM pending_lesson_starts WHERE completedAt >= :since") fun observeSince(since: Long): Flow<List<PendingLessonEntity>>
    @Query("UPDATE pending_lesson_starts SET status = 'SUBMITTED', submittedAt = :at, message = NULL, attempts = attempts + 1 WHERE assignmentId = :id")
    suspend fun markSubmitted(id: Long, at: Long)
    @Query("UPDATE pending_lesson_starts SET status = 'REJECTED', message = :message, attempts = attempts + 1 WHERE assignmentId = :id")
    suspend fun markRejected(id: Long, message: String)
    @Query("UPDATE pending_lesson_starts SET attempts = attempts + 1, message = :message WHERE assignmentId = :id")
    suspend fun markAttemptFailed(id: Long, message: String)
    @Query("UPDATE pending_lesson_starts SET visible = 0 WHERE status = 'REJECTED'") suspend fun dismissRejected()
}
