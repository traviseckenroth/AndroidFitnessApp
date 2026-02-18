// File: app/src/main/java/com/example/myapplication/ui/workout/ContentDiscoveryScreen.kt
package com.example.myapplication.ui.workout

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log

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
                        text = content?.title ?: "Loading...",
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
                        val isInstagram = url.contains("instagram.com")
                        IconButton(onClick = {
                            openUrlExternally(context, url)
                        }) {
                            Icon(
                                imageVector = if (isInstagram) Icons.Default.OpenInNew else Icons.Default.OpenInBrowser,
                                contentDescription = if (isInstagram) "Open in Instagram" else "Open in Browser"
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
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                // Set a mobile user agent to avoid some desktop-only issues
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.99 Mobile Safari/537.36"
                            }
                            
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    
                                    // Specifically handle instagram:// schemes which web pages often use for deep linking
                                    if (url.startsWith("instagram://")) {
                                        return try {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }

                                    // Handle non-http/https schemes (like intent://, etc.)
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        return try {
                                            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                            // Ensure the intent can be handled before starting
                                            if (intent.resolveActivity(ctx.packageManager) != null) {
                                                ctx.startActivity(intent)
                                                true
                                            } else {
                                                // If it's an intent:// but no app found, try the fallback URL if present
                                                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                                if (fallbackUrl != null) {
                                                    view?.loadUrl(fallbackUrl)
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("WebView", "Could not handle custom scheme: $url", e)
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                ctx.startActivity(intent)
                                                true
                                            } catch (e2: Exception) {
                                                false
                                            }
                                        }
                                    }
                                    return false // Let WebView handle regular http/https
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
        val uri = Uri.parse(url)
        var intent: Intent? = null

        // Improved deep linking for Instagram
        if (uri.host?.contains("instagram.com") == true) {
            val pathSegments = uri.pathSegments
            // Handle tags: /explore/tags/{tag}/
            if (pathSegments.size >= 3 && pathSegments[0] == "explore" && pathSegments[1] == "tags") {
                val tag = pathSegments[2]
                val instagramUri = Uri.parse("instagram://tag?name=$tag")
                intent = Intent(Intent.ACTION_VIEW, instagramUri)
            }
            // Handle posts: /p/{shortcode}/
            else if (pathSegments.size >= 2 && pathSegments[0] == "p") {
                val shortcode = pathSegments[1]
                val instagramUri = Uri.parse("instagram://media?id=$shortcode") // Note: some versions use media?id, some use p/
                intent = Intent(Intent.ACTION_VIEW, instagramUri)
            }
            // Handle profiles: /{username}/
            else if (pathSegments.size == 1) {
                val username = pathSegments[0]
                val instagramUri = Uri.parse("instagram://user?username=$username")
                intent = Intent(Intent.ACTION_VIEW, instagramUri)
            }
            
            // If we created a specific instagram intent, set package to ensure it opens in the app
            if (intent != null) {
                intent.setPackage("com.instagram.android")
                // Verify if Instagram app can handle this intent
                if (intent.resolveActivity(context.packageManager) == null) {
                    intent = null // Fallback to browser if app not installed or can't handle
                }
            }
        }

        // Default fallback to browser if no specific app intent was created or it failed
        if (intent == null) {
            intent = Intent(Intent.ACTION_VIEW, uri)
        }
        
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("ContentDiscovery", "Error opening URL: $url", e)
    }
}
