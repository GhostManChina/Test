dependencies {
    // CameraX core
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    
    // Kotlin Coroutines (for background threads)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // Location services (optional, using FusedLocationProvider)
    implementation("com.google.android.gms:play-services-location:21.2.0")
}