package com.example.focusflow.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.example.focusflow.system.ProfilePhoto

/**
 * The round profile picture, or the first letter of the name (or a person icon) when there is none.
 * [version] changes whenever the picture changes, so the image reloads.
 */
@Composable
fun ProfileAvatar(size: Dp, name: String, version: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, version) {
        value = if (version > 0) ProfilePhoto.load(ctx)?.asImageBitmap() else null
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(cs.primaryContainer).then(modifier),
        contentAlignment = Alignment.Center
    ) {
        val p = photo
        val initial = name.trim().firstOrNull()?.uppercase()
        when {
            p != null -> Image(p, "Profile picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            initial != null -> Text(initial, style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
            else -> Icon(Icons.Rounded.Person, null, tint = cs.onPrimaryContainer)
        }
    }
}
