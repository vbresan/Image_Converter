package biz.binarysolutions.imageconverter.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import biz.binarysolutions.imageconverter.data.OutputFormat;

/**
 *
 */
public class Converter {

    /**
     *
     * @param bitmap
     * @param format
     * @param file
     * @throws IOException
     */
    private static void encodeUsingNativeAPI
        (
            Bitmap         bitmap,
            Bitmap.CompressFormat format,
            File           file
        )
            throws IOException {

        FileOutputStream out  = new FileOutputStream(file);
        bitmap.compress(format, 100, out);
        out.close();
    }

    /**
     *
     * @param bitmap
     * @param format
     * @param file
     * @throws Exception
     */
    private static void encodeUsingNonNativeAPI
        (
            Bitmap bitmap,
            OutputFormat format,
            File file
        )
            throws IOException {

        if (format == OutputFormat.BMP) {
            BMPUtil.encodeToBMP(bitmap, file);
        } else /* if (format == OutputFormat.TIF) */ {
            TiffUtil.encodeToTIF(bitmap, file);
        }
    }

    /**
     *
     * @param bitmap
     * @param format
     * @param file
     */
    public static void encodeBitmap
        (
            Bitmap       bitmap,
            OutputFormat format,
            File         file
        )
            throws IOException {

        Bitmap.CompressFormat compressFormat = format.getCompressFormat();
        if (compressFormat != null) {
            encodeUsingNativeAPI(bitmap, compressFormat, file);
        } else {
            encodeUsingNonNativeAPI(bitmap, format, file);
        }
    }

    /**
     * TODO: do in memory?
     *
     * @param bitmap
     * @return
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static Bitmap recodeGIFBitmap(Bitmap bitmap, File cacheDirectory)
        throws IOException {

        File cache = new File(cacheDirectory, "temp.png");
        encodeUsingNativeAPI(bitmap, Bitmap.CompressFormat.PNG, cache);

        Bitmap reloaded = BitmapFactory.decodeFile(cache.getAbsolutePath());
        cache.delete();

        return reloaded;
    }
}
