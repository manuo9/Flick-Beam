package com.flickbeam.tv.ui.moreapps

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.flickbeam.shared.ShowcaseApps

/** "More by manuo9": a simple showcase of the developer's other apps. */
@Composable
fun MoreAppsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Column {
            Text(
                text = "More by manuo9",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 24.dp),
            ) {
                ShowcaseApps.forEach { app ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(20.dp),
                    ) {
                        Text(
                            text = app.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = app.description,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(app.link)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                // No browser on this TV — nothing we can do about that.
                                runCatching { context.startActivity(intent) }
                            },
                        ) {
                            Text(app.link)
                        }
                    }
                }
            }
        }
    }
}
