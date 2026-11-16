package com.example.focusflow

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.system.Notifications
import com.example.focusflow.system.ReminderScheduler
import com.example.focusflow.ui.VmFactory
import com.example.focusflow.ui.distraction.DistractionViewModel
import com.example.focusflow.ui.focus.FocusViewModel
import com.example.focusflow.ui.navigation.AppRoot
import com.example.focusflow.ui.onboarding.OnboardingFlow
import com.example.focusflow.ui.plan.PlanViewModel
import com.example.focusflow.ui.tasks.TasksViewModel
import com.example.focusflow.ui.theme.FocusTheme
import com.example.focusflow.ui.theme.ThemeState

/** Bumped every time the app returns to the foreground, so screens can re-check permissions and the date. */
object UiTick { val resume = mutableIntStateOf(0) }

/** Hand-offs between screens: a tab to open, a task to pre-select in Focus, "plan next week". */
/** Asks the app to show the status dialog for a task. [mode] is incoming, deadline or checkin. */
data class StatusRequest(val mode: String, val taskId: Long)

object Route {
    val target = mutableStateOf<String?>(null)
    val planNextWeek = mutableStateOf(false)
    val planRoutine = mutableStateOf(false)
    val focusTask = mutableStateOf<Long?>(null)
    val status = mutableStateOf<StatusRequest?>(null)
}

class MainActivity : ComponentActivity() {
    private val vm: TasksViewModel by viewModels { VmFactory(applicationContext) }
    private val focusVm: FocusViewModel by viewModels { VmFactory(applicationContext) }
    private val distractionVm: DistractionViewModel by viewModels { VmFactory(applicationContext) }
    private val planVm: PlanViewModel by viewModels { VmFactory(applicationContext) }

    override fun onCreate(b: android.os.Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()
        Notifications.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        handleRoute(intent)
        ThemeState.load(this)
        val settings = ServiceLocator.settings(this)
        setContent {
            FocusTheme {
                // Onboarding only on the very first launch; the flag is saved when the flow finishes.
                var onboarded by rememberSaveable { mutableStateOf(settings.onboardingDone) }
                if (onboarded) AppRoot(vm, focusVm, distractionVm, planVm)
                else OnboardingFlow { settings.onboardingDone = true; onboarded = true }
            }
        }
        ReminderScheduler.reschedule(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRoute(intent)
    }

    private fun handleRoute(i: Intent?) {
        i?.getStringExtra("route")?.let { Route.target.value = it }
    }

    override fun onResume() {
        super.onResume()
        UiTick.resume.intValue++
    }

    override fun onStop() {
        super.onStop()
        // Tasks may have changed while the app was open: bring the reminder alarms up to date.
        ReminderScheduler.reschedule(applicationContext)
    }
}

/** "Display over other apps": needed to cover a distracting app with the full-screen warning. */
fun hasOverlayAccess(c: Context): Boolean = android.provider.Settings.canDrawOverlays(c)

fun hasUsageAccess(c: Context): Boolean {
    val ops = c.getSystemService(AppOpsManager::class.java)
    return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
}
