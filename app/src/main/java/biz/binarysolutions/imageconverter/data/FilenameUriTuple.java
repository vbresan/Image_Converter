package biz.binarysolutions.imageconverter.data;

import android.net.Uri;

import java.util.AbstractMap;

/**
 *
 */
public class FilenameUriTuple extends AbstractMap.SimpleEntry<String, Uri> {

    /**
     *
     * @param key
     * @param value
     */
    public FilenameUriTuple(String key, Uri value) {
        super(key, value);
    }

    /**
     *
     * @return
     */
    public String getFilename() {
        return getKey();
    }

    /**
     *
     * @return
     */
    public Uri getUri() {
        return getValue();
    }

    @Override
    public String toString() {
        return getKey();
    }
}
