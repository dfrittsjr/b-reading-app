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
import com.hamferd.breading.ui.ProgressScreen
import com.hamferd.breading.ui.TodayScreen
import com.hamferd.breading.ui.theme.BReadingTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BReadingTheme {
                var showProgress by rememberSaveable { mutableStateOf(false) }
                if (showProgress) {
                    BackHandler { showProgress = false }
                    ProgressScreen(
                        viewModel = viewModel,
                        onBack = { showProgress = false },
                        onOpenDay = { date ->
                            viewModel.goToDate(date)
                            showProgress = false
                        },
                    )
                } else {
                    TodayScreen(viewModel, onOpenProgress = { showProgress = true })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
    }
}
