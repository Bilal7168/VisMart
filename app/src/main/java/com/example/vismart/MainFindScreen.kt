package com.example.vismart

import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.PermissionChecker
import androidx.transition.Visibility
import com.example.vismart.databinding.ActivityMainBinding
import com.example.vismart.databinding.ActivityMainFindScreenBinding
import com.example.vismart.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.log

//The screen where we will launch the camera and work on it

class MainFindScreen : AppCompatActivity() {

    private lateinit var binding: ActivityMainFindScreenBinding;

    private var imageCapture: ImageCapture?=null;

    private var prod_name:String?=null;

    private lateinit var outputDirectory: File;

    private var request_code:Int?=null;

    var imageLocation: String?=null;

    private lateinit var textureView:TextureView;

    private lateinit var cameraManager:CameraManager;

    private lateinit var handler:Handler;

    private lateinit var cameraDevice:CameraDevice;

    private lateinit var imageView:ImageView;

    private lateinit var bitmap:Bitmap;

    private lateinit var model:SsdMobilenetV11Metadata1;

    private lateinit var imageProcessor:ImageProcessor;

    private val paint = Paint();

    private lateinit var labels:List<String>

    private var indicator:TextView?=null;

    private var indicatorStrip:RelativeLayout?=null;

    private var process_button:Button?=null;

    private val rectangle_arr:MutableList<RectF> = mutableListOf();

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainFindScreenBinding.inflate(layoutInflater);
        setContentView(binding.root)

        //hide the titlebar
        this.supportActionBar?.hide();

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                       WindowManager.LayoutParams.FLAG_FULLSCREEN)

        //get the product name
        prod_name = intent.getStringExtra("prod_name")

        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300,  ResizeOp.ResizeMethod.BILINEAR))
            .build()

        model = SsdMobilenetV11Metadata1.newInstance(this)

        labels = FileUtil.loadLabels(this, "labels.txt")

        indicator = findViewById(R.id.indicator)
        indicatorStrip = findViewById(R.id.indicatorStrip)

        process_button = findViewById(R.id.process)
        process_button!!.setOnClickListener{
            //call a function
            text_processor()
        }

        //NEW_CODE_CHANGE_IF_NO_WORK


//        outputDirectory = getOutputDirectory();
//
//        if (allPermissionGranted()){
//            Toast.makeText(this, "We have Permission", Toast.LENGTH_SHORT).show()
//            startCamera()
//        }
//        else{
//            ActivityCompat.requestPermissions(
//                    this, Constants.REQUIRED_PERMISSIONS,
//                    Constants.REQUEST_CODE_PERMISSIONS
//            )
//        }
//
//        binding.btnTakePhoto.setOnClickListener{
//            takePhoto();
//        }
        get_permission();
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)
        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera();
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false;
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap = textureView.bitmap!! //to be sent to the PreviewView



                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)
                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height;
                val w = mutable.width;

                paint.textSize = h/15f;
                paint.strokeWidth = h/85f;
                var x = 0;

                //empty the found rectangles array
                //rectangle_arr = rectangle_arr?.plus(RectF(5F, 5F, 5F, 5F));
                if(rectangle_arr!!.size > 0) {
                    rectangle_arr!!.clear()
                }

                scores.forEachIndexed {index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5){
                        paint.setColor(Color.BLUE)
                        paint.style = Paint.Style.STROKE
                        //add this to the rectangle_arr
                        rectangle_arr!!.add(RectF(
                            locations.get(x + 1),
                            locations.get(x),
                            locations.get(x + 3),
                            locations.get(x + 2)
                        ))
                        canvas.drawRect( //this draws the rectangles at a single moment
                            RectF(
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                locations.get(x + 3) * w,
                                locations.get(x + 2) * h
                            ), paint
                        );
                        paint.style = Paint.Style.FILL
                        canvas.drawText(
                            labels.get(
                                classes.get(index).toInt()
                            ) ,
                            locations.get(x + 1) * w,
                            locations.get(x) * h,
                            paint
                        )
                        safe_indicator_proximity(locations.get(x + 1) * w, locations.get(x + 3) * w, locations.get(x) * h, locations.get(x + 2) * h)
                        Log.d("The locations give :", " left -> ${(locations.get(x)*h)} and top -> ${locations.get(x)*h}" +
                                "and right -> ${locations.get(x+3)*w} and bottom -> ${locations.get(x+2)*h} and h is : $h and w is : $w and" +
                                "imageView.top : ${imageView.top} and imageView.bottom is: ${imageView.bottom}")
                    }
                }

                //TEST
