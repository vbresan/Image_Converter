package biz.binarysolutions.imageconverter.listeners;

import android.widget.CompoundButton;

import java.util.List;

import biz.binarysolutions.imageconverter.data.OutputFormat;

/**
 *
 */
public class OutputFormatListener
    implements CompoundButton.OnCheckedChangeListener {

    private final List<OutputFormat> outputFormats;
    private final OutputFormat       format;

    /**
     *
     * @param outputFormats
     * @param format
     */
    public OutputFormatListener
        (
            List<OutputFormat> outputFormats,
            OutputFormat       format
        ) {

        this.outputFormats = outputFormats;
        this.format        = format;
    }

    @Override
    public void onCheckedChanged(CompoundButton view, boolean isChecked) {

        if (isChecked) {
            outputFormats.add(format);
        } else {
            outputFormats.remove(format);
        }
    }
}
