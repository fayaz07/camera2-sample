package `in`.mfayaz.camera2

import android.media.ImageReader
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    imageReader: StateFlow<ImageReader?>
) {
    val image = imageReader.collectAsState().value

    Column(modifier = modifier) {
        if (image != null) {
            val latestImage = image.acquireLatestImage()
            val bitmap = imageToBitmap(latestImage)

            Box(
                modifier = Modifier.fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(Color.Blue)
            ) {
                Image(
                    bitmap.asImageBitmap(),
                    "",
                    modifier = Modifier.fillMaxSize()
                        .fillMaxHeight(0.5f)
                )
            }
        } else {
            Box {
                Text("Image is null")
            }
        }
    }
}