//                paint.setColor(Color.GREEN)
//                paint.style = Paint.Style.STROKE
//                canvas.drawRect(RectF(0F,0F,100F,100F), paint)

                imageView.setImageBitmap(mutable);

            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private fun safe_indicator_proximity(left:Float, right:Float, top:Float, bottom:Float){
        //focus on a single object and calculate distance

        //the more the distance from left and right increases, the more unsafe it gets

        //if distance from left to right is lets say 2000 pixels, we want to define SAFE: left->250px, right->250px, Yellow: left-> >250px , right >250px
        //RED: right->>500px and left>>500px, so this would become a ratio to the distance
        //SAFE : left-> 250/2000 -> 0.125, right -> 0.125 , YELLOW: left-> <0.125 and similar for right and for RED: > 0.25 for left and right

        //if we have 3 4 objects in the shelf, of which none looks fine, we might want to denote RED and keep the screen RED
        //if one or more object have a left small but right not, it means they are at an end and might want to just be as it is, doesnt matter



        val margin_left = left;
        val margin_top = top;
        val margin_right = imageView.width - (right+left)
        val margin_bottom = imageView.height - (top+bottom)

        //calculate ratios
        val right_ratio = margin_right/imageView.width
        val left_ratio = margin_left/imageView.width
        val top_ratio = margin_top/imageView.height
        val bottom_ratio = margin_top/imageView.height

        if(right_ratio <= 0.25 && left_ratio <= 0.25 && top_ratio <= 0.25 && bottom_ratio <= 0.25){ //allowed till quarted length
            indicator!!.text = "Crystal Clear"
            indicatorStrip!!.setBackgroundColor(Color.GREEN)
        }
        else if(right_ratio <= 0.5 && left_ratio <= 0.5 && top_ratio <= 0.5 && bottom_ratio <= 0.5){
            indicator!!.text = "Normal - Med"
            indicatorStrip!!.setBackgroundColor(Color.YELLOW)
        }
        if(left_ratio > 0.35 && bottom_ratio > 0.35 ||
                right_ratio > 0.35 && bottom_ratio > 0.35 ||
                left_ratio > 0.35 && top_ratio > 0.35 ||
                right_ratio > 0.35 && top_ratio >0.35 ||
                left_ratio >0.35 && bottom_ratio > 0.35 && top_ratio >0.35 && right_ratio >0.35){ //extreme case
            indicator!!.text = "UnSafe"
            indicatorStrip!!.setBackgroundColor(Color.RED)
        }
    }

    private fun text_processor(){
        //start the new preview screen and send the coordinates of each rectangle to it
        //also send the taken bitmap
        var intent = Intent(this, ImagePreview::class.java)
        //save bitmap to file
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
        var dir = File(path)
        if(!dir.exists()){
            dir.mkdirs()
        }
        var image = File(dir, "temp.png" ) //one temporary file
        val fOut = FileOutputStream(image);

        if(bitmap.height > 0 && bitmap.width > 0) {
            var scaled_bmp =
                Bitmap.createScaledBitmap(bitmap, 700, 700, true)
            scaled_bmp.compress(Bitmap.CompressFormat.PNG, 85, fOut) //use the original unmutated bitmap
        }

        fOut.flush();
        fOut.close();
        intent.putExtra("bitmap_location", "$path/temp.png")
        intent.putParcelableArrayListExtra("detect_rects", ArrayList(rectangle_arr)) //the object bounding boxes
        intent.putExtra("prod_name", prod_name)
        startActivity(intent);
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close();
    }

    @SuppressLint("MissingPermission")
    private fun open_camera(){
        cameraManager.openCamera(cameraManager.cameraIdList[0], @SuppressLint("MissingPermission")
        object:CameraDevice.StateCallback(){
            override fun onDisconnected(p0: CameraDevice) {

            }

            override fun onError(p0: CameraDevice, p1: Int) {

            }

            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0;

                var surfaceTexture = textureView.surfaceTexture;
                var surface = Surface(surfaceTexture)

                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigureFailed(p0: CameraCaptureSession) {

                    }

                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                 }, handler)
            }

        }, handler)
    }

    //NEW_CODE_CHANGE IF NO WORK
    fun get_permission(){
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            get_permission()
        }
    }


    /////--------------

