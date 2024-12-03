package `in`.mfayaz.camera2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream

fun imageToBitmap(image: Image): Bitmap {
    val planes = image.planes
    val buffer = planes[0].buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)

    val yuvImage = YuvImage(
        data,
        ImageFormat.NV21,
        image.width,
        image.height,
        null
    )

    val outputStream = ByteArrayOutputStream()
    yuvImage.compressToJpeg(
        android.graphics.Rect(0, 0, image.width, image.height),
        100,
        outputStream
    )
    val byteArray = outputStream.toByteArray()
    return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
}