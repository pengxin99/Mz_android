package com.example.xinye.camera6_6;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import CNN_JAVA.cnn;

public class MainActivity extends AppCompatActivity {

    private ImageView img_64;
    private TextView textView_result;
    private SurfaceView SurfaceView_preview;

    private SurfaceHolder mSurfaceViewHolder;
    private CameraDevice mCameraDevice;         // surfaceview销毁时，销毁摄像头用
    private String mCameraId;                   // 存储摄像机信息
    private ImageReader mImageReader;           //
    private CameraManager mCameraManager;       // 相机控制

    private Handler mHandler;                   // 初始化相机并且预览时需要的线程
    private Handler mainHandler;

    private CaptureRequest.Builder mPreviewBuilder;     // capturesession创建
    private CameraCaptureSession mSession;

    private int pictureId = 0 ;
    double[][] ImageMatrix = new double[64][64] ;

    //在类里声明一个Handler
    Handler mTimeHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (msg.what == 0) {
//                textView_result.setText("woshi :" + pictureId);
                pictureId ++ ;
                takePicture();
//                String takepicinfo = ownTakePic(mImageReader);

                sendEmptyMessageDelayed(0, 1000);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // 定义控件直接的链接
        img_64 = (ImageView)findViewById(R.id.imageview1);
        textView_result = (TextView)findViewById(R.id.tv1);
        SurfaceView_preview = (SurfaceView)findViewById(R.id.SurfaceView1);

        // 初始化SurfaceView
        Log.d("################ 1", "initSurfaceView!");
        initSurfaceView();

        mTimeHandler.sendEmptyMessageDelayed(0, 1000);
    }

    /**
     * 初始化 SurfaceView
     */
    public void initSurfaceView(){

        SurfaceView_preview = (SurfaceView)findViewById(R.id.SurfaceView1);
        // 通过SurfaceViewHolder可以对SurfaceView进行管理
        mSurfaceViewHolder = SurfaceView_preview.getHolder();

        // 添加surfaceviewHolder的回调函数
        // 如果surface创建成功，则初始化摄像头并预览； 如果surface销毁，则关闭摄像头；
        mSurfaceViewHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                // 创建成功，初始化摄像头
                Log.d("################ 2", "initCameraAndPreview!");

                initCameraAndPreview();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                //释放camera
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            }
        });
        Toast.makeText(this, "相机和预览初始化完毕！！", Toast.LENGTH_LONG).show();
    }


    public void initCameraAndPreview(){
        // 使用HandlerThread线程进行多线程操作
        HandlerThread handlerThread = new HandlerThread("My First Camera2");
        handlerThread.start();

        mHandler = new Handler(handlerThread.getLooper());      // mhandler线程
        mainHandler = new Handler(getMainLooper());             // 用来处理ui线程的handler，即ui线程
        Toast.makeText(this, "开始调用摄像机！！", Toast.LENGTH_LONG).show();
        try{
            // 后置摄像头ID
            mCameraId = "" + CameraCharacteristics.LENS_FACING_FRONT;
            // mImageReader 是 ImageReader 对象，这里进行实例化，传入surfaceview的大小
            mImageReader = ImageReader.newInstance(SurfaceView_preview.getWidth(),
                    SurfaceView_preview.getHeight(), ImageFormat.JPEG,27);
            // 设置 ImageReader 的图片监听，
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);

            // 获得CameraManager实例
            mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
                // 按理说这里应该有一个申请权限的过程，但为了使程序尽可能最简化，所以先不添加
            }
            Log.d("################ 3", "openCamera and receive the deviceStateCallback!");
            // 获取了摄像机权限后，开启摄像机； 传入deviceStateCallback回调函数(开启摄像机成功，则开始预览)，及mHandler主功能进程
            mCameraManager.openCamera(mCameraId, deviceStateCallback, null);
        }catch (CameraAccessException e){
            Toast.makeText(this, "未获取摄像机权限！！", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 定义ImageReader的监听实例， 在 ImageReader 的监听中对图片进行处理
     */
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {

            Image image = imageReader.acquireNextImage();
            Log.d("################## PreviewListener", "GetPreviewImage");
            if (image == null){
                Log.d("################## ImageReader ", "image == null");
                return;
            }

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);                              // 将image对象转化为byte，再转化为bitmap
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            if (bitmap != null) {
                // 缩放后显示
                Bitmap resizedBitmap =  zoomBitmap(bitmap, 64,64) ;
                bytes = Bitmap2Bytes(resizedBitmap) ;
                img_64.setImageBitmap(bitmap);
//                String res = "";
//
//                for (int i = 0; i<bytes.length;i++){
//                    res =res +  (bytes[i]+128) + "*";
//                }
                int width = resizedBitmap.getWidth();
                int heigth = resizedBitmap.getHeight();
//                textView_result.setText("woshi :" + res);

                textView_result.setText("width is: " + width + "heigth is: " + heigth);
                String res = "";
                int grey = 0;
                // 这里将 64*64 的ARGB的bitmap图转换为灰度图，存入矩阵
                for (int i =0;i <resizedBitmap.getWidth(); i++){
                    for (int j = 0; j<resizedBitmap.getWidth(); j++) {
                        grey = RGB2Grey(Color.red(resizedBitmap.getPixel(i, j)),
                                Color.green(resizedBitmap.getPixel(i, j)),
                                Color.blue(resizedBitmap.getPixel(i, j)));
                        res = res + "  " + grey;
                        ImageMatrix[i][j] = grey;
                    }
                }
                // 开始执行CNN算法进行分类检测
                try {
                    Log.d("################ final", "calculate cnn!");
                    String output = cnn.run_cnn(MainActivity.this,ImageMatrix);
                    textView_result.setText(output);
                }catch (Exception e){
                    e.printStackTrace();
                }

          }
        }
    };

    /**
     *   bitmap 缩放
     */
    public Bitmap zoomBitmap(Bitmap bitmap, int width, int height) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Matrix matrix = new Matrix();
        float scaleWidth = ((float) width / w);
        float scaleHeight = ((float) height / h);
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap newbmp = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
        return newbmp;
    }

    /**
     *      bitmap 转 bytes
     */
    public byte[] Bitmap2Bytes(Bitmap bm) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
        return baos.toByteArray();
    }

    /**
     * 单个像素，RGB转grey
     * @param R
     * @param G
     * @param B
     * @return 0-255的图像灰度值
     */
    public int RGB2Grey(int R, int G ,int B){
        return (R*38 + G*75 + B*15) >> 7;
    }

    /**
     * 相机状态的回调函数：1、相机打开，则进行预览； 2、相机无连接，则关闭相机； 3、出错，发出Toast信息
     */
    private CameraDevice.StateCallback deviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            try{

                Log.d("################ 4", "camera onOpened and then to takePreview!");
                takePreview();
                Toast.makeText(MainActivity.this,"打开摄像头成功", Toast.LENGTH_LONG).show();

            }catch (CameraAccessException e){
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            if (mCameraDevice != null){
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Toast.makeText(MainActivity.this,"打开摄像头出错", Toast.LENGTH_LONG).show();
        }
    };


    /**
     * 相机预览函数，mImageReader 是 captureSession 和 SurfaceView 的接口
     * @throws CameraAccessException
     */
    public void takePreview() throws CameraAccessException {
        // mPreviewBuilder是前文讲过的CaptureRequest的Builder(Builder设计模式），用来对CaptureRequest进行编辑。
        // 这里有个参数——CameraDevice.TEMPLATE_PREVIEW：看名字可以猜到，这是表明这个request是针对于相机预览界面的。
        mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        // 将request设置的参数数据应用于SurfaceView对应的surface，request设置的参数形成的图像数据都会保存在SurfaceView的surface中。
        mPreviewBuilder.addTarget(mSurfaceViewHolder.getSurface());

        // 在安卓端和相机硬件端建立通道，
        mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceViewHolder.getSurface(),
                mImageReader.getSurface()),
                mSessionPreviewStateCallback,
                null);
        //************************* 做算法，检测
