package biz.binarysolutions.imageconverter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class FlavorSpecific {

    private void showSupportDialog() {

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(activity);

        builder.setTitle(R.string.support_dialog_title);
        builder.setMessage(R.string.support_dialog_text);

        builder.setPositiveButton(R.string.cont, (dialog, which) -> {

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(activity.getString(R.string.donation_url)));
            activity.startActivity(intent);

            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private final Activity activity;

    public FlavorSpecific(Activity activity) {
        this.activity = activity;
    }

    public void initialize() {

        Button button = activity.findViewById(R.id.buttonSupport);
        if (button != null) {
            button.setOnClickListener(view -> showSupportDialog());
        }
    }
}
