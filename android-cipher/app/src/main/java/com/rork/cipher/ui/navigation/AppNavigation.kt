package com.rork.cipher.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rork.cipher.data.ClaimResult
import com.rork.cipher.data.Invite
import com.rork.cipher.data.SessionState
import com.rork.cipher.ui.CipherViewModel
import com.rork.cipher.ui.DeepLinks
import com.rork.cipher.ui.screens.AccountKeyScreen
import com.rork.cipher.ui.screens.AppLockScreen
import com.rork.cipher.ui.screens.ConversationScreen
import com.rork.cipher.ui.screens.GroupProfileScreen
import com.rork.cipher.ui.screens.HomeScreen
import com.rork.cipher.ui.screens.KeyVaultScreen
import com.rork.cipher.ui.screens.NewChatSheet
import com.rork.cipher.ui.screens.NewGroupSheet
import com.rork.cipher.ui.screens.PeerProfileScreen
import com.rork.cipher.ui.screens.PinLockScreen
import com.rork.cipher.ui.screens.ProfileScreen
import com.rork.cipher.ui.screens.UnlockScreen
import com.rork.cipher.ui.screens.WelcomeScreen
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextSecondary

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Chats", Icons.Outlined.Forum),
    PROFILE("profile", "Profile", Icons.Outlined.Person)
}

@Composable
fun AppNavigation() {
    val viewModel: CipherViewModel = viewModel()
    val session by viewModel.session.collectAsStateWithLifecycle()
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var showUnlock by remember { mutableStateOf(false) }
    var keyFallback by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        when (val state = session) {
            SessionState.Loading -> Unit

            SessionState.SignedOut -> if (showUnlock) {
                UnlockScreen(
                    username = null,
                    onUnlock = viewModel::unlock,
                    onBack = { showUnlock = false },
                    onForget = null
                )
            } else {
                WelcomeScreen(
                    onClaim = { username ->
                        val result = viewModel.createAccount(username)
                        if (result is ClaimResult.Success) pendingKey = result.accountKey
                        result
                    },
                    onUseKey = { showUnlock = true }
                )
            }

            is SessionState.Locked -> if (state.pin && !keyFallback) {
                PinLockScreen(
                    username = state.username,
                    biometricOffered = state.biometric,
                    onSubmit = viewModel::unlockWithPin,
                    onBiometric = { viewModel.unlockWithBiometric() },
                    onUseKey = { keyFallback = true }
                )
            } else {
                UnlockScreen(
                    username = state.username,
                    onUnlock = viewModel::unlock,
                    onBack = if (state.pin) {
                        { keyFallback = false }
                    } else {
                        null
                    },
                    onForget = viewModel::signOut
                )
            }

            is SessionState.Active -> {
                val key = pendingKey
                if (key != null) {
                    AccountKeyScreen(
                        username = state.account.username,
                        accountKey = key,
                        onEnter = { pendingKey = null }
                    )
                } else {
                    keyFallback = false
                    MainShell(viewModel = viewModel, username = state.account.username)
                }
            }
        }
    }
}

@Composable
private fun MainShell(viewModel: CipherViewModel, username: String) {
    val navController = rememberNavController()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val invite by DeepLinks.pending.collectAsStateWithLifecycle()

    val openThread: (String) -> Unit = { peer ->
        navController.navigate("chat/${Uri.encode(peer)}")
    }

    LaunchedEffect(invite) {
        when (val pending = invite) {
            null -> Unit

            is Invite.User -> {
                DeepLinks.consume()
                if (!pending.username.equals(username, ignoreCase = true)) {
                    openThread(pending.username)
                }
            }

            // A verification code only means something inside the verify sheet,
            // where the other key is already known. Opening the chat is the
            // most it can honestly do from a cold link.
            is Invite.Verify -> {
                DeepLinks.consume()
                if (!pending.username.equals(username, ignoreCase = true)) {
                    openThread(pending.username)
                }
            }

            is Invite.Room -> {
                DeepLinks.consume()
                viewModel.joinGroup(pending)?.let(openThread)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "tabs",
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) }
    ) {
        composable("tabs") {
            TabsScaffold(
                viewModel = viewModel,
                username = username,
                onOpenChat = openThread,
                onOpenKey = { navController.navigate("key") },
                onOpenLock = { navController.navigate("lock") }
            )
        }
        composable("key") {
            KeyVaultScreen(
                username = username,
                accountKey = viewModel.accountKey(),
                blockScreenshots = settings.blockScreenshots,
                onBack = { navController.popBackStack() }
            )
        }
        composable("lock") {
            AppLockScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("chat/{peer}") { entry ->
            val peer = entry.arguments?.getString("peer").orEmpty()
            ConversationScreen(
                peer = peer,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenProfile = {
                    val route = if (viewModel.thread(peer)?.isGroup == true) "room" else "peer"
                    navController.navigate("$route/${Uri.encode(peer)}")
                }
            )
        }
        composable("peer/{peer}") { entry ->
            val peer = entry.arguments?.getString("peer").orEmpty()
            PeerProfileScreen(
                peer = peer,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLeaveChat = {
                    navController.popBackStack(route = "tabs", inclusive = false)
                }
            )
        }
        composable("room/{peer}") { entry ->
            val peer = entry.arguments?.getString("peer").orEmpty()
            GroupProfileScreen(
                peer = peer,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLeft = {
                    navController.popBackStack(route = "tabs", inclusive = false)
                }
            )
        }
    }
}

@Composable
private fun TabsScaffold(
    viewModel: CipherViewModel,
    username: String,
    onOpenChat: (String) -> Unit,
    onOpenKey: () -> Unit,
    onOpenLock: () -> Unit
) {
    val tabController: NavHostController = rememberNavController()
    val backStack by tabController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.hierarchy
        ?.firstOrNull { entry -> Tab.entries.any { it.route == entry.route } }
        ?.route ?: Tab.HOME.route
    var showNewChat by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceElevated,
                tonalElevation = 0.dp,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                Tab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                tabController.navigate(tab.route) {
                                    popUpTo(tabController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SignalGreen,
                            selectedTextColor = SignalGreen,
                            indicatorColor = Canvas,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Tab.HOME.route) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { showNewGroup = true },
                        containerColor = SurfaceElevated,
                        contentColor = SignalGreen,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Outlined.Groups, contentDescription = "New room")
                    }
                    ExtendedFloatingActionButton(
                        onClick = { showNewChat = true },
                        containerColor = SignalGreen,
                        contentColor = OnSignal,
                        shape = RoundedCornerShape(28.dp),
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text("New chat", style = MaterialTheme.typography.titleSmall) }
                    )
                }
            }
        }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        val listPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            end = padding.calculateEndPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 96.dp
        )

        NavHost(
            navController = tabController,
            startDestination = Tab.HOME.route,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenChat = onOpenChat,
                    contentPadding = listPadding
                )
            }
            composable(Tab.PROFILE.route) {
                ProfileScreen(
                    viewModel = viewModel,
                    username = username,
                    onOpenKey = onOpenKey,
                    onOpenLock = onOpenLock,
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 24.dp
                    )
                )
            }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            viewModel = viewModel,
            onDismiss = { showNewChat = false },
            onOpenChat = { peer ->
                showNewChat = false
                onOpenChat(peer)
            }
        )
    }

    if (showNewGroup) {
        NewGroupSheet(
            viewModel = viewModel,
            onDismiss = { showNewGroup = false },
            onCreated = { threadId ->
                showNewGroup = false
                onOpenChat(threadId)
            }
        )
    }
}
