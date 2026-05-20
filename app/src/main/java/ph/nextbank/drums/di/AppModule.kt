package ph.nextbank.drums.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ph.nextbank.drums.data.db.AppDatabase
import ph.nextbank.drums.data.db.SongDao
import ph.nextbank.drums.data.repo.SongRepository
import ph.nextbank.drums.audio.DrumSampleBank
import ph.nextbank.drums.data.samples.SAMPLE_SONGS
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context, scope: CoroutineScope): AppDatabase =
        AppDatabase.build(ctx, scope)

    @Provides
    fun provideSongDao(db: AppDatabase): SongDao = db.songDao()

    @Provides @Singleton
    fun provideSongRepository(dao: SongDao, scope: CoroutineScope): SongRepository {
        val repo = SongRepository(dao)
        // Idempotent seed: insertAll uses REPLACE on conflict, so re-seeding bundled
        // songs is safe — user changes to bundled songs (e.g. BPM) are overwritten on
        // app cold start. For Phase 1 that's acceptable; v1.1 can compare and skip.
        scope.launch { repo.upsertAll(SAMPLE_SONGS) }
        return repo
    }

    @Provides @Singleton
    fun provideDrumSampleBank(@ApplicationContext ctx: Context): DrumSampleBank = DrumSampleBank(ctx)
}
