class BackgroundMediaService : LifecycleService() {

    private lateinit var cameraProvider: ProcessCameraProvider
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Used to store the captured data to a file
    private lateinit var outputFile: File

    override fun onCreate() {
        super.onCreate()
        // Critical: Must call startForeground immediately after Service creation, otherwise it will crash
        startAsForeground()
        
        // Initialize storage path (it is recommended to use MediaStore or SAF in production, here using app-private directory for demonstration to avoid permission issues)
        outputFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "bg_capture_${System.currentTimeMillis()}.mp4")
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, "bg_service_channel")
            .setContentTitle("Background Service Running")
            .setContentText("Accessing camera and microphone")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true) // Non-dismissible
            .build()

        // Create notification channel (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("bg_service_channel", "Background Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // Use the correct service type to start foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or 
                                              ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                                              ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        // Start camera capture
        startCameraCapture()
        // Start microphone recording
        startAudioRecording()
        
        return START_STICKY
    }

    // --- Camera Capture Logic (CameraX, No UI) ---
    private fun startCameraCapture() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            // Only bind ImageCapture, no Preview required
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                // Bind lifecycle to the Service itself
                cameraProvider.bindToLifecycle(
                    this, 
                    CameraSelector.DEFAULT_BACK_CAMERA, 
                    imageCapture
                )
                
                // Example: Capture one photo every 5 seconds
                serviceScope.launch {
                    while (isActive) {
                        delay(5000)
                        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "img_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this@BackgroundMediaService), object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                Log.d("BgService", "Photo saved: ${photoFile.absolutePath}")
                            }
                            override fun onError(exc: ImageCaptureException) {
                                Log.e("BgService", "Photo capture failed", exc)
                            }
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e("BgService", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // --- Microphone Recording Logic (AudioRecord) ---
    private fun startAudioRecording() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        // Permission check (safety measure)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("BgService", "AudioRecord initialization failed")
            return
        }

        isRecording = true
        audioRecord?.startRecording()

        // Read audio data in a background thread (for demonstration, only read without writing to file to save space, production environment needs to write to file or stream)
        serviceScope.launch {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // Data is in buffer, can encode to file or stream here
                    // Note: Directly writing PCM files is relatively large, production environments typically use MediaCodec to encode to AAC
                }
            }
        }
    }

    override fun onDestroy() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        cameraProvider.unbindAll()
        serviceScope.cancel()
        super.onDestroy()
    }
}