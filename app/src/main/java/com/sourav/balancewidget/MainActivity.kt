package com.sourav.balancewidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sourav.balancewidget.ui.HomeScreen
import com.sourav.balancewidget.ui.theme.BalanceWidgetTheme
import com.sourav.balancewidget.widget.BalanceWidget
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BalanceWidgetTheme {
                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        "balance widget".uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                                    titleContentColor = MaterialTheme.colorScheme.onBackground
                                ),
                                scrollBehavior = scrollBehavior
                            )
                        }
                    ) { padding ->
                        HomeScreen(modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Safety net: any time the user opens or returns to the app, push the latest
        // balance to the widget. Covers cases where the SMS receiver was killed before
        // it could call updateAll (e.g. doze, battery optimizer, app force-stopped).
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { BalanceWidget().updateAll(applicationContext) }
        }
    }
}
