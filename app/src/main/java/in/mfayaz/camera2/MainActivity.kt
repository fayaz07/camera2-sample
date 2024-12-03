package `in`.mfayaz.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import `in`.mfayaz.camera2.databinding.ActivityMainBinding
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class MainActivity : ComponentActivity() {

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var availableCamera: CameraData? = null

    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private lateinit var session: CameraCaptureSession

    private val imageState = MutableStateFlow<ImageReader?>(null)

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d(TAG, "onCreate")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initCamera()

//        surfaceView = AutoFitSurfaceView(applicationContext)
//        setContent {
//            Camera2sampleTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
////                    CameraView(
////                        modifier = Modifier.padding(innerPadding),
////                        imageState as StateFlow<ImageReader?>
////                    )
//                    Column(
//                        modifier = Modifier.padding(innerPadding)
//                    ) {
//                        AndroidView(
//                            factory = {
//                                surfaceView
//                            },
//                            update = { view ->
//
//                            }
//                        )
//                    }
//                }
//            }
//        }
    }

    private fun initCamera() {
        Log.d(TAG, "fetching available cameras list")
        enumerateCameras(cameraManager)
        if (availableCamera == null) {
            Toast.makeText(applicationContext, "Oops! No camera found", Toast.LENGTH_LONG).show()
            return
        }

        availableCamera?.let { c ->
            setupSurfaceView {
                afterSurfaceIsSetup(c)
            }
        }
    }

    private fun afterSurfaceIsSetup(c: CameraData) {
        lifecycleScope.launch {
            Log.d(TAG, "initializing camera")

            camera = openCamera(cameraManager, c.cameraId, cameraHandler)

            Log.d(TAG, "camera initialized")

            // Initialize an image reader which will be used to capture still photos
            val size = c.characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                .getOutputSizes(availableCamera!!.format)
                .maxByOrNull { it.height * it.width }!!

            binding.surfaceView.setAspectRatio(
                size.width,
                size.height
            )

            imageReader =
                ImageReader.newInstance(size.width, size.height, c.format, IMAGE_BUFFER_SIZE)

            imageReader.setOnImageAvailableListener(OnImgAvailable(), cameraHandler)

            val targets = listOf(imageReader.surface, binding.surfaceView.holder.surface)

            // Start a capture session using our open camera and list of Surfaces where frames will go
            session = createCaptureSession(camera, targets, cameraHandler)

            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply { addTarget(binding.surfaceView.holder.surface) }

            // This will keep sending the capture request as frequently as possible until the
            // session is torn down or session.stopRepeating() is called
            session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
        }
    }

    private fun setupSurfaceView(function: () -> Unit) {
        binding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                // Selects appropriate preview size and configures view finder
                val previewSize = getPreviewOutputSize(
                    binding.surfaceView.display,
                    availableCamera!!.characteristics,
                    SurfaceHolder::class.java
                )
//                Log.d(TAG, "View finder size: ${fragmentCameraBinding.viewFinder.width} x ${fragmentCameraBinding.viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                binding.surfaceView.setAspectRatio(
                    previewSize.width,
                    previewSize.height
                )

                function()
            }
        })
    }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    inner class OnImgAvailable : OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            Log.d(TAG, "image available brother")
            imageState.update { reader }
        }
    }

    /** Helper class used as a data holder for each selectable camera format item */
    private data class CameraData(
        val cameraId: String,
        val format: Int,
        val characteristics: CameraCharacteristics
    )

    /** Helper function used to list all compatible cameras and supported pixel formats */
    private fun enumerateCameras(cameraManager: CameraManager) {
        // Get list of all compatible cameras
        cameraManager.cameraIdList.forEach {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            val capabilities =
                characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)

            capabilities?.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
            ) ?: false
            if (capabilities != null) {
                handleCameraCapabilities(it, capabilities, characteristics)
            }
            if (availableCamera != null) return@forEach
        }
    }

    private fun handleCameraCapabilities(
        cameraId: String, capabilities: IntArray, characteristics: CameraCharacteristics
    ) {
        if (!capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
            return
        }

        if (characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
            return
        }

        val outputFormats = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )!!.outputFormats

        // All cameras *must* support JPEG output so we don't need to check characteristics
        var format = ImageFormat.JPEG

        // Return cameras that support RAW capability
        if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
            && outputFormats.contains(ImageFormat.RAW_SENSOR)
        ) {
            format = ImageFormat.RAW_SENSOR
        }

        // Return cameras that support JPEG DEPTH capability
        if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)
            && outputFormats.contains(ImageFormat.DEPTH_JPEG)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            format = ImageFormat.DEPTH_JPEG
        }
        availableCamera = CameraData(cameraId, format, characteristics)
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager, cameraId: String, handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    companion object {
        const val TAG = "main-act"

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 1
    }
}
