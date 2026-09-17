package com.matheo.flashcardcompanion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.matheo.flashcardcompanion.ui.CoursesScreen
import com.matheo.flashcardcompanion.ui.ExamsScreen
import com.matheo.flashcardcompanion.ui.FlashcardTheme
import com.matheo.flashcardcompanion.ui.HomeScreen
import com.matheo.flashcardcompanion.ui.LocalFcColors
import com.matheo.flashcardcompanion.ui.LocalT
import com.matheo.flashcardcompanion.ui.MaxContentWidth
import com.matheo.flashcardcompanion.ui.PagePadding
import com.matheo.flashcardcompanion.ui.PdfViewerScreen
import com.matheo.flashcardcompanion.ui.ReviewScreen
import com.matheo.flashcardcompanion.ui.SettingsScreen
import com.matheo.flashcardcompanion.ui.StatsScreen
import com.matheo.flashcardcompanion.ui.StorageGateScreen
import com.matheo.flashcardcompanion.ui.Translator

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by lazy { ViewModelProvider(this)[AppViewModel::class.java] }

    /**
     * Android 13+ gates notifications behind a runtime grant. Without it the
     * daily due-cards reminder posts nothing and fails silently, which is the
     * only thing left that used to be handled outside the app.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { Root() }
    }

    override fun onResume() {
        super.onResume()
        // All-files access is granted in system Settings, outside the app, so
        // returning to the foreground is the only reliable moment to notice it.
        vm.recheckStorage()
    }
}

@Composable
private fun Root(vm: AppViewModel = viewModel()) {
    val themePref by vm.theme.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val dark = when (themePref) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    FlashcardTheme(dark = dark) {
        CompositionLocalProvider(LocalT provides Translator(lang)) {
            val colors = LocalFcColors.current
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.bg)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) { AppNav(vm) }
            }
        }
    }
}

@Composable
private fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val storageGranted by vm.storageGranted.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (!storageGranted) {
        Box(Modifier.fillMaxWidth().padding(horizontal = PagePadding)) {
            StorageGateScreen(onGrant = { requestAllFilesAccess(context) })
        }
        return
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onReview = { path -> nav.navigate("review?path=${Uri.encode(path)}") },
                onOpen = { route -> nav.navigate(route) },
            )
        }
        composable(
            "review?path={path}",
            arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            ReviewScreen(
                vm = vm,
                path = entry.arguments?.getString("path").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenPdf = { rel -> nav.navigate("pdf?path=${Uri.encode(rel)}") },
            )
        }
        composable("stats") { StatsScreen(vm) { nav.popBackStack() } }
        composable("exams") { ExamsScreen(vm) { nav.popBackStack() } }
        composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
        composable("courses") {
            CoursesScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenPdf = { rel -> nav.navigate("pdf?path=${Uri.encode(rel)}") },
            )
        }
        composable(
            "pdf?path={path}",
            arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            PdfViewerScreen(
                vm = vm,
                absPath = entry.arguments?.getString("path").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}

private fun requestAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val direct = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Some OEM builds do not honour the per-package deep link; fall back to the
    // global list rather than leaving the button doing nothing.
    runCatching { context.startActivity(direct) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
