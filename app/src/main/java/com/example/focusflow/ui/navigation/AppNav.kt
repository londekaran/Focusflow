package com.example.focusflow.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.focusflow.Route
import com.example.focusflow.StatusRequest
import com.example.focusflow.ui.voice.VoiceStatusDialog
import com.example.focusflow.data.Task
import com.example.focusflow.ui.about.AboutScreen
import com.example.focusflow.ui.about.PrivacyScreen
import com.example.focusflow.ui.about.TermsScreen
import com.example.focusflow.ui.account.AccountScreen
import com.example.focusflow.ui.account.PlansScreen
import com.example.focusflow.ui.components.FFBottomBar
import com.example.focusflow.ui.distraction.DistractionSettingsScreen
import com.example.focusflow.ui.distraction.DistractionViewModel
import com.example.focusflow.ui.distraction.DistractionsScreen
import com.example.focusflow.ui.focus.FocusScreen
import com.example.focusflow.ui.focus.FocusViewModel
import com.example.focusflow.ui.home.HomeScreen
import com.example.focusflow.ui.insights.InsightsScreen
import com.example.focusflow.ui.plan.PlanScreen
import com.example.focusflow.ui.plan.PlanViewModel
import com.example.focusflow.ui.settings.SettingsScreen
import com.example.focusflow.ui.tasks.TaskDetailsScreen
import com.example.focusflow.ui.tasks.TaskEditScreen
import com.example.focusflow.ui.tasks.TasksViewModel
import com.example.focusflow.ui.theme.Spacing

/** All navigation lives here: the five tabs, plus task details, the editor, settings, distractions and Ask. */
@Composable
fun AppRoot(vm: TasksViewModel, focusVm: FocusViewModel, distractionVm: DistractionViewModel, planVm: PlanViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = route in Routes.tabs
    val tasks by vm.tasks.collectAsState()
    val sessions by focusVm.sessions.collectAsState()

    fun goTab(r: String) {
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openTask(id: Long) = nav.navigate(Routes.details(id))
    fun startFocus(id: Long?) {
        Route.focusTask.value = id
        goTab(Routes.FOCUS)
    }

    // A tapped notification asks to open a tab.
    val target = Route.target.value
    LaunchedEffect(target) {
        if (target != null) {
            if (target.startsWith("status:")) {
                // A voice notification was tapped: show the status dialog for that task.
                val parts = target.split(":")
                val id = parts.getOrNull(2)?.toLongOrNull()
                if (id != null) Route.status.value = StatusRequest(parts[1], id)
            } else {
                Routes.tabFor(target)?.let { goTab(it) }
            }
            Route.target.value = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isTab) FFBottomBar(selected = Routes.tabs.indexOf(route), onSelect = { goTab(Routes.tabs[it]) })
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(pad).consumeWindowInsets(pad),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(120)) }
        ) {
            composable(Routes.HOME) {
                TabBox {
                    HomeScreen(
                        vm, focusVm,
                        onOpen = ::openTask,
                        onProfile = { nav.navigate(Routes.SETTINGS) },
                        onReview = { goTab(Routes.INSIGHTS) },
                        onSeeAll = { goTab(Routes.PLAN) },
                        onRoutine = { Route.planRoutine.value = true; goTab(Routes.PLAN) },
                        onAdd = { nav.navigate(Routes.edit(0)) },
                        onStartFocus = ::startFocus
                    )
                }
            }
            composable(Routes.PLAN) {
                TabBox {
                    PlanScreen(
                        vm, planVm, onOpen = ::openTask,
                        onAdd = { nav.navigate(Routes.edit(0)) },
                        onEdit = { nav.navigate(Routes.edit(it)) },
                        onStartRoutineFocus = { goTab(Routes.FOCUS) }
                    )
                }
            }
            composable(Routes.FOCUS) {
                TabBox { FocusScreen(tasks, focusVm, distractionVm) { nav.navigate(Routes.DISTRACTIONS) } }
            }
            composable(Routes.INSIGHTS) {
                TabBox {
                    InsightsScreen(
                        vm, focusVm, distractionVm, onOpen = ::openTask,
                        onPlanNextWeek = { Route.planNextWeek.value = true; goTab(Routes.PLAN) }
                    )
                }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    focusVm, planVm,
                    onClose = { nav.popBackStack() },
                    onDistractions = { nav.navigate(Routes.DISTRACTIONS) },
                    onAccount = { nav.navigate(Routes.ACCOUNT) },
                    onPlans = { nav.navigate(Routes.PLANS) },
                    onAbout = { nav.navigate(Routes.ABOUT) },
                    onTerms = { nav.navigate(Routes.TERMS) },
                    onPrivacy = { nav.navigate(Routes.PRIVACY) }
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    onClose = { nav.popBackStack() },
                    onTerms = { nav.navigate(Routes.TERMS) },
                    onPrivacy = { nav.navigate(Routes.PRIVACY) }
                )
            }
            composable(Routes.TERMS) { TermsScreen { nav.popBackStack() } }
            composable(Routes.PRIVACY) { PrivacyScreen { nav.popBackStack() } }
            composable(Routes.ACCOUNT) {
                AccountScreen(onClose = { nav.popBackStack() }, onPlans = { nav.navigate(Routes.PLANS) })
            }
            composable(Routes.PLANS) { PlansScreen { nav.popBackStack() } }
            composable(Routes.DISTRACTIONS) {
                DistractionsScreen(
                    distractionVm, focusVm,
                    onBack = { nav.popBackStack() },
                    onManageApps = { nav.navigate(Routes.APPS) }
                )
            }
            composable(Routes.APPS) { DistractionSettingsScreen(distractionVm) { nav.popBackStack() } }
            composable(Routes.DETAILS, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                TaskDetailsScreen(
                    vm, sessions, e.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.edit(it)) },
                    onStartFocus = ::startFocus
                )
            }
            composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                TaskEditScreen(vm, e.arguments?.getLong("id") ?: 0L, onClose = { nav.popBackStack() })
            }
        }
    }

    val statusRequest = Route.status.value
    if (statusRequest != null) {
        VoiceStatusDialog(statusRequest, tasks, onDismiss = { Route.status.value = null })
    }
}

/** Standard side padding for the five main tabs. */
@Composable
private fun TabBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) { content() }
}

