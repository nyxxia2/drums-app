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

@Database(entities = [SongEntity::class], version = 4, exportSchema = false)
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

        /** v2 → v3: added youtubeOffsetMs + youtubeBlocklist for sync nudge + "Try another video". */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN youtubeOffsetMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN youtubeBlocklist TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3 → v4: added songsterrId + songsterrRevisionId for Songsterr-sourced tabs. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN songsterrId INTEGER")
                db.execSQL("ALTER TABLE songs ADD COLUMN songsterrRevisionId TEXT")
            }
        }

        fun build(ctx: Context, scope: CoroutineScope): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "drums.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch(Dispatchers.IO) { /* no-op */ }
                    }
                })
                .build()
    }
}
