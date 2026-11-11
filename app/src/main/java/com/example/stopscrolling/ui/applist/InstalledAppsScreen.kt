package com.example.stopscrolling.ui.applist

import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stopscrolling.data.InstalledApp

@Composable
fun InstalledAppsScreen(
    viewModel: InstalledAppsViewModel = viewModel()
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Installed Apps",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error
                )
            }

            apps.isEmpty() -> {
                Text("No apps found")
            }

            else -> {
                Text("${apps.size} apps found")

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn {
                    items(
                        items = apps,
                        key = { it.packageName }
                    ) { app ->
                        InstalledAppRow(app)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppRow(app: InstalledApp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AndroidView(
            factory = { context ->
                ImageView(context)
            },
            update = { imageView ->
                imageView.setImageDrawable(app.icon)
            },
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    HorizontalDivider()
}
