// File: app/src/main/java/com/example/myapplication/ui/workout/ContentDiscoveryScreen.kt
package com.example.myapplication.ui.workout

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentDiscoveryScreen(
    contentId: Long,
    onBack: () -> Unit,
    viewModel: ContentViewModel = hiltViewModel()
) {
    val content by viewModel.content.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(contentId) {
        viewModel.loadContent(contentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = content?.title ?: "Discovery",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    content?.url?.let { url ->
                        // FIX: Removed the unused `isSocial` variable entirely.
                        IconButton(onClick = {
                            openUrlExternally(context, url)
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (content != null) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true // Suppressed via @SuppressLint at top
                                domStorageEnabled = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                cacheMode = WebSettings.LOAD_DEFAULT

                                //noinspection SpellCheckingInspection
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false

                                    // Deep link handling for social apps
                                    if (url.startsWith("instagram://") || url.startsWith("reddit://") || url.startsWith("twitter://")) {
                                        return try {
                                            // FIX: Upgraded to String.toUri()
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                            true
                                        } catch (_: Exception) { // FIX: Replaced unused 'e' with '_'
                                            false
                                        }
                                    }

                                    // Force external browser for login/auth pages which often fail in WebView
                                    if (url.contains("accounts.google.com") || url.contains("facebook.com/login") || url.contains("instagram.com/accounts/login")) {
                                        openUrlExternally(ctx, url)
                                        return true
                                    }

                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        return try {
                                            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                            if (intent.resolveActivity(ctx.packageManager) != null) {
                                                ctx.startActivity(intent)
                                                true
                                            } else {
                                                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                                if (fallbackUrl != null) {
                                                    view?.loadUrl(fallbackUrl)
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                        } catch (_: Exception) { // FIX: Replaced unused 'e' with '_'
                                            try {
                                                // FIX: Upgraded to String.toUri()
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                                true
                                            } catch (_: Exception) { // FIX: Replaced unused 'e2' with '_'
                                                false
                                            }
                                        }
                                    }
                                    return false
                                }
                            }
                            loadUrl(content!!.url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

private fun openUrlExternally(context: Context, url: String) {
    try {
        // FIX: Upgraded to String.toUri()
        val uri = url.toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)

        // Attempt to find a specific app to handle social links
        if (url.contains("instagram.com")) {
            intent.setPackage("com.instagram.android")
        } else if (url.contains("reddit.com")) {
            intent.setPackage("com.reddit.frontpage")
        } else if (url.contains("twitter.com") || url.contains("x.com")) {
            intent.setPackage("com.twitter.android")
        }

        // If specific app is not available, reset package to use system chooser
        if (intent.resolveActivity(context.packageManager) == null) {
            intent.setPackage(null)
        }

        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("ContentDiscovery", "Error opening URL: $url", e) // 'e' is correctly utilized here
    }
}