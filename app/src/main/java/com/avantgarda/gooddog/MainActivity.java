package com.avantgarda.gooddog;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.ActionBar;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    Button button;
    ImageView corgiView, pulseView, photoView;
    TextView resultView;

    ObjectAnimator scaleDown;
    String currentPhotoPath;
    String labelCheck, labelLower;

    boolean isUp;

    static final int REQUEST_TAKE_PHOTO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = findViewById(R.id.button);
        photoView = findViewById(R.id.photoView);
        corgiView = findViewById(R.id.corgiView);
        pulseView = findViewById(R.id.pulseView);
        resultView = findViewById(R.id.resultsView);

        isUp = true;
        labelCheck = "Dog";
        labelLower = "dog";
        button.setText(getString(R.string.find_good_dog, labelLower));

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                slideUp(resultView,true);
                dispatchTakePictureIntent();
            }
        });

        button.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                corgiView.setVisibility(View.VISIBLE);
                photoView.setVisibility(View.GONE);
                slideUp(resultView,false);
                return true;
            }
        });

        corgiView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // Change label
                labelChange();
                return true;
            }
        });

        scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                pulseView,
                PropertyValuesHolder.ofFloat("scaleX", 1.2f),
                PropertyValuesHolder.ofFloat("scaleY", 1.2f));
        scaleDown.setDuration(310);

        scaleDown.setRepeatCount(ObjectAnimator.INFINITE);
        scaleDown.setRepeatMode(ObjectAnimator.REVERSE);
    }

    private void labelChange() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Label");
        builder.setMessage("Look for other things that are good?");

        // Set up the input
        final EditText input = new EditText(this);
        input.setTextSize(32);
        input.setPadding(32,32,32,32);
        // Specify the type of input expected
        input.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                labelCheck = input.getText().toString();
                labelLower = labelCheck.toLowerCase();
                button.setText(getString(R.string.find_good_dog, labelLower));
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_TAKE_PHOTO){
            if(resultCode == RESULT_OK){
                loadingStart();
                File imageFile = new File(currentPhotoPath);
                Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                photoView.setImageBitmap(bitmap);
                FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
                FirebaseVisionImageLabeler labeler = FirebaseVision.getInstance()
                        .getOnDeviceImageLabeler();
                labeler.processImage(image)
                        .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
                            @Override
                            public void onSuccess(List<FirebaseVisionImageLabel> labels) {
                                // Task completed successfully
                                float labelConfidence = 0;
                                for (FirebaseVisionImageLabel label: labels) {
                                    String text = label.getText();
                                    String entityId = label.getEntityId();
                                    float confidence = label.getConfidence();
                                    if(labelCheck.equals(text)){ labelConfidence = confidence; }
                                    Log.i("MLResult", "onSuccess: " + text + " " + entityId + " " + confidence);
                                }
                                loadingFinish(true, labelConfidence);
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                Log.i("MLResult", "onFailure: " + e.getMessage());
                                loadingFinish(false,0);
                            }
                        });
            }
        }
    }

    private void loadingStart() {
        corgiView.setVisibility(View.GONE);
        photoView.setVisibility(View.GONE);
        button.setVisibility(View.GONE);
        pulseView.setVisibility(View.VISIBLE);
        scaleDown.start();
    }

    private void loadingFinish(boolean successful, float confidence) {
        photoView.setVisibility(View.VISIBLE);
        button.setVisibility(View.VISIBLE);
        pulseView.setVisibility(View.GONE);
        scaleDown.cancel();
        if(successful){
            if(confidence <= 0.05){ resultView.setText(getString(R.string.not_dog, labelLower, labelLower)); }
            else { resultView.setText(getString(R.string.is_good_dog, labelLower)); }
            slideDown(resultView);
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                // ...
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.avantgarda.gooddog.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    public void slideUp(View view, boolean hideQuick){
        if(isUp){ return; }
        TranslateAnimation animate = new TranslateAnimation(
                0,                 // fromXDelta
                0,                 // toXDelta
                0,  // fromYDelta
                -view.getHeight());                // toYDelta
        animate.setDuration(500);
        if(hideQuick){ animate.setDuration(0); }
        animate.setFillAfter(true);
        view.startAnimation(animate);
        isUp = true;
    }

    public void slideDown(View view){
        view.setVisibility(View.VISIBLE);
        view.bringToFront();
        TranslateAnimation animate = new TranslateAnimation(
                0,                 // fromXDelta
                0,                 // toXDelta
                -view.getHeight(),                 // fromYDelta
                0); // toYDelta
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
        isUp = false;
    }
}
