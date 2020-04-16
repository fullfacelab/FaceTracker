package br.com.wdnascimento.facetracker;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import br.com.wdnascimento.facetracker.camera.CameraSourcePreview;
import br.com.wdnascimento.facetracker.camera.GraphicOverlay;


/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */

public class FaceTrackerActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private LinearLayout face_mascara;

    // Handle
    private static final int RC_HANDLE_GMS = 9001;

    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */

    private boolean on, take;
    //private TextView textContador;

    // close Eye
    private static final float EYE_CLOSED_THRESHOLD = 0.30f;

    // set state
    private int state = 0;
    private ImageView imageCropped;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_face_tracker);

        mPreview        = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay)      findViewById(R.id.faceOverlay);
        imageCropped    = (ImageView) findViewById(R.id.imageCropped);

        // verifica as permissoes (Uses Permission)
        boolean camera = PermissionUtils.checkPermission(FaceTrackerActivity.this, Manifest.permission.CAMERA);
        if (camera){
            //criar a camera
            createCameraSource();
            startCameraSource();
        }
        else{
            //request permission camera
            PermissionUtils.validate(FaceTrackerActivity.this,2,Manifest.permission.CAMERA);
        }

        // pega as Views do Layout
        //textContador  = (TextView)     findViewById(R.id.textContador);
        face_mascara  = (LinearLayout) findViewById(R.id.face_mascara);
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setTrackingEnabled(true)
                .setMode(FaceDetector.ACCURATE_MODE)
                .setProminentFaceOnly(true)
                .setMinFaceSize(0.30f)
                .build();

        FaceDetectorFrame faceDetectorFrame = new FaceDetectorFrame(detector);

        faceDetectorFrame.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, faceDetectorFrame)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .setAutoFocusEnabled(true)
                .build();
    }

    @Override
    protected void onStart(){
        super.onStart();
        on = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            on = false;
            take = true;
        }
        catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
        on = false;
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCameraSource();
        on = false;
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            startCameraSource();
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            }
            catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
        else {
            createCameraSource();
            startCameraSource();
        }
    }

    /**
     * Metodo stop camera
     */
    private void stopCameraSource() {
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource = null;
        }
    }


    // Iniciar a captura das imagens
    private void takePicture() {
        on = true;
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic2 mFaceGraphic;

        /**
         * Constructor
         *
         * @param overlay
         */
        public GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay     = overlay;
            mFaceGraphic = new FaceGraphic2(getBaseContext(), overlay);
            mFaceGraphic.setFaceMask(face_mascara);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);

            // pega Rosots detectados
            final SparseArray<Face> faces = detectionResults.getDetectedItems();

            // verifica
            if ((faces.size() == 1) && (mFaceGraphic.isDetect())){
                /**
                 * para pegar o blink chamar o metodo getStateEye(face)
                 */
                getStateEye(face);
                //on = true; //Start para começar capturar a face
            }
            else if (!mFaceGraphic.isDetect()) {
                on = false;
            }
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) { mOverlay.remove(mFaceGraphic); }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }

    /**
     * Gerar bitmap direto do frame de video
     * sem precisar tirar foto
     */
    class FaceDetectorFrame extends Detector<Face> {
        private Detector<Face> mDelegate;

        FaceDetectorFrame(Detector<Face> delegate) {
            mDelegate = delegate;
        }

        public SparseArray<Face> detect(Frame frame) {
            YuvImage yuvImage = new YuvImage(frame.getGrayscaleImageData().array(), ImageFormat.NV21, frame.getMetadata().getWidth(), frame.getMetadata().getHeight(), null);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, frame.getMetadata().getWidth(), frame.getMetadata().getHeight()), 100, byteArrayOutputStream);
            byte[] jpegArray = byteArrayOutputStream.toByteArray();
            Bitmap TempBitmap = BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.length);

            //Rotate bitmap
            TempBitmap = rotate(TempBitmap, -90);

            //Detecta face no frame
            SparseArray<Face> faces = mDelegate.detect(frame);

            //captura somente 1 face
            if(faces.size() == 1 && on){
                for (int i = 0; i < faces.size() && (take); i++) {
                    Face thisFace = faces.valueAt(i);
                    int x = (int) thisFace.getPosition().x;
                    int y = (int) thisFace.getPosition().y;
                    int width = (int) thisFace.getWidth();
                    int height = (int) thisFace.getHeight();

                    //TempBitmap Face recortada do frame gerado.
                    TempBitmap = Bitmap.createBitmap(TempBitmap, x, y, width, height);
                    Handler h = new Handler(Looper.getMainLooper());
                    final Bitmap finalTempBitmap = TempBitmap;
                    h.post(new Runnable() {
                        public void run() {
                            // intervalo de captura
                            int t = 1000;
                            new CountDownTimer(t, 1000) {
                                public void onTick(long millisUntilFinished) {
                                    take = false;
                                    //Exibir a face extraida do frame no ImageView
                                    imageCropped.setImageBitmap(finalTempBitmap);
                                    Log.d(TAG,"seconds remaining: " + millisUntilFinished / 1000);
                                }

                                public void onFinish() {
                                    take = true;
                                    Log.d(TAG,"done!");
                                }
                            }.start();
                        }
                    });
                }

            }
            return mDelegate.detect(frame);
        }

        public boolean isOperational() {
            return mDelegate.isOperational();
        }

        public boolean setFocus(int id) {
            return mDelegate.setFocus(id);
        }
    }

    /**
     * Metodo que retorna o bitmap rotacionado
     * @param b
     * @param degrees
     * @return
     */
    public final Bitmap rotate(Bitmap b, float degrees) {
        if (degrees != 0 && b != null) {
            Matrix m = new Matrix();
            m.setRotate(degrees, (float) b.getWidth() / 2,
                    (float) b.getHeight() / 2);

            Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(),
                    b.getHeight(), m, true);
            if (b != b2) {
                b.recycle();
                b = b2;
            }

        }
        return b;
    }

    /**
     * Metodo que retorna o estado dos olhos
     *
     * @return
     * --------------------------------------------------------
     * state:
     * 0 - Both eyes are open again
     * 1 - Both eyes are initially open
     * 2 - Both eyes become closed
     * --------------------------------------------------------
     */
    private void getStateEye(Face face) {
        float leftEye  = face.getIsLeftEyeOpenProbability();
        float rightEye = face.getIsRightEyeOpenProbability();

        // verifica o resultado
        if ((leftEye == Face.UNCOMPUTED_PROBABILITY) || (rightEye == Face.UNCOMPUTED_PROBABILITY)) { return; }

        // calcula e retorna o minimo
        float value = Math.min(leftEye, rightEye);

        // verifica o minimo
        switch (state) {
            case 0:
                if (value > EYE_CLOSED_THRESHOLD) { state = 1; }
                break;
            case 1:
                if (value < EYE_CLOSED_THRESHOLD) { state = 2; }
                break;
            case 2:
                if (value > EYE_CLOSED_THRESHOLD)  {
                    Log.d("debug", "blink occurred!");

                    // seta o state
                    state = 0;

                    // tira a foto
                    takePicture();
                }
                break;
        }
    }
}