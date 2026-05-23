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
import ph.nextbank.drums.audio.songsterr.DefaultDrumTabParser
import ph.nextbank.drums.audio.songsterr.DrumTabParser
import ph.nextbank.drums.audio.songsterr.OkHttpSongsterrSearchService
import ph.nextbank.drums.audio.songsterr.OkHttpSongsterrTabFetcher
import ph.nextbank.drums.audio.songsterr.SongsterrSearchService
import ph.nextbank.drums.audio.songsterr.SongsterrTabFetcher
import ph.nextbank.drums.audio.youtube.NewPipeYouTubeSearchService
import ph.nextbank.drums.audio.youtube.YouTubeSearchService
import ph.nextbank.drums.data.db.AppDatabase
import ph.nextbank.drums.data.db.SongDao
import ph.nextbank.drums.data.repo.SongRepository
import ph.nextbank.drums.data.repo.RoomSongRepository
import ph.nextbank.drums.audio.DrumSampleBank
import ph.nextbank.drums.audio.DrumSampleBankApi
import ph.nextbank.drums.audio.playback.YouTubeAdapter
import ph.nextbank.drums.audio.playback.YouTubeAdapterFactory
import ph.nextbank.drums.ui.player.ExoYouTubeAdapter
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
        val repo: SongRepository = RoomSongRepository(dao)
        // Seed bundled songs on first launch only — preserves user-set youtubeVideoId / offset / blocklist on subsequent starts.
        scope.launch { repo.seedNew(SAMPLE_SONGS) }
        return repo
    }

    @Provides @Singleton
    fun provideDrumSampleBank(@ApplicationContext ctx: Context): DrumSampleBankApi = DrumSampleBank(ctx)

    @Provides @Singleton
    fun provideYouTubeSearchService(): YouTubeSearchService = NewPipeYouTubeSearchService()

    @Provides @Singleton
    fun provideSongsterrSearchService(): SongsterrSearchService = OkHttpSongsterrSearchService()

    @Provides @Singleton
    fun provideSongsterrTabFetcher(): SongsterrTabFetcher = OkHttpSongsterrTabFetcher()

    @Provides @Singleton
    fun provideDrumTabParser(): DrumTabParser = DefaultDrumTabParser()

    @Provides @Singleton
    fun provideYouTubeAdapterFactory(@ApplicationContext ctx: Context): YouTubeAdapterFactory =
        object : YouTubeAdapterFactory {
            override fun create(streamUrl: String): YouTubeAdapter = ExoYouTubeAdapter(ctx, streamUrl)
        }
}
