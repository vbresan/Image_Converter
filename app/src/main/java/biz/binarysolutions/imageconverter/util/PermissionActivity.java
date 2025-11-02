package biz.binarysolutions.imageconverter.util;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import biz.binarysolutions.imageconverter.R;

import static android.content.DialogInterface.*;

/**
 *
 */
public abstract class PermissionActivity  extends AppCompatActivity {

    private static final String PERMISSION =
        Manifest.permission.WRITE_EXTERNAL_STORAGE;

    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    /**
     *
     * @return
     */
    private boolean shouldRequestPermission() {

        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }

        int granted = PackageManager.PERMISSION_GRANTED;
        return ContextCompat.checkSelfPermission(this, PERMISSION) != granted;
    }

    /**
     *
     * @return
     */
    private boolean shouldShowRequestRationale() {
        return ActivityCompat.
                shouldShowRequestPermissionRationale(this, PERMISSION);
    }

    /**
     *
     */
    private void requestPermission() {

        ActivityCompat.requestPermissions(
            this,
            new String[] { PERMISSION },
            PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE
        );
    }

    /**
     *
     */
    private void displayDialogRequestPermission() {

        new AlertDialog.Builder(this)
            .setTitle(android.R.string.dialog_alert_title)
            .setMessage(R.string.write_external_storage)
            .setPositiveButton(android.R.string.ok, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    requestPermission();
                }
            })
            .create()
            .show();
    }

    protected abstract void onPermissionGranted(boolean isGranted);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (shouldRequestPermission()) {
            if (shouldShowRequestRationale()) {
                displayDialogRequestPermission();
            } else {
                requestPermission();
            }
        } else {
            onPermissionGranted(true);
        }
    }

    @Override
    public void onRequestPermissionsResult
        (
            int               requestCode,
            @NonNull String[] permissions,
            @NonNull int[]    results
        ) {
        if (requestCode != PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE) {
            return;
        }

        int granted = PackageManager.PERMISSION_GRANTED;
        if (results.length > 0 && results[0] == granted) {
            onPermissionGranted(true);
        } else {
            onPermissionGranted(false);
        }
    }
}
