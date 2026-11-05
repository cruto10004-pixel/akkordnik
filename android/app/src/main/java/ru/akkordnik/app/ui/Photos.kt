package ru.akkordnik.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private fun decodeScaled(ctx: Context, uri: Uri, max: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= 28) {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            val w = info.size.width; val h = info.size.height
            val k = minOf(1f, max.toFloat() / maxOf(w, h))
            dec.setTargetSize(maxOf(1, (w * k).toInt()), maxOf(1, (h * k).toInt()))
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var sample = 1
    while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val k = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
    return if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
}

/** Фото → data:image/webp;base64 (как в веб-версии, ≤900 px). */
fun photoToDataUrl(ctx: Context, uri: Uri, square: Int = 0): String? = try {
    val bmp = if (square > 0) decodeScaled(ctx, uri, square * 3)?.let { b ->
        val side = minOf(b.width, b.height)
        val c = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
        if (side > square) Bitmap.createScaledBitmap(c, square, square, true) else c
    } else decodeScaled(ctx, uri, 900)
    bmp?.let {
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        it.compress(fmt, 78, out)
        "data:image/webp;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
} catch (_: Throwable) { null }

fun dataUrlToBitmap(s: String): ImageBitmap? = try {
    val b = Base64.decode(s.substringAfter(","), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
} catch (_: Throwable) { null }

private val cache = android.util.LruCache<Int, ImageBitmap>(24)

@Composable
fun rememberPhoto(data: String?): State<ImageBitmap?> = produceState(data?.let { cache.get(it.hashCode()) }, data) {
    if (data == null) { value = null; return@produceState }
    cache.get(data.hashCode())?.let { value = it; return@produceState }
    value = withContext(Dispatchers.Default) { dataUrlToBitmap(data) }?.also { cache.put(data.hashCode(), it) }
}

class PhotoPicker(val pick: () -> Unit, val shoot: () -> Unit)

/** Выбор фото: галерея или камера. Результат — data URL. */
@Composable
fun rememberPhotoPicker(
    app: AppState,
    title: String = "Фото аккорда",
    text: String = "Сфотографируйте руку на грифе или выберите готовое фото.",
    square: Int = 0,
    onPicked: (String) -> Unit,
): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val camUri = remember { arrayOfNulls<Uri>(1) }
    fun handle(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val r = withContext(Dispatchers.IO) { photoToDataUrl(ctx, uri, square) }
            if (r != null) onPicked(r) else app.toast.show("Не удалось открыть фото")
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { handle(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) handle(camUri[0]) }
    return {
        app.dialogs.ask(
            title, text,
            listOf(DBtn("Сфотографировать", "cam", icon = Ic.camera), DBtn("Выбрать из галереи", "gal", BtnKind.Ghost, Ic.image), DBtn("Отмена", null, BtnKind.Ghost))
        ) { v ->
            when (v) {
                "gal" -> gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                "cam" -> try {
                    val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
                    val f = File(dir, "shot.jpg")
                    val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    camUri[0] = u
                    camera.launch(u)
                } catch (_: Throwable) { app.toast.show("Камера недоступна") }
            }
        }
    }
}
