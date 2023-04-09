package com.example.readit;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_CODE = 9999;
    private static final int CAPTURE_IMAGE_CODE = 8888;
    private static final int REQ_CAM_CODE = 100;

    public ActivityResultLauncher<CropImageContractOptions> cropImageActivityResultLauncher = registerForActivityResult(
            new CropImageContract(),
            result -> {
                if (result.isSuccessful()) {
                    Uri resultUri = result.getUriContent();
                    Bitmap bmp = result.getBitmap();
                    try {
                        bmp = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
                        getTextFromImage(bmp);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
    );
    private Bitmap bitmap;
    private long backtime;
    private Toast backToast;
    private ImageView cam_img, gal_img;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cam_img = findViewById(R.id.camview);
        gal_img = findViewById(R.id.gallery);
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.CAMERA
            }, REQ_CAM_CODE);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, REQ_CAM_CODE);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_CAM_CODE);
        }
        cam_img.setOnClickListener(v -> {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, CAPTURE_IMAGE_CODE);
        });
        gal_img.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE_CODE);
        });
    }

    public CropImageContractOptions getCropOptions(Uri imageUri) {
        return new CropImageContractOptions(imageUri, new CropImageOptions())
                .setActivityTitle("Cropper")
                .setGuidelines(CropImageView.Guidelines.ON)
                .setCropShape(CropImageView.CropShape.RECTANGLE)
                .setAllowRotation(true)
                .setAllowFlipping(true)
                .setOutputCompressFormat(Bitmap.CompressFormat.PNG)
                .setAllowCounterRotation(true)
                .setActivityMenuIconColor(R.color.white)
                .setScaleType(CropImageView.ScaleType.CENTER);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_CODE && resultCode == RESULT_OK) {
            Uri currentUri = data.getData();
            cropImageActivityResultLauncher.launch(getCropOptions(currentUri));
        }
        if (requestCode == CAPTURE_IMAGE_CODE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            bitmap = (Bitmap) extras.get("data");
            ContextWrapper cw = new ContextWrapper(getApplicationContext());
            File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
            File fileName = new File(directory, "img_temp.jpg");
            try {
                FileOutputStream out = new FileOutputStream(fileName);
                Uri currentUri = Uri.fromFile(fileName);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                cropImageActivityResultLauncher.launch(getCropOptions(currentUri));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }


        }
    }

    private void textMessage(StringBuilder result) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        // Add the buttons
        builder.setPositiveButton(R.string.Copy, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                ClipboardManager clipboard = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Copied Data", result.toString().trim());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "Copied", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.Retake, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

            }
        });
// Set other dialog properties
        builder.setMessage(result.toString());
        //.setTitle(R.string.dialog_title);

// Create the AlertDialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void getTextFromImage(Bitmap bitmap) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Task<Text> result = recognizer.process(bitmap, 0).addOnSuccessListener(text -> {
            StringBuilder result1 = new StringBuilder();
            for (Text.TextBlock block : text.getTextBlocks()) {
                String blockText = block.getText();
                Point[] blockCornerPoints = block.getCornerPoints();
                Rect blockFrame = block.getBoundingBox();
                for (Text.Line line : block.getLines()) {
                    String lineText = line.getText();
                    Point[] lineCornerPoints = line.getCornerPoints();
                    Rect lineFrame = line.getBoundingBox();
                    for (Text.Element element : line.getElements()) {
                        String elementText = element.getText();
                        result1.append(elementText);
                        result1.append(" ");
                    }
                }
                result1.append("\n");
            }
            if (result1.length() != 0)
                textMessage(result1);
            else
                Toast.makeText(MainActivity.this, "Fail to detect text", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "Fail to detect text", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onBackPressed() {

        if (backtime + 2000 > System.currentTimeMillis()) {
            backToast.cancel();
            super.onBackPressed();
            return;
        } else {
            backToast = Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT);
            backToast.show();
        }
        backtime = System.currentTimeMillis();
    }
}