//        mImageReader.setOnImageAvailableListener(doDetective,mHandler);


//        Bitmap bitmap1 = SurfaceView_preview.getDrawingCache();
//        Log.d("################ 5 ", "bitmap from surfaceview!");
//        img_64.setImageBitmap(bitmap1);

    }


    /**
     * 创建session的一个回调，对session建立成功和失败的情况进行处理
     */
    private CameraCaptureSession.StateCallback mSessionPreviewStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mSession = session;
            // 配置完毕开始预览
            try {
                /**
                 * 设置你需要配置的参数
                 */
                // 自动对焦
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // 打开闪光灯
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                // 无限次的重复获取图像
//                mSession.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);
                mSession.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Toast.makeText(MainActivity.this, "CameraCaptureSession配置失败", Toast.LENGTH_SHORT).show();
        }
    };


    public void takePicture() {
        try {
            // 参数 CameraDevice.TEMPLATE_STILL_CAPTURE 是用来设置拍照请求的request
            CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            // 使图片做顺时针旋转
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation(cameraCharacteristics, rotation));
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            mSession.capture(mCaptureRequest, null, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取图片应该旋转的角度，使图片竖直
     * @param rotation
     * @return
     */
    public int getOrientation(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0:
                return 90;
            case Surface.ROTATION_90:
                return 0;
            case Surface.ROTATION_180:
                return 270;
            case Surface.ROTATION_270:
                return 180;
            default:
                return 0;
        }
    }

    /**
     * 获取图片应该旋转的角度，使图片竖直，来自Google官网
     * @param c
     * @param deviceOrientation
     * @return
     */
    private int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN)
            return 0;
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // LENS_FACING相对于设备屏幕的方向,LENS_FACING_FRONT相机设备面向与设备屏幕相同的方向
        boolean facingFront = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return jpegOrientation;
    }





}
