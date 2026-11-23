package com.example.galaxylut

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var texture: TextureView
    private lateinit var status: TextView
    private lateinit var lensButton: Button
    private lateinit var lutButton: Button
    private lateinit var captureButton: Button
    private lateinit var cameraManager: CameraManager
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSize = android.util.Size(1920,1080)
    private var selectedIndex = 0
    private var cameras: List<CameraInfo> = emptyList()
    private var lut: CubeLut? = null
    private var lutName = "No LUT"
    private val lock = Semaphore(1)

    data class CameraInfo(val id:String, val focal:Float, val label:String, val physicalIds:Set<String>)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        buildUi()
        enumerateCameras()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCameraThread()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), 10)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(0xFF080A08.toInt()); setPadding(18,18,18,18) }
        val header = TextView(this).apply { text="GALAXY LUT CAMERA  /  ALPHA"; textSize=17f; setTextColor(0xFFB7F34A.toInt()); setPadding(4,8,4,14) }
        root.addView(header)
        texture = TextureView(this).apply { surfaceTextureListener=surfaceListener }
        root.addView(texture, LinearLayout.LayoutParams(-1,0,1f))
        status = TextView(this).apply { text="Đang kiểm tra camera…"; textSize=12f; setTextColor(0xFFCCCCCC.toInt()); setPadding(4,12,4,12) }
        root.addView(status)
        val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; weightSum=3f }
        lensButton = makeButton("LENS") { cycleLens() }
        lutButton = makeButton("IMPORT LUT") { pickLut() }
        captureButton = makeButton("●") { takePhoto() }
        row.addView(lensButton, LinearLayout.LayoutParams(0,58,1f).apply { setMargins(3,3,3,3) })
        row.addView(lutButton, LinearLayout.LayoutParams(0,58,1f).apply { setMargins(3,3,3,3) })
        row.addView(captureButton, LinearLayout.LayoutParams(0,58,1f).apply { setMargins(3,3,3,3) })
        root.addView(row)
        val note = TextView(this).apply { text="LUT được áp vào JPEG sau khi chụp. RAW/DNG và tích hợp Samsung Camera chưa có trong bản alpha."; textSize=11f; setTextColor(0xFF888888.toInt()); setPadding(4,10,4,2) }
        root.addView(note)
        setContentView(root)
    }

    private fun makeButton(label:String, action:()->Unit)=Button(this).apply {
        text=label; textSize=11f; setTextColor(0xFFB7F34A.toInt()); setBackgroundColor(0xFF20251D.toInt()); setOnClickListener { action() }
    }

    private fun enumerateCameras() {
        val found=mutableListOf<CameraInfo>()
        try {
            for(id in cameraManager.cameraIdList) {
                val c=cameraManager.getCameraCharacteristics(id)
                if(c.get(CameraCharacteristics.LENS_FACING)!=CameraCharacteristics.LENS_FACING_BACK) continue
                val focal=c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(0f)
                val f=focal.firstOrNull() ?: 0f
                val label=when { f<2.0f -> "0.6x / Ultra-wide"; f<5.0f -> "1x / Main"; else -> "3x / Tele" }
                found.add(CameraInfo(id,f,label,c.physicalCameraIds))
            }
        } catch (_:Exception) {}
        cameras=found.sortedBy { it.focal }
        if(cameras.isEmpty()) status.text="Không tìm thấy camera sau qua Camera2 API."
        else updateStatus()
    }

    private fun startCameraThread() { cameraThread=HandlerThread("GalaxyLUT-Camera").also { it.start(); cameraHandler=Handler(it.looper) }; if(texture.isAvailable) openCamera() }
    private val surfaceListener=object:TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface:SurfaceTexture,width:Int,height:Int) { if(cameraHandler==null && checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCameraThread() else openCamera() }
        override fun onSurfaceTextureSizeChanged(surface:SurfaceTexture,width:Int,height:Int) { }
        override fun onSurfaceTextureDestroyed(surface:SurfaceTexture)=true
        override fun onSurfaceTextureUpdated(surface:SurfaceTexture) { }
    }

    private fun openCamera() {
        if(!texture.isAvailable || cameras.isEmpty() || checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) return
        closeCamera()
        val info=cameras[selectedIndex.coerceIn(0,cameras.lastIndex)]
        try {
            cameraManager.openCamera(info.id, object:CameraDevice.StateCallback() {
                override fun onOpened(camera:CameraDevice) { device=camera; createPreviewSession() }
                override fun onDisconnected(camera:CameraDevice) { camera.close(); device=null }
                override fun onError(camera:CameraDevice,error:Int) { camera.close(); device=null; runOnUiThread { status.text="Camera lỗi: $error (ID ${info.id})" } }
            },cameraHandler)
        } catch(e:Exception) { status.text="Không mở được camera ${info.id}: ${e.message}" }
        updateStatus()
    }

    private fun createPreviewSession() {
        val camera=device ?: return
        val st=texture.surfaceTexture ?: return
        st.setDefaultBufferSize(previewSize.width,previewSize.height)
        val preview=Surface(st)
        val characteristics=cameraManager.getCameraCharacteristics(camera.id)
        val map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSize=map?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.maxByOrNull { it.width.toLong()*it.height } ?: android.util.Size(4000,3000)
        imageReader=ImageReader.newInstance(jpegSize.width,jpegSize.height,android.graphics.ImageFormat.JPEG,2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image=reader.acquireNextImage() ?: return@setOnImageAvailableListener
            val buffer=image.planes[0].buffer; val bytes=ByteArray(buffer.remaining()); buffer.get(bytes); image.close()
            cameraHandler?.post { processAndSave(bytes) }
        },cameraHandler)
        try {
            camera.createCaptureSession(listOf(preview,imageReader!!.surface),object:CameraCaptureSession.StateCallback() {
                override fun onConfigured(s:CameraCaptureSession) { session=s; try { val req=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(preview); set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO) }; s.setRepeatingRequest(req.build(),null,cameraHandler) } catch(e:Exception) { status.text="Preview error: ${e.message}" } }
                override fun onConfigureFailed(s:CameraCaptureSession) { status.text="Không cấu hình được preview." }
            },cameraHandler)
        } catch(e:Exception) { status.text="Session error: ${e.message}" }
    }

    private fun takePhoto() {
        val cam=device ?: return; val s=session ?: return; val reader=imageReader ?: return
        try {
            val req=cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface); set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.JPEG_QUALITY,98.toByte())
            }
            s.capture(req.build(),object:CameraCaptureSession.CaptureCallback(){},cameraHandler)
            status.text="Đang chụp…"
        } catch(e:Exception) { status.text="Chụp lỗi: ${e.message}" }
    }

    private fun processAndSave(bytes:ByteArray) {
        try {
            var bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: throw Exception("Không đọc được JPEG")
            lut?.let { bitmap=it.apply(bitmap) }
            val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"GLUT_${System.currentTimeMillis()}.jpg"); put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg"); if(Build.VERSION.SDK_INT>=29) put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/GalaxyLUTCamera") }
            val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: throw Exception("Không tạo được file ảnh")
            contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,98,out) }
            runOnUiThread { status.text="Đã lưu ảnh · ${cameras.getOrNull(selectedIndex)?.label ?: "Camera"} · $lutName" }
        } catch(e:Exception) { runOnUiThread { status.text="Xử lý ảnh lỗi: ${e.message}" } }
    }

    private fun pickLut() {
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="*/*" }
        startActivityForResult(intent, 22)
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==22 && resultCode==RESULT_OK) data?.data?.let { loadLut(it) }
    }

    private fun loadLut(uri:Uri) {
        try {
            val text=contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw Exception("Không đọc được file")
            lut=CubeLut.parse(text); lutName=uri.lastPathSegment?.substringAfterLast('/') ?: "Custom LUT"; updateStatus()
        } catch(e:Exception) { status.text="LUT không hợp lệ: ${e.message}" }
    }

    private fun cycleLens() { if(cameras.size<2) { status.text="Camera2 chỉ công bố ${cameras.size} camera sau trên máy này."; return }; selectedIndex=(selectedIndex+1)%cameras.size; openCamera() }
    private fun updateStatus() { runOnUiThread { val c=cameras.getOrNull(selectedIndex); status.text="${c?.label ?: "No camera"} · ID ${c?.id ?: "-"} · $lutName · ${cameras.size} camera sau phát hiện"; lensButton.text=c?.label ?: "LENS"; lutButton.text=if(lut==null) "IMPORT LUT" else "LUT ✓" } }

    private fun closeCamera() { try { session?.close(); session=null; device?.close(); device=null; imageReader?.close(); imageReader=null } catch(_:Exception){} }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) { super.onRequestPermissionsResult(requestCode,permissions,grantResults); if(requestCode==10 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) startCameraThread() else status.text="Cần quyền camera để sử dụng." }
    override fun onResume() { super.onResume(); if(cameraThread==null && checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED && texture.isAvailable) startCameraThread() }
    override fun onPause() { closeCamera(); super.onPause() }
    override fun onDestroy() { cameraThread?.quitSafely(); cameraThread=null; cameraHandler=null; super.onDestroy() }
}
