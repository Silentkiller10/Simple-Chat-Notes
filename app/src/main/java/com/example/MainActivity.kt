package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.MessageType
import com.example.ui.SavedViewModel
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MediaGalleryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SavedAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SavedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming share intents (if app was launched via Share Sheet)
        handleIncomingIntent(intent)

        setContent {
            val themeMode by viewModel.userPreferences.themeMode.collectAsState()
            val isAppLocked by viewModel.isAppLocked.collectAsState()

            SavedAppTheme(themePreference = themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "chats",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable("chats") {
                            ChatListScreen(
                                viewModel = viewModel,
                                onOpenChat = { chatId ->
                                    viewModel.selectChat(chatId)
                                    navController.navigate("home")
                                },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToGallery = { navController.navigate("gallery") }
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    if (!navController.popBackStack()) {
                                        navController.navigate("chats")
                                    }
                                },
                                onNavigateToMediaGallery = { navController.navigate("gallery") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("gallery") {
                            MediaGalleryScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onLockTriggered = { /* App locked */ }
                            )
                        }
                    }

                    // App Lock overlay if locked
                    AnimatedContent(
                        targetState = isAppLocked,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "app_lock_overlay"
                    ) { locked ->
                        if (locked) {
                            AppLockScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    lifecycleScope.launch {
                        viewModel.repository.saveTextMessage(
                            text = sharedText,
                            chatId = viewModel.activeChatId.value
                        )
                        Toast.makeText(this@MainActivity, "Saved to Saved Messages", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                val caption = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

                if (streamUri != null) {
                    lifecycleScope.launch {
                        val typeHint = when {
                            type.startsWith("image/") -> MessageType.IMAGE
                            type.startsWith("video/") -> MessageType.VIDEO
                            type.startsWith("audio/") -> MessageType.AUDIO
                            else -> MessageType.FILE
                        }
                        viewModel.repository.saveMediaAttachments(
                            uris = listOf(streamUri),
                            caption = caption,
                            typeHint = typeHint,
                            chatId = viewModel.activeChatId.value
                        )
                        Toast.makeText(this@MainActivity, "Saved to Saved Messages", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            val caption = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

            if (!streamUris.isNullOrEmpty()) {
                lifecycleScope.launch {
                    val typeHint = when {
                        type.startsWith("image/") -> MessageType.IMAGE
                        type.startsWith("video/") -> MessageType.VIDEO
                        type.startsWith("audio/") -> MessageType.AUDIO
                        else -> MessageType.FILE
                    }
                    viewModel.repository.saveMediaAttachments(
                        uris = streamUris,
                        caption = caption,
                        typeHint = typeHint,
                        chatId = viewModel.activeChatId.value
                    )
                    Toast.makeText(this@MainActivity, "Saved ${streamUris.size} items to Saved Messages", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