//    private fun getOutputDirectory(): File{
//        val mediaDir = externalMediaDirs.firstOrNull()?.let {mFile ->
//            File(mFile, resources.getString(R.string.app_name)).apply{
//                mkdirs();
//            }
//        }
//
//        return if (mediaDir != null && mediaDir.exists())
//            mediaDir else filesDir
//    }
//
//    private fun takePhoto(){ //this is the process button
//        // Get a stable reference of the modifiable image capture use case
//        var imageCapture = imageCapture ?: return
//
//        // Create time stamped name and MediaStore entry.
//        val name = SimpleDateFormat(Constants.FILE_NAME_FORMAT, Locale.US)
//            .format(System.currentTimeMillis())
//        imageLocation = name;
//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
//            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
//                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
//            }
//        }
//
//        // Create output options object which contains file + metadata
//        val outputOptions = ImageCapture.OutputFileOptions
//            .Builder(contentResolver,
//                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//                contentValues)
//            .build()
//
//        // Set up image capture listener, which is triggered after photo has
//        // been taken
//        Log.d("Image", imageCapture.toString())
//        imageCapture.takePicture(
//            outputOptions,
//            ContextCompat.getMainExecutor(this@MainFindScreen),
//            object : ImageCapture.OnImageSavedCallback {
//                override fun onError(exc: ImageCaptureException) {
//                    Log.e(Constants.TAG, "Photo capture failed: ${exc.message}", exc)
//                }
//
//                override fun
//                        onImageSaved(output: ImageCapture.OutputFileResults){
//                    val msg = "Photo capture succeeded: ${output.savedUri}" //we will use this image Location to send to rest api
//                    // and get log
//                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                    Log.d(Constants.TAG, msg)
//
//                    //call function for image still
//                    showImage();
//
//
//
//                }
//            }
//        )
//        //not here because we want to wait for the time the image shows
//    }
//
//    private fun showImage(){
//
//        val intent = Intent(this, ImagePreview::class.java)
//        intent.putExtra("image_name", imageLocation)
//        //put the product name for check
//        intent.putExtra("prod_name", prod_name)
//        startActivity(intent);
//
//    }
//
//
//    private fun startCamera(){
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this);
//
//        cameraProviderFuture.addListener({
//
//            val cameraProvider : ProcessCameraProvider = cameraProviderFuture.get();
//
//            val preview = Preview.Builder()
//                .build()
//                .also { it.setSurfaceProvider(
//                        binding.ViewFinder.surfaceProvider
//                    )
//                }
//
//
//            imageCapture = ImageCapture.Builder()
//                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
//                .setJpegQuality(100)
//                .setTargetResolution(Size(1100,1100))
//                .build()
//
//            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//
//            try{
//
//                cameraProvider.unbindAll()
//
//                cameraProvider.bindToLifecycle(
//                    this, cameraSelector,
//                    preview, imageCapture
//                )
//
//                Toast.makeText(this, "INSIDE THE CAMERA bud", Toast.LENGTH_SHORT).show()
//            }
//            catch(e:Exception) {
//                Log.d(Constants.TAG, "Start Camera Fail: ", e);
//            }
//        }, ContextCompat.getMainExecutor(this))
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String>,
//        grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
//            //write our code here
//            if (allPermissionGranted()) {
//                startCamera();
//            } else {
//                Toast.makeText(this, "Permission not granted by user", Toast.LENGTH_SHORT).show()
//                finish();
//            }
//        }
//    }
//
//    private fun allPermissionGranted() =
//            Constants.REQUIRED_PERMISSIONS.all{
//                ContextCompat.checkSelfPermission(
//                    baseContext, it
//                ) == PackageManager.PERMISSION_GRANTED
//            }

}