class MainActivity : AppCompatActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Permissions are all granted, start the background service
            startBackgroundService()
        } else {
            // Handle denied permissions (guide user to settings or display a prompt)
            Toast.makeText(this, "Camera, microphone, and location permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. First request basic permissions
        val basicPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        // Check if already granted
        if (hasPermissions(basicPermissions)) {
            requestBackgroundLocation() // Next, request background location
        } else {
            requestPermissionsLauncher.launch(basicPermissions)
        }
    }

    // Background location must be requested separately and requires guiding the user to select "Always allow" in system settings
    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundPerm = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (!hasPermissions(backgroundPerm)) {
                // Note: The background location popup may not appear directly here, it is recommended to use Settings.ACTION_APPLICATION_DETAILS_SETTINGS to guide users
                requestPermissionsLauncher.launch(backgroundPerm)
            } else {
                startBackgroundService()
            }
        } else {
            startBackgroundService()
        }
    }

    private fun startBackgroundService() {
        val intent = Intent(this, BackgroundMediaService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun hasPermissions(perms: Array<String>): Boolean {
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }
}