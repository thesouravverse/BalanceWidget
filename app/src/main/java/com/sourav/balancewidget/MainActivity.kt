package com.sourav.balancewidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sourav.balancewidget.ui.ExpenseScreen
import com.sourav.balancewidget.ui.HomeScreen
import com.sourav.balancewidget.ui.theme.BalanceWidgetTheme
import com.sourav.balancewidget.ui.theme.CredPalette
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
                var selectedTab by rememberSaveable { mutableStateOf(0) }
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
                                        if (selectedTab == 0) "balance widget".uppercase()
                                        else "expenses".uppercase(),
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
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = {
                                        Icon(
                                            Icons.Filled.AccountBalanceWallet,
                                            contentDescription = null
                                        )
                                    },
                                    label = { Text("Balance") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CredPalette.Gold,
                                        selectedTextColor = CredPalette.Gold,
                                        indicatorColor = CredPalette.Gold.copy(alpha = 0.12f),
                                        unselectedIconColor = CredPalette.TextMuted,
                                        unselectedTextColor = CredPalette.TextMuted
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = {
                                        Icon(
                                            Icons.Filled.ReceiptLong,
                                            contentDescription = null
                                        )
                                    },
                                    label = { Text("Expenses") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CredPalette.Gold,
                                        selectedTextColor = CredPalette.Gold,
                                        indicatorColor = CredPalette.Gold.copy(alpha = 0.12f),
                                        unselectedIconColor = CredPalette.TextMuted,
                                        unselectedTextColor = CredPalette.TextMuted
                                    )
                                )
                            }
                        }
                    ) { padding ->
                        when (selectedTab) {
                            0 -> HomeScreen(modifier = Modifier.padding(padding))
                            else -> ExpenseScreen(modifier = Modifier.padding(padding))
                        }
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
