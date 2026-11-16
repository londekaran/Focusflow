package com.example.focusflow.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.ui.distraction.DistractionViewModel
import com.example.focusflow.ui.focus.FocusViewModel
import com.example.focusflow.ui.plan.PlanViewModel
import com.example.focusflow.ui.tasks.TasksViewModel

class VmFactory(private val ctx: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = ctx.applicationContext
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(TasksViewModel::class.java) ->
                TasksViewModel(ServiceLocator.repository(app))
            modelClass.isAssignableFrom(FocusViewModel::class.java) ->
                FocusViewModel(ServiceLocator.sessions(app), ServiceLocator.settings(app))
            modelClass.isAssignableFrom(DistractionViewModel::class.java) ->
                DistractionViewModel(ServiceLocator.distractions(app), ServiceLocator.settings(app), app)
            modelClass.isAssignableFrom(PlanViewModel::class.java) ->
                PlanViewModel(ServiceLocator.settings(app), app)
            else -> throw IllegalArgumentException("Unknown ViewModel: $modelClass")
        }
        return vm as T
    }
}
