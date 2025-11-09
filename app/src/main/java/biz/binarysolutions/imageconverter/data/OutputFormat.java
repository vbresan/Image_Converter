package biz.binarysolutions.imageconverter.data;

import android.graphics.Bitmap;

public enum OutputFormat {
    BMP, JPG, PNG, TIF, WEBP;

    public String getMimeType() {

        return switch (this) {
            case BMP  -> "image/x-ms-bmp";
            case JPG  -> "image/jpeg";
            case PNG  -> "image/png";
            case TIF  -> "image/tiff";
            case WEBP -> "image/webp";
        };
    }

    public Bitmap.CompressFormat getCompressFormat() {

        return switch (this) {
            case BMP  -> null;
            case JPG  -> Bitmap.CompressFormat.JPEG;
            case PNG  -> Bitmap.CompressFormat.PNG;
            case TIF  -> null;
            case WEBP -> Bitmap.CompressFormat.WEBP;
        };
    }

    public String getFileExtension() {

        return switch (this) {
            case BMP  -> "bmp";
            case JPG  -> "jpg";
            case PNG  -> "png";
            case TIF  -> "tif";
            case WEBP -> "webp";
        };
    }
}
