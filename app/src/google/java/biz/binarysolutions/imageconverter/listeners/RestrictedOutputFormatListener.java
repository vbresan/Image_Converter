package biz.binarysolutions.imageconverter.listeners;

import android.widget.CompoundButton;

import java.util.List;

import biz.binarysolutions.imageconverter.MainActivityGoogle;
import biz.binarysolutions.imageconverter.R;
import biz.binarysolutions.imageconverter.data.OutputFormat;

/**
 *
 */
public class RestrictedOutputFormatListener extends OutputFormatListener {

    private final MainActivityGoogle activity;

    /**
     * @param outputFormats
     * @param format
     * @param activity
     */
    public RestrictedOutputFormatListener
        (
            List<OutputFormat> outputFormats,
            OutputFormat       format,
            MainActivityGoogle activity
        ) {
        super(outputFormats, format);
        this.activity = activity;
    }

    @Override
    public void onCheckedChanged(CompoundButton view, boolean isChecked) {

        if (isChecked && !activity.isFullVersion()) {
            view.setChecked(false);
            activity.displayPurchaseDialog(R.string.output_format_not_available);
        } else {
            super.onCheckedChanged(view, isChecked);
        }
    }
}
