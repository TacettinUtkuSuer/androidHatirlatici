package com.utkusuer.hatirlatici.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.utkusuer.hatirlatici.ui.calendar.CalendarScreen
import com.utkusuer.hatirlatici.ui.dashboard.DashboardScreen
import com.utkusuer.hatirlatici.ui.edit.EditPaymentScreen
import com.utkusuer.hatirlatici.ui.payments.PaymentsScreen

private enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Özet", Icons.Filled.Home),
    CALENDAR("calendar", "Takvim", Icons.Filled.DateRange),
    PAYMENTS("payments", "Kayıtlar", Icons.AutoMirrored.Filled.List),
}

private const val EDIT_ROUTE = "edit?id={id}"
private fun editRoute(id: Long = 0L) = "edit?id=$id"

/**
 * Uygulama kabuğu. Dar ekranda alt gezinme çubuğu, geniş ekranda (tablet / katlanabilir / yatay)
 * yan gezinme rayı kullanılır.
 */
@Composable
fun HatirlaticiAppUi(widthSizeClass: WindowWidthSizeClass) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showNav = TopLevel.entries.any { it.route == route }
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    Row(Modifier.fillMaxSize()) {
        if (useRail && showNav) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TopLevel.entries.forEach { item ->
                    NavigationRailItem(
                        selected = route == item.route,
                        onClick = { nav.navigateTopLevel(item.route) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail && showNav) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        TopLevel.entries.forEach { item ->
                            NavigationBarItem(
                                selected = route == item.route,
                                onClick = { nav.navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = TopLevel.DASHBOARD.route,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                composable(TopLevel.DASHBOARD.route) {
                    DashboardScreen(
                        onAdd = { nav.navigate(editRoute()) },
                        onOpenPayment = { nav.navigate(editRoute(it)) },
                    )
                }
                composable(TopLevel.CALENDAR.route) {
                    CalendarScreen(
                        widthSizeClass = widthSizeClass,
                        onOpenPayment = { nav.navigate(editRoute(it)) },
                    )
                }
                composable(TopLevel.PAYMENTS.route) {
                    PaymentsScreen(
                        onAdd = { nav.navigate(editRoute()) },
                        onOpenPayment = { nav.navigate(editRoute(it)) },
                    )
                }
                composable(
                    EDIT_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
                ) { entry ->
                    EditPaymentScreen(
                        paymentId = entry.arguments?.getLong("id") ?: 0L,
                        onDone = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
