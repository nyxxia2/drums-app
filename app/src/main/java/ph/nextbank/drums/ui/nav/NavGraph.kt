package ph.nextbank.drums.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ph.nextbank.drums.ui.library.LibraryScreen
import ph.nextbank.drums.ui.player.PlayerScreen
import ph.nextbank.drums.ui.practice.PracticeScreen
import ph.nextbank.drums.ui.song_detail.SongDetailScreen
import ph.nextbank.drums.ui.upload.UploadScreen

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Route.Library.path) {
        composable(Route.Library.path) {
            LibraryScreen(
                onSongClick = { id -> nav.navigate(Route.Player(id).path) },
                onSongLongPress = { id -> nav.navigate(Route.SongDetail(id).path) },
                onAddClick = { nav.navigate(Route.Upload.path) },
            )
        }
        composable(Route.Upload.path) {
            UploadScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Route.Player.PATH,
            arguments = listOf(navArgument("songId") { type = NavType.StringType }),
        ) { entry ->
            PlayerScreen(
                songId = entry.arguments!!.getString("songId")!!,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Route.SongDetail.PATH,
            arguments = listOf(navArgument("songId") { type = NavType.StringType }),
        ) { entry ->
            SongDetailScreen(
                songId = entry.arguments!!.getString("songId")!!,
                onBack = { nav.popBackStack() },
                onStartReading = { id -> nav.navigate(Route.Player(id).path) },
                onStartPractice = { id -> nav.navigate(Route.Practice(id).path) },
            )
        }
        composable(
            Route.Practice.PATH,
            arguments = listOf(navArgument("songId") { type = NavType.StringType }),
        ) { entry ->
            PracticeScreen(
                songId = entry.arguments!!.getString("songId")!!,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
