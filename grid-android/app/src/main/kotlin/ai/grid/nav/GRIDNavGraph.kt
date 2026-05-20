package ai.grid.nav

import ai.grid.agent.CLUAgent
import ai.grid.ui.codex.CodexScreen
import ai.grid.ui.editor.EditorScreen
import ai.grid.ui.editor.EditorViewModel
import ai.grid.ui.files.FilesScreen
import ai.grid.ui.files.FilesViewModel
import ai.grid.ui.projects.ProjectsScreen
import ai.grid.ui.projects.ProjectsViewModel
import ai.grid.ui.session.SessionScreen
import ai.grid.ui.settings.SettingsScreen
import ai.grid.ui.settings.SettingsViewModel
import ai.grid.ui.stage.StageScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private val BG      = Color(0xFF0A0A0A)
private val CYAN    = Color(0xFF00E5FF)
private val SURFACE = Color(0xFF141414)

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Session  : Screen("session",  "SESSION", Icons.Default.Chat)
    object Projects : Screen("projects", "FORGE",   Icons.Default.FolderOpen)
    object Stage    : Screen("stage",    "STAGE",   Icons.Default.SportsEsports)
    object Editor   : Screen("editor",   "TWEAKER", Icons.Default.Tune)
    object Files    : Screen("files",    "FILES",   Icons.Default.Storage)
    object Codex    : Screen("codex",    "CODEX",   Icons.Default.AccountTree)
    object Settings : Screen("settings", "VENDOR",  Icons.Default.Settings)
}

private val ALL_SCREENS = listOf(
    Screen.Session, Screen.Projects, Screen.Stage,
    Screen.Editor,  Screen.Files,    Screen.Codex, Screen.Settings,
)

@Composable
fun GRIDNavGraph(modifier: Modifier = Modifier) {
    // All ViewModels at activity scope — survive tab switches.
    val cluAgent:   CLUAgent           = viewModel()
    val settingsVm: SettingsViewModel  = viewModel()
    val editorVm:   EditorViewModel    = viewModel()
    val filesVm:    FilesViewModel     = viewModel()
    val projectsVm: ProjectsViewModel  = viewModel()

    val activeProject by projectsVm.activeProject.collectAsState()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        containerColor = BG,
        bottomBar = {
            NavigationBar(containerColor = SURFACE, contentColor = CYAN) {
                ALL_SCREENS.forEach { screen ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon  = { Icon(screen.icon, contentDescription = screen.label) },
                        label = {
                            Text(
                                screen.label,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                            )
                        },
                        selected = selected,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = CYAN,
                            selectedTextColor   = CYAN,
                            indicatorColor      = CYAN.copy(alpha = 0.15f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Session.route,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Session.route)  {
                SessionScreen(
                    onNavigateToStage = { navController.navigate(Screen.Stage.route) },
                    agent = cluAgent,
                )
            }
            composable(Screen.Projects.route) {
                ProjectsScreen(
                    onNavigateToSession = { navController.navigate(Screen.Session.route) },
                    vm = projectsVm,
                )
            }
            composable(Screen.Stage.route)    {
                StageScreen(projectPath = activeProject?.path)
            }
            composable(Screen.Editor.route)   { EditorScreen(vm = editorVm) }
            composable(Screen.Files.route)    { FilesScreen(vm = filesVm) }
            composable(Screen.Codex.route)    { CodexScreen() }
            composable(Screen.Settings.route) { SettingsScreen(vm = settingsVm) }
        }
    }
}
