package app.mittsu.database

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter fun meaningsTo(v: List<StoredMeaning>): String = json.encodeToString(v)
    @TypeConverter fun meaningsFrom(v: String): List<StoredMeaning> = json.decodeFromString(v)
    @TypeConverter fun auxTo(v: List<StoredAuxMeaning>): String = json.encodeToString(v)
    @TypeConverter fun auxFrom(v: String): List<StoredAuxMeaning> = json.decodeFromString(v)
    @TypeConverter fun readingsTo(v: List<StoredReading>): String = json.encodeToString(v)
    @TypeConverter fun readingsFrom(v: String): List<StoredReading> = json.decodeFromString(v)
    @TypeConverter fun sentencesTo(v: List<StoredSentence>): String = json.encodeToString(v)
    @TypeConverter fun sentencesFrom(v: String): List<StoredSentence> = json.decodeFromString(v)
    @TypeConverter fun imagesTo(v: List<StoredImage>): String = json.encodeToString(v)
    @TypeConverter fun imagesFrom(v: String): List<StoredImage> = json.decodeFromString(v)
    @TypeConverter fun stringsTo(v: List<String>): String = json.encodeToString(v)
    @TypeConverter fun stringsFrom(v: String): List<String> = json.decodeFromString(v)
    @TypeConverter fun longsTo(v: List<Long>): String = json.encodeToString(v)
    @TypeConverter fun longsFrom(v: String): List<Long> = json.decodeFromString(v)
}
