package com.example.focusflow.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.focusflow.system.ProfilePhoto
import kotlinx.coroutines.launch

/**
 * Gallery picker followed by a square cropper. Returns the function that opens the gallery.
 * [onResult] gets true when the cropped picture was saved, false when saving failed.
 * Cancelling the picker or the cropper changes nothing, so the current picture stays.
 */
@Composable
fun rememberProfilePhotoPicker(onResult: (Boolean) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) pending = uri }

    pending?.let { uri ->
        ImageCropDialog(
            uri = uri,
            onCancel = { pending = null },
            onUnreadable = { pending = null; onResult(false) },
            onConfirm = { bmp, cx, cy, side ->
                pending = null
                scope.launch { onResult(ProfilePhoto.saveCropped(ctx, bmp, cx, cy, side)) }
            }
        )
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Full-screen 1:1 cropper: drag to reposition, pinch or use the slider to zoom. */
@Composable
private fun ImageCropDialog(
    uri: Uri, onCancel: () -> Unit, onUnreadable: () -> Unit,
    onConfirm: (Bitmap, Float, Float, Float) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var off by remember(uri) { mutableStateOf(Offset.Zero) }
    var boxPxSize by remember(uri) { mutableFloatStateOf(0f) }

    LaunchedEffect(uri) {
        val b = ProfilePhoto.loadUpright(ctx, uri)
        if (b == null) onUnreadable() else bmp = b
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Crop photo", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Drag to move, pinch to zoom", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(16.dp))

            val b = bmp
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (b == null) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    val density = LocalDensity.current
                    val boxPx = with(density) { minOf(maxWidth, maxHeight).toPx() }
                    SideEffect { boxPxSize = boxPx }
                    // "Cover" size of the picture inside the square at zoom 1.
                    val cover = boxPx / minOf(b.width, b.height)
                    val wPx = b.width * cover
                    val hPx = b.height * cover

                    fun clamp(o: Offset, z: Float) = Offset(
                        o.x.coerceIn(-maxOf(0f, (wPx * z - boxPx) / 2), maxOf(0f, (wPx * z - boxPx) / 2)),
                        o.y.coerceIn(-maxOf(0f, (hPx * z - boxPx) / 2), maxOf(0f, (hPx * z - boxPx) / 2))
                    )

                    val boxDp = with(density) { boxPx.toDp() }
                    Box(
                        Modifier.size(boxDp).clip(androidx.compose.ui.graphics.RectangleShape)
                            .pointerInput(b) {
                                detectTransformGestures { _, pan, g, _ ->
                                    val z = (zoom * g).coerceIn(1f, 6f)
                                    zoom = z
                                    off = clamp(off + pan, z)
                                }
                            }
                    ) {
                        Image(
                            b.asImageBitmap(), "Picture to crop", contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .requiredSize(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    scaleX = zoom; scaleY = zoom
                                    translationX = off.x; translationY = off.y
                                }
                        )
                        // Dim everything outside the circle the avatar will show; the square is what gets saved.
                        Canvas(Modifier.fillMaxSize()) {
                            val hole = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
                            val full = Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
                            val mask = Path().apply { op(full, hole, PathOperation.Difference) }
                            drawPath(mask, Color.Black.copy(alpha = 0.55f))
                            drawRoundRect(Color.White, Offset.Zero, size, CornerRadius.Zero, style = Stroke(2.dp.toPx()))
                            drawOval(Color.White.copy(alpha = 0.8f), Offset.Zero, size, style = Stroke(1.dp.toPx()))
                        }
                    }

                    // Keep the picture covering the square if the layout or zoom changed.
                    SideEffect { off = clamp(off, zoom) }
                }
            }

            Spacer(Modifier.height(8.dp))
            Slider(
                value = zoom, onValueChange = { zoom = it }, valueRange = 1f..6f, enabled = b != null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f), neutral = true)
                PrimaryButton(
                    "Confirm", enabled = b != null, modifier = Modifier.weight(1f),
                    onClick = {
                        val bm = b ?: return@PrimaryButton
                        // Map the visible square back to pixels of the full picture.
                        val minSide = minOf(bm.width, bm.height).toFloat()
                        val perDisplayPx = if (boxPxSize > 0f) minSide / (boxPxSize * zoom) else 0f
                        onConfirm(
                            bm,
                            bm.width / 2f - off.x * perDisplayPx,
                            bm.height / 2f - off.y * perDisplayPx,
                            minSide / zoom
                        )
                    }
                )
            }
        }
    }
}
