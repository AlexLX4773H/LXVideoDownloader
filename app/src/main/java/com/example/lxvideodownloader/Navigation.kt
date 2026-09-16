package com.example.lxvideodownloader

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.lxvideodownloader.core.sniffer.SourceType
import com.example.lxvideodownloader.ui.browser.WebViewBrowserScreen
import com.example.lxvideodownloader.ui.main.MainScreen
import com.example.lxvideodownloader.ui.main.MainScreenViewModel

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)
    val context = LocalContext.current
    val mainViewModel: MainScreenViewModel = viewModel()

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry<Main> {
                    MainScreen(
                        onItemClick = { navKey -> backStack.add(navKey) },
                        onNavigateToWebBrowser = { url -> backStack.add(WebBrowser(url)) },
                        modifier = Modifier.fillMaxSize(),
                        viewModel = mainViewModel
                    )
                }
                entry<WebBrowser> { key ->
                    WebViewBrowserScreen(
                        initialUrl = key.url,
                        onVideoSelected = { detectedVideo ->
                            // Navigate back to main screen
                            backStack.removeLastOrNull()
                            // Start the appropriate download based on source type
                            mainViewModel.onWebVideoSelected(context, detectedVideo)
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
            },
    )
}
