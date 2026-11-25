package com.tvb.player

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File

/** Creates recordings in the shared Movies/Tv Ch Recorded/Recordings folder on Android 10+. */
class PublicRecordingTarget private constructor(
    val uri: Uri?,
    val file: File?,
    val descriptor: ParcelFileDescriptor?
) {
    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT >= 29 && uri != null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    fun delete(context: Context) {
        try {
            descriptor?.close()
        } catch (_: Exception) { }
        if (uri != null) {
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
        } else {
            try { file?.delete() } catch (_: Exception) { }
        }
    }

    companion object {
        private const val RELATIVE_PATH = "Tv Ch Recorded/Recordings"

        fun create(context: Context, displayName: String, mimeType: String): PublicRecordingTarget {
            if (Build.VERSION.SDK_INT >= 29) {
                val collection = if (mimeType == "video/mp4") {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(collection, values)
                    ?: throw IllegalStateException("ساخت فایل ضبط در پوشه عمومی انجام نشد")
                val descriptor = try {
                    context.contentResolver.openFileDescriptor(uri, "w")
                        ?: throw IllegalStateException("باز کردن فایل ضبط ممکن نشد")
                } catch (e: Exception) {
                    context.contentResolver.delete(uri, null, null)
                    throw e
                }
                return PublicRecordingTarget(uri, null, descriptor)
            }

            // Android 6 تا 9: به علت محدودیت Scoped Storage مسیر عمومی بدون مجوز
            // ذخیره‌سازی قابل تضمین نیست؛ از پوشه اختصاصی برنامه استفاده می‌شود.
            val folder = context.getExternalFilesDir("Recordings")
                ?: File(context.filesDir, "Recordings")
            if (!folder.exists() && !folder.mkdirs()) {
                throw IllegalStateException("ساخت پوشه ضبط ممکن نشد")
            }
            return PublicRecordingTarget(null, File(folder, displayName), null)
        }
    }
}
