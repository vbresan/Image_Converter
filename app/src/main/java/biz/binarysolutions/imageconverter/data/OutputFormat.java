package biz.binarysolutions.imageconverter.data;

import android.graphics.Bitmap;

/**
 *
 */
public enum OutputFormat {
    BMP, JPG, PNG, TIF, WEBP;


    /**
     *
     * @param mimeType
     * @return
     */
    public boolean isMimeType(String mimeType) {

        if (mimeType == null) {
            return false;
        }

        switch (this) {
            case BMP:
                return mimeType.equals("image/x-ms-bmp");
            case JPG:
                return mimeType.equals("image/jpeg");
            case PNG:
                return mimeType.equals("image/png");
            case TIF:
                return mimeType.equals("image/tiff");
            case WEBP:
                return false;
        }

        return false;
    }

    /**
     *
     * @return
     */
    public Bitmap.CompressFormat getCompressFormat() {

        switch (this) {
            case BMP:
                return null;
            case JPG:
                return Bitmap.CompressFormat.JPEG;
            case PNG:
                return Bitmap.CompressFormat.PNG;
            case TIF:
                return null;
            case WEBP:
                return Bitmap.CompressFormat.WEBP;
        }

        return null;
    }

    /**
     *
     * @return
     */
    public String getFileExtension() {

        switch (this) {
            case BMP:
                return "bmp";
            case JPG:
                return "jpg";
            case PNG:
                return "png";
            case TIF:
                return "tif";
            case WEBP:
                return "webp";
        }

        return "unknown";
    }
}
