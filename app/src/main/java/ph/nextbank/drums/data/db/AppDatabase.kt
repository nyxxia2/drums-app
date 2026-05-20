package ph.nextbank.drums.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ph.nextbank.drums.data.samples.SAMPLE_SONGS

@Database(entities = [SongEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        fun build(ctx: Context, scope: CoroutineScope): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "drums.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed sample songs on first launch — the actual seed is done from
                        // the Hilt provider (AppModule) because the callback can't access
                        // the Hilt-provided DAO directly. This callback is intentionally a no-op.
                        scope.launch(Dispatchers.IO) {
                            // No-op: seeding happens in AppModule.provideSongRepository
                        }
                    }
                })
                .build()
    }
}
