package biz.binarysolutions.imageconverter.image;

import android.graphics.Bitmap;

import org.beyka.tiffbitmapfactory.TiffBitmapFactory;
import org.beyka.tiffbitmapfactory.TiffConverter;
import org.beyka.tiffbitmapfactory.TiffSaver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import biz.binarysolutions.imageconverter.data.OutputFormat;
import biz.binarysolutions.imageconverter.exceptions.EncodeException;

/**
 *
 */
public class TiffUtil {

    /**
     *
     * @param file
     * @return
     */
    private static TiffBitmapFactory.Options getOptions(File file) {

        TiffBitmapFactory.Options options = new TiffBitmapFactory.Options();

        options.inJustDecodeBounds = true;
        TiffBitmapFactory.decodeFile(file, options);
        options.inJustDecodeBounds = false;

        return options;
    }

    /**
     *
     * @param filename
     * @param current
     * @param total
     * @param format
     * @return
     */
    private static String getNewFilename
        (
            String       filename,
            int          current,
            int          total,
            OutputFormat format
        ) {

        String toInsert = String.format(
            Locale.getDefault(), " (%04d of %04d)", current, total);

        String substring;

        int index = filename.lastIndexOf(".");
        if (index == -1) {
            substring = filename + toInsert;
        } else {
            substring = filename.substring(0, index) + toInsert;
        }

        return substring + "." + format.getFileExtension();
    }

    /**
     *
     * @param format
     * @param in
     * @param out
     * @param directory
     * @return
     */
    private static boolean convertDirectly
        (
            OutputFormat format,
            String       in,
            String       out,
            int          directory
        )
            throws Exception {

        TiffConverter.ConverterOptions options =
            new TiffConverter.ConverterOptions();

        // limiting memory can cause a failure in conversion
        // options.availableMemory   = 10000000;
        options.readTiffDirectory = directory;
        options.throwExceptions   = true;

        if (format == OutputFormat.BMP) {
            return TiffConverter.convertTiffBmp(in, out, options, null);
        } else if (format == OutputFormat.JPG) {
            return TiffConverter.convertTiffJpg(in, out, options, null);
        } else if (format == OutputFormat.PNG) {
            return TiffConverter.convertTiffPng(in, out, options, null);
        } else {
            throw new Exception();
        }
    }

    /**
     *
     * @param bitmap
     * @param file
     */
    static void encodeToTIF(Bitmap bitmap, File file)
        throws IOException {

        TiffSaver.SaveOptions options = new TiffSaver.SaveOptions();
        options.inThrowException = true;

        try {
            TiffSaver.saveBitmap(file, bitmap, options);
        } catch (Exception e) {

            IOException exception = new IOException(e.toString());
            exception.setStackTrace(e.getStackTrace());

            throw exception;
        }
    }

    /**
     *
     * @param inFile
     * @param format
     * @param options
     * @param outFile
     */
    private static void convertFromTIFSinglePage
        (
            File                      inFile,
            OutputFormat              format,
            TiffBitmapFactory.Options options,
            File                      outFile
        )
            throws EncodeException {

        Bitmap bitmap    = TiffBitmapFactory.decodeFile(inFile, options);
        int    directory = options.inDirectoryNumber;
        try {
            if (bitmap != null) {
                Converter.encodeBitmap(bitmap, format, outFile);
            } else {
                String in  = inFile.getAbsolutePath();
                String out = outFile.getAbsolutePath();

                TiffUtil.convertDirectly(format, in, out, directory);
            }
        } catch (Exception e) {
            throw new EncodeException(e);
        }
    }

    /**
     *
     * @param inFile
     * @param format
     * @param outputFolder
     * @throws EncodeException
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void convertFromTIF
        (
            File         inFile,
            OutputFormat format,
            File         outputFolder
        )
            throws EncodeException {

        TiffBitmapFactory.Options options = getOptions(inFile);
        options.inAvailableMemory  = 10000000;

        List<Integer> directories = new ArrayList<>();
        String inFilename = inFile.getName();

        int count = options.outDirectoryCount;
        for (int i = 0; i < count; i++) {

            String outFilename = getNewFilename(inFilename, i + 1, count, format);
            File   outFile     = new File(outputFolder, outFilename);

            options.inDirectoryNumber = i;

            try {
                convertFromTIFSinglePage(inFile, format, options, outFile);
            } catch (EncodeException e) {
                outFile.delete();
                directories.add(i);
            }
        }

        if (directories.size() > 0) {
            throw new EncodeException(directories);
        }
    }

    /**
     *
     * @param file
     * @return
     */
    public static boolean isTiffFile(File file) {

        TiffBitmapFactory.Options options = getOptions(file);
        return options.outWidth != -1 || options.outHeight != -1;
    }
}