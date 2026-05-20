package ph.nextbank.drums.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [SongEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        /** v1 → v2: added youtubeVideoId column for YouTube-synced playback. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN youtubeVideoId TEXT")
            }
        }

        fun build(ctx: Context, scope: CoroutineScope): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "drums.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seeding happens in AppModule.provideSongRepository because the
                        // callback can't access the Hilt-provided DAO directly.
                        scope.launch(Dispatchers.IO) { /* no-op */ }
                    }
                })
                .build()
    }
}
