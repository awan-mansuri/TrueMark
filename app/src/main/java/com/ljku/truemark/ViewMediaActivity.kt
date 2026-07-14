package com.ljku.truemark

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.imageview.ShapeableImageView
import java.io.File

class ViewMediaActivity : AppCompatActivity() {

    private lateinit var imageView: ShapeableImageView
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private var startX = 0f
    private var startY = 0f
    private var isScaling = false
    private var lastTouchTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_media)

        imageView = findViewById(R.id.fullImageView)
        val closeBtn: android.widget.ImageView = findViewById(R.id.closeMediaBtn)

        // Initialize scale gesture detector
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        val mediaUri = intent.getStringExtra("MEDIA_URI")
        val mediaType = intent.getStringExtra("MEDIA_TYPE")

        Log.d("ViewMedia", "Received mediaUri: $mediaUri")
        Log.d("ViewMedia", "Received mediaType: $mediaType")

        if (mediaUri == null) {
            Log.e("ViewMedia", "Media URI is null, finishing activity")
            finish()
            return
        }

        if (mediaType == "IMAGE") {
            imageView.visibility = android.view.View.VISIBLE
            var imageLoaded = false
            
            try {
                // Handle base64 encoded images
                if (mediaUri.startsWith("data:image/")) {
                    Log.d("ViewMedia", "Loading base64 image")
                    val base64Data = mediaUri.substring(mediaUri.indexOf(",") + 1)
                    val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        imageLoaded = true
                        Log.d("ViewMedia", "Base64 image loaded successfully")
                    } else {
                        Log.e("ViewMedia", "Failed to decode base64 image")
                    }
                } else {
                    Log.d("ViewMedia", "Loading URI image: $mediaUri")
                    // Try direct URI first
                    try {
                        imageView.setImageURI(Uri.parse(mediaUri))
                        imageLoaded = true
                        Log.d("ViewMedia", "URI image loaded successfully")
                    } catch (e: Exception) {
                        Log.e("ViewMedia", "Failed to load URI image: ${e.message}")
                        
                        // Try file path as fallback
                        val imgFile = File(mediaUri)
                        if (imgFile.exists()) {
                            val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                            if (bitmap != null) {
                                imageView.setImageBitmap(bitmap)
                                imageLoaded = true
                                Log.d("ViewMedia", "File path image loaded successfully")
                            } else {
                                Log.e("ViewMedia", "Failed to decode file path image")
                            }
                        } else {
                            Log.e("ViewMedia", "File does not exist: ${imgFile.absolutePath}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewMedia", "Error loading image: ${e.message}", e)
            }
            
            if (!imageLoaded) {
                Log.e("ViewMedia", "Image failed to load, closing activity")
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        closeBtn.setOnClickListener { finish() }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            // Let the scale gesture detector handle the touch events first
            val wasScaleHandled = scaleGestureDetector.onTouchEvent(it)
            
            when (it.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    startX = it.x - imageView.translationX
                    startY = it.y - imageView.translationY
                    lastTouchTime = System.currentTimeMillis()
                    isScaling = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scaleFactor > 1.0f && !wasScaleHandled) {
                        // Only handle pan if not scaling and image is zoomed
                        imageView.translationX = it.x - startX
                        imageView.translationY = it.y - startY
                        isScaling = true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val currentTime = System.currentTimeMillis()
                    val touchDuration = currentTime - lastTouchTime
                    
                    // Reset position if scale is 1.0
                    if (scaleFactor == 1.0f) {
                        imageView.translationX = 0f
                        imageView.translationY = 0f
                    }
                    
                    isScaling = false
                }
            }
        }
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(1.0f, 5.0f)
            
            imageView.scaleX = scaleFactor
            imageView.scaleY = scaleFactor
            
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            // Delay setting isScaling to false to allow for smooth interaction
            imageView.postDelayed({ isScaling = false }, 100)
        }
    }
}
