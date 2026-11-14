package com.zeroxion.cardscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroxion.cardscanner.ui.CardscannerTheme
import com.zeroxion.cardscanner.ui.CollectionScreen
import com.zeroxion.cardscanner.ui.DetailScreen
import com.zeroxion.cardscanner.ui.ScanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            CardscannerTheme {
                CardscannerApp()
            }
        }
    }
}

private const val TAB_COLLECTION = 0
private const val TAB_SCAN = 1

@Composable
fun CardscannerApp(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(TAB_COLLECTION) }
    var detailUid by rememberSaveable { mutableStateOf<String?>(null) }
    // Reihenfolge der Karten, wie sie beim Öffnen in der Sammlung standen – darin wird gewischt.
    var detailOrder by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    val items by vm.items.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val detailItem = detailUid?.let { uid -> items.firstOrNull { it.uid == uid } }
    val detailItems = remember(items, detailOrder, detailItem) {
        val ordered = detailOrder.mapNotNull { id -> items.firstOrNull { it.uid == id } }
        if (detailItem != null && ordered.none { it.uid == detailItem.uid }) listOf(detailItem) else ordered
    }

    BackHandler(enabled = detailItem != null) { detailUid = null }
    BackHandler(enabled = detailItem == null && tab == TAB_SCAN) {
        if (scan is ScanState.Result) vm.discardScan() else tab = TAB_COLLECTION
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (detailItem == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                    )
                    NavigationBarItem(
                        selected = tab == TAB_COLLECTION,
                        onClick = { tab = TAB_COLLECTION },
                        icon = { Icon(Icons.Rounded.Style, contentDescription = null) },
                        label = { Text("Sammlung") },
                        colors = colors,
                    )
                    NavigationBarItem(
                        selected = tab == TAB_SCAN,
                        onClick = { tab = TAB_SCAN },
                        icon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                        label = { Text("Scannen") },
                        colors = colors,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                detailItem != null -> {
                    val start = detailItems.indexOfFirst { it.uid == detailItem.uid }.coerceAtLeast(0)
                    val pagerState = rememberPagerState(initialPage = start) { detailItems.size }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { page -> detailItems.getOrNull(page)?.uid ?: page },
                    ) { page ->
                        val pageItem = detailItems.getOrNull(page)
                        if (pageItem != null) {
                            DetailScreen(
                                vm = vm,
                                item = pageItem,
                                padding = padding,
                                onBack = { detailUid = null },
                                position = if (detailItems.size > 1) "${page + 1} / ${detailItems.size}" else null,
                            )
                        }
                    }
                }

                tab == TAB_SCAN -> ScanScreen(
                    vm = vm,
                    padding = padding,
                )

                else -> CollectionScreen(
                    vm = vm,
                    padding = padding,
                    onOpen = { uid, order ->
                        detailOrder = ArrayList(order)
                        detailUid = uid
                    },
                    onScan = { tab = TAB_SCAN },
                )
            }
        }
    }
}
