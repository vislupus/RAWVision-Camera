package com.rawvision.camera.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rawvision.camera.camera.CameraScreen
import com.rawvision.camera.camera.CameraViewModel
import com.rawvision.camera.data.AppContainer
import com.rawvision.camera.editor.EditorScreen
import com.rawvision.camera.editor.EditorViewModel
import com.rawvision.camera.gallery.GalleryScreen
import com.rawvision.camera.gallery.ExportsScreen
import com.rawvision.camera.gallery.GalleryViewModel
import com.rawvision.camera.gallery.PhotoDetailScreen
import com.rawvision.camera.settings.SettingsScreen

object Routes {
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val EXPORTS = "exports"
    const val PHOTO = "photo/{id}"
    const val EDITOR = "editor/{id}"
    fun photo(id: Long) = "photo/$id"
    fun editor(id: Long) = "editor/$id"
}

@Composable
fun AppNavigation(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.CAMERA) {
        composable(Routes.CAMERA) {
            val vm: CameraViewModel = viewModel(factory = CameraViewModel.Factory(container))
            CameraScreen(
                vm = vm,
                volumeKeys = container.volumeKeyPresses,
                onOpenGallery = { nav.navigate(Routes.GALLERY) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenPhoto = { id -> nav.navigate(Routes.photo(id)) },
            )
        }
        composable(Routes.GALLERY) {
            val vm: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory(container))
            GalleryScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenPhoto = { id -> nav.navigate(Routes.photo(id)) },
                onOpenCamera = { nav.popBackStack(Routes.CAMERA, inclusive = false) },
                onOpenExports = { nav.navigate(Routes.EXPORTS) },
            )
        }
        composable(Routes.EXPORTS) {
            val vm: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory(container))
            ExportsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenPhoto = { id -> nav.navigate(Routes.photo(id)) },
            )
        }
        composable(Routes.PHOTO, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            val vm: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory(container))
            PhotoDetailScreen(
                vm = vm,
                photoId = id,
                onBack = { nav.popBackStack() },
                onEdit = { pid -> nav.navigate(Routes.editor(pid)) },
            )
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            val vm: EditorViewModel = viewModel(key = "editor-$id", factory = EditorViewModel.Factory(container, id))
            EditorScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}
