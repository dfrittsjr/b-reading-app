package com.hamferd.breading

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.hamferd.breading.ui.BackupScreen
import com.hamferd.breading.ui.BibleNotesScreen
import com.hamferd.breading.ui.BibleScreen
import com.hamferd.breading.ui.BibleYearScreen
import com.hamferd.breading.ui.LogReadingScreen
import com.hamferd.breading.ui.ProgressScreen
import com.hamferd.breading.ui.Screen
import com.hamferd.breading.ui.TodayScreen
import com.hamferd.breading.ui.theme.BReadingTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BReadingTheme {
                // Simple back stack: Today is the root, and the Bible view sits on top of it.
                // Other screens can open "Log other reading".
                var backStack by rememberSaveable { mutableStateOf(listOf(Screen.Today)) }
                val navigate = { screen: Screen -> backStack = backStack + screen }
                val back = { backStack = backStack.dropLast(1) }

                BackHandler(enabled = backStack.size > 1, onBack = back)
                when (backStack.last()) {
                    Screen.Today -> TodayScreen(viewModel, onNavigate = navigate)
                    Screen.Bible -> BibleScreen(viewModel, onShowPlan = back, onOpenNotes = { navigate(Screen.BibleNotes) })
                    Screen.BibleNotes -> BibleNotesScreen(
                        viewModel = viewModel,
                        onBack = back,
                        onOpenNote = { note ->
                            viewModel.showInBible(note)
                            backStack = listOf(Screen.Today, Screen.Bible)
                        },
                    )
                    Screen.PlanProgress -> ProgressScreen(
                        viewModel = viewModel,
                        onBack = back,
                        onOpenDay = { date ->
                            viewModel.goToDate(date)
                            backStack = listOf(Screen.Today)
                        },
                    )
                    Screen.BibleYear -> BibleYearScreen(viewModel, onBack = back, onLogReading = { navigate(Screen.LogReading) })
                    Screen.LogReading -> LogReadingScreen(viewModel, onBack = back)
                    Screen.Backup -> BackupScreen(viewModel, onBack = back)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
    }
}
