package com.example.sobr;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.Date;
import java.util.Random;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelManager;
import com.google.firebase.ml.custom.FirebaseModelOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;
import com.google.firebase.ml.custom.model.FirebaseCloudModelSource;
import com.google.firebase.ml.custom.model.FirebaseLocalModelSource;
import com.google.firebase.ml.custom.model.FirebaseModelDownloadConditions;


import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class SobrActivity extends AppCompatActivity {

    // SmartCar Auth info
    private static String CLIENT_ID;
    private static String REDIRECT_URI;
    private static String[] SCOPE;
    // private SmartcarAuth smartcarAuth;

    // Camera variables
    // i think the number might be too big
    private static final int REQ_CODE_TAKE_PICTURE = 420;
    private Camera mCamera;
    private CameraPreview mPreview;

    private ImageView preview;

    // Firebase stuff
    private FirebaseModelInterpreter mInterpreter;
    private FirebaseModelInputOutputOptions mDataOptions;

    private static final String HOSTED_MODEL_NAME = "drunk_quant_model";
    private static final String LOCAL_MODEL_ASSET = "quant_model.tflite";
    private static final String LABEL_PATH = "labels.txt";

    // Input size might need to be adjusted
    private static final int DIM_BATCH_SIZE = 1; // 32
    private static final int DIM_PIXEL_SIZE = 3;
    private static final int DIM_IMG_SIZE_X = 224; // 106
    private static final int DIM_IMG_SIZE_Y = 224; // 106

    private List<String> mLabelList;
    private static final int RESULTS_TO_SHOW = 3;

    /* Preallocated buffers for storing image data. */
    private final int[] intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

    private final PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float>
                                o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });

    private Interpreter tflite;
    NotificationManager manager;
    Notification.Builder builder;

    private DatabaseReference dbrf;
    private FirebaseAuth mAuth;
    private FirebaseUser user = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sobr);

        preview = (ImageView) findViewById(R.id.camera_preview);

        Button unlockCar = (Button) findViewById(R.id.unlock_car);
        unlockCar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent picIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(picIntent, REQ_CODE_TAKE_PICTURE);
            }
        });

        mLabelList = new ArrayList<String>();
        mLabelList.add("Sober");
        mLabelList.add("Drunk");

        dbrf = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        builder = new Notification.Builder(this)
                .setContentTitle("Sobr")
                .setContentText("Car has been unlocked")
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_launcher_foreground);
        // Notification notification = builder.build();

        manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        // manager.notify(ID, notification);


        try {
            tflite = new Interpreter(loadModelFile(this));
        } catch (IOException e){
            e.printStackTrace();
        }

    }

    @Override
    protected void onStart(){
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        user = mAuth.getCurrentUser();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent){
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQ_CODE_TAKE_PICTURE && resultCode == RESULT_OK){
            if (intent != null) {
                Bitmap bitmap = (Bitmap) intent.getExtras().get("data");
                preview.setImageBitmap(bitmap);
                Random rand = new Random();



                ByteBuffer imgData = convertBitmapToByteBuffer(bitmap, bitmap.getWidth(), bitmap.getHeight());

                float[][] result = new float[1][mLabelList.size()];

                try {
                    tflite.run(imgData, result);
                } catch (Exception e) {
                    Log.v("TF LITE", "BEGIN");
                    e.printStackTrace();
                    Log.v("TF LITE", "END");
                }


                float val = result[0][0];

                // showToast("Drunk Probability: " + val);

                if (rand.nextInt(5) > 1) {
                    unlockCar();
                } else {
                    showToast("Get an uber mate.");
                }
            }
        }
    }

    // Unlock the car
    private void unlockCar(){
        showToast("Car has been unlocked.");
        Notification notification = builder.build();
        manager.notify(120, notification);
        logCarAccess();
    }


    // Store time and date of accessing car
    private void logCarAccess(){
        if (user == null){
            Toast.makeText(getApplicationContext(), "Please log in", Toast.LENGTH_SHORT).show();
            return;
        }

        Random rand = new Random();
        String id = String.valueOf(rand.nextInt(10000));

        Date date = new Date();

        DatabaseReference ref = dbrf.child("times/" + id);
        ref.child("day").setValue(Integer.toString(date.getDay()));
        ref.child("month").setValue(Integer.toString(date.getMonth()));
        ref.child("year").setValue(Integer.toString(date.getYear()));
        ref.child("hour").setValue(Integer.toString(date.getHours()));
        ref.child("minute").setValue(Integer.toString(date.getMinutes()));
        ref.child("second").setValue(Integer.toString(date.getSeconds()));
    }


    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(LOCAL_MODEL_ASSET);

        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    private void initCustomModel() {
        mLabelList = loadLabelList(this);

        int[] inputDims = {DIM_BATCH_SIZE, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, DIM_PIXEL_SIZE};
        int[] outputDims = {DIM_BATCH_SIZE, mLabelList.size()};
        try {
            mDataOptions =
                    new FirebaseModelInputOutputOptions.Builder()
                            .setInputFormat(0, FirebaseModelDataType.BYTE, inputDims)
                            .setOutputFormat(0, FirebaseModelDataType.BYTE, outputDims)
                            .build();

            FirebaseModelDownloadConditions conditions = new FirebaseModelDownloadConditions
                    .Builder()
                    .requireWifi()
                    .build();

            FirebaseLocalModelSource localSource =
                    new FirebaseLocalModelSource.Builder("asset")
                            .setAssetFilePath(LOCAL_MODEL_ASSET).build();

            FirebaseCloudModelSource cloudSource = new FirebaseCloudModelSource.Builder
                    (HOSTED_MODEL_NAME)
                    .enableModelUpdates(true)
                    .setInitialDownloadConditions(conditions)
                    .setUpdatesDownloadConditions(conditions)  // You could also specify
                    // different conditions
                    // for updates
                    .build();

            FirebaseModelManager manager = FirebaseModelManager.getInstance();
            manager.registerLocalModelSource(localSource);
            manager.registerCloudModelSource(cloudSource);

            FirebaseModelOptions modelOptions =
                    new FirebaseModelOptions.Builder()
                            .setCloudModelName(HOSTED_MODEL_NAME)
                            .setLocalModelName("asset")
                            .build();


            mInterpreter = FirebaseModelInterpreter.getInstance(modelOptions);

        } catch (FirebaseMLException e) {
            showToast("Error while setting up the model");
            e.printStackTrace();
        }
    }


    private void runModelInference(Bitmap mSelectedImage) {
        if (mInterpreter == null) {
            Log.e("TAG: Run model inference", "Image classifier has not been initialized; Skipped.");
            return;
        }

        // Create input data.
        ByteBuffer imgData = convertBitmapToByteBuffer(mSelectedImage, mSelectedImage.getWidth(),
                mSelectedImage.getHeight());

        showToast(Integer.toString(mSelectedImage.getWidth()) + " " + Integer.toString(mSelectedImage.getHeight()));

        try {
            FirebaseModelInputs inputs = new FirebaseModelInputs.Builder().add(imgData).build();
            // Here's where the magic happens!!

            mInterpreter
                    .run(inputs, mDataOptions)
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            e.printStackTrace();
                            showToast("Error running model inference 1");
                        }
                    })
                    .continueWith(
                            new Continuation<FirebaseModelOutputs, List<String>>() {
                                @Override
                                public List<String> then(Task<FirebaseModelOutputs> task) {

                                    byte[][] labelProbArray = task.getResult()
                                            .<byte[][]>getOutput(0);

                                    List<String> topLabels = getTopLabels(labelProbArray);

                                    /* Displays the top three labels in the graphic overlay
                                    mGraphicOverlay.clear();

                                    GraphicOverlay.Graphic labelGraphic = new LabelGraphic
                                            (mGraphicOverlay, topLabels);

                                    mGraphicOverlay.add(labelGraphic);
                                    */

                                    showToast(topLabels.get(0));

                                    return topLabels;
                                }
                            });

        } catch (FirebaseMLException e) {
            e.printStackTrace();
            showToast("Error running model inference 2");
        }
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Writes Image data into a {@code ByteBuffer}.
     */
    private synchronized ByteBuffer convertBitmapToByteBuffer(
            Bitmap bitmap, int width, int height) {

        ByteBuffer imgData =
                ByteBuffer.allocateDirect(
                        4 * DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);

        imgData.order(ByteOrder.nativeOrder());
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y,
                true);

        imgData.rewind();
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0,
                scaledBitmap.getWidth(), scaledBitmap.getHeight());

        // Convert the image to int points.
        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                imgData.putFloat((byte) ((val >> 16) & 0xFF));
                imgData.putFloat((byte) ((val >> 8) & 0xFF));
                imgData.putFloat((byte) (val & 0xFF));
            }
        }
        return imgData;
    }

    /**
     * Reads label list from Assets.
     */
    private List<String> loadLabelList(Activity activity) {
        List<String> labelList = new ArrayList<>();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(activity.getAssets().open
                             (LABEL_PATH)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
        } catch (IOException e) {
            Log.e("TAG: Load Label List", "Failed to read label list.", e);
        }
        return labelList;
    }

    /**
     * Gets the top labels in the results.
     */
    private synchronized List<String> getTopLabels(byte[][] labelProbArray) {
        for (int i = 0; i < mLabelList.size(); ++i) {
            sortedLabels.add(
                    new AbstractMap.SimpleEntry<>(mLabelList.get(i), (labelProbArray[0][i] &
                            0xff) / 255.0f));
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }
        List<String> result = new ArrayList<>();
        final int size = sortedLabels.size();
        for (int i = 0; i < size; ++i) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            result.add(label.getKey() + ":" + label.getValue());
        }
        Log.d("TAG: get top labels", "labels: " + result.toString());
        return result;
    }
}

