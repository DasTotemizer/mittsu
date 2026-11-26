package app.mittsu.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Phones with the Phase 2/3 build have a version 1 database; upgrading must keep their data. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    @Test fun version1DatabaseUpgradesAndKeepsData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }

        // Build exactly the version 1 schema Room exported for Phase 2.
        val schema = Json.parseToJsonElement(File("schemas/app.mittsu.database.MittsuDatabase/1.json").readText())
            .jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                old.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { idx ->
                    old.execSQL(idx.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { old.execSQL(it.jsonPrimitive.content) }
            old.execSQL("INSERT INTO user (`key`, username, level, maxLevelGranted, subscriptionType, subscriptionActive, fetchedAt) VALUES (0, 'testlearner', 14, 60, 'lifetime', 1, 0)")
            old.execSQL("INSERT INTO assignments (id, subjectId, subjectType, srsStage, hidden) VALUES (101, 1, 'radical', 4, 0)")
            old.version = 1
        }

        val db = Room.databaseBuilder(context, MittsuDatabase::class.java, file.absolutePath).allowMainThreadQueries().build()
        try {
            assertEquals("testlearner", db.users().get()!!.username)
            assertEquals(1, db.assignments().count())
            val id = db.pendingReviews().insert(PendingReviewEntity(assignmentId = 101, subjectId = 1, availableAt = 5, incorrectMeaning = 0, incorrectReading = 0, completedAt = 6))
            assertTrue(id > 0)
            assertEquals(null, db.activeSession().get())
        } finally {
            db.close()
            file.delete()
        }
    }
}
