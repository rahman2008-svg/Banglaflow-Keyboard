package com.example.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "clipboard_items")
data class ClipboardItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Entity(tableName = "custom_themes")
data class CustomTheme(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val backgroundColor: Long, // Use Long to store ARGB values nicely in Room
    val keyColor: Long,
    val textColor: Long,
    val cornerRadius: Int = 8,
    val isDark: Boolean = false,
    val isSystem: Boolean = false
)

@Entity(tableName = "user_words")
data class UserWord(
    @PrimaryKey val word: String,
    val frequency: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)

@Dao
interface KeyboardDao {
    // Clipboard
    @Query("SELECT * FROM clipboard_items ORDER BY isPinned DESC, timestamp DESC")
    fun getAllClipboardFlow(): Flow<List<ClipboardItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClipboard(item: ClipboardItem)

    @Delete
    suspend fun deleteClipboard(item: ClipboardItem)

    @Query("DELETE FROM clipboard_items")
    suspend fun clearAllClipboard()

    @Query("DELETE FROM clipboard_items WHERE isPinned = 0")
    suspend fun clearUnpinnedClipboard()

    // Themes
    @Query("SELECT * FROM custom_themes")
    fun getAllThemesFlow(): Flow<List<CustomTheme>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTheme(theme: CustomTheme): Long

    @Delete
    suspend fun deleteTheme(theme: CustomTheme)

    @Query("SELECT * FROM custom_themes WHERE id = :id")
    suspend fun getThemeById(id: Int): CustomTheme?

    // User Words
    @Query("SELECT * FROM user_words WHERE word LIKE :query || '%' ORDER BY frequency DESC, lastUsed DESC LIMIT 20")
    suspend fun getWordsStartingWith(query: String): List<UserWord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserWord(word: UserWord)

    @Query("SELECT * FROM user_words WHERE word = :word")
    suspend fun getUserWord(word: String): UserWord?
}

@Database(entities = [ClipboardItem::class, CustomTheme::class, UserWord::class], version = 1, exportSchema = false)
abstract class KeyboardDatabase : RoomDatabase() {
    abstract fun dao(): KeyboardDao

    companion object {
        @Volatile
        private var INSTANCE: KeyboardDatabase? = null

        fun getInstance(context: Context): KeyboardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KeyboardDatabase::class.java,
                    "banglaflow_keyboard_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
