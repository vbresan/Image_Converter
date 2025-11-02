package biz.binarysolutions.imageconverter.image;

import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 *
 */
@SuppressWarnings("FieldCanBeLocal")
class BMPUtil {

    private final static int BITMAPFILEHEADER_SIZE = 14;
    private final static int BITMAPINFOHEADER_SIZE = 40;

    // Bitmap file header
    private byte[] bfType = {'B', 'M'};
    private int    bfSize = 0;
    private int    bfReserved1 = 0;
    private int    bfReserved2 = 0;
    private int    bfOffBits = BITMAPFILEHEADER_SIZE + BITMAPINFOHEADER_SIZE;

    // Bitmap info header
    private int biSize = BITMAPINFOHEADER_SIZE;
    private int biWidth = 0;
    private int biHeight = 0;
    private int biPlanes = 1;
    private int biBitCount = 24;
    private int biCompression = 0;
    private int biSizeImage = 0x030000;
    private int biXPelsPerMeter = 0x0;
    private int biYPelsPerMeter = 0x0;
    private int biClrUsed = 0;
    private int biClrImportant = 0;

    // Bitmap raw data
    private int[] pixels;

    // File section
    private ByteBuffer   buffer = null;
    private OutputStream outputStream;

    /**
     *
     * @param bitmap
     * @param filename
     * @throws IOException
     */
    private void saveBitmap(Bitmap bitmap, String filename) throws IOException {

        outputStream = new FileOutputStream(filename);
        save(bitmap);
        outputStream.close();
    }

    /**
     *
     * @param bitmap
     * @param outputStream
     */
    private void saveBitmap(Bitmap bitmap, OutputStream outputStream)
        throws IOException {

        this.outputStream = outputStream;
        save(bitmap);
    }

    /*
     *  The saveMethod is the main method of the process. This method
     *  will call the convertImage method to convert the memory image to
     *  a byte array; method writeBitmapFileHeader creates and writes
     *  the bitmap file header; writeBitmapInfoHeader creates the
     *  information header; and writeBitmap writes the image.
     */
    private void save(Bitmap bitmap) throws IOException {

        convertImage(bitmap);
        writeBitmapFileHeader();
        writeBitmapInfoHeader();
        writeBitmap();
        // write to output stream
        outputStream.write(buffer.array());
    }

    /*
     * convertImage converts the memory image to the bitmap format (BRG).
     * It also computes some information for the bitmap info header.
     */
    private void convertImage(Bitmap bitmap) {

        int pad;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        pixels = new int[width * height];

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        pad = (4 - ((width * 3) % 4)) * height;
        biSizeImage = ((width * height) * 3) + pad;
        bfSize = biSizeImage + BITMAPFILEHEADER_SIZE + BITMAPINFOHEADER_SIZE;

        buffer = ByteBuffer.allocate(bfSize);
        biWidth = width;
        biHeight = height;
    }

    /*
     * writeBitmap converts the image returned from the pixel grabber to
     * the format required. Remember: scan lines are inverted in
     * a bitmap file!
     * Each scan line must be padded to an even 4-byte boundary.
     */
    private void writeBitmap() {

        int    size;
        int    value;
        int    j;
        int    i;
        int    rowCount;
        int    rowIndex;
        int    lastRowIndex;
        int    pad;
        int    padCount;
        byte[] rgb = new byte[3];

        size = (biWidth * biHeight) - 1;
        pad = 4 - ((biWidth * 3) % 4);
        if (pad == 4) {   // Bug correction
            pad = 0;      // Bug correction
        }
        rowCount = 1;
        padCount = 0;
        rowIndex = size - biWidth;
        lastRowIndex = rowIndex;

        for (j = 0; j < size; j++) {

            value = pixels[rowIndex];
            rgb[0] = (byte) (value & 0xFF);
            rgb[1] = (byte) ((value >> 8) & 0xFF);
            rgb[2] = (byte) ((value >> 16) & 0xFF);
            buffer.put(rgb);
            if (rowCount == biWidth) {
                padCount += pad;
                for (i = 1; i <= pad; i++) {
                    buffer.put((byte) 0x00);
                }
                rowCount = 1;
                rowIndex = lastRowIndex - biWidth;
                lastRowIndex = rowIndex;
            } else {
                rowCount++;
            }
            rowIndex++;
        }
        // Update the size of the file
        bfSize += padCount - pad;
        biSizeImage += padCount - pad;
    }

    /*
     * writeBitmapFileHeader writes the bitmap file header to the file.
     */
    private void writeBitmapFileHeader() {

        buffer.put(bfType);
        buffer.put(intToDWord(bfSize));
        buffer.put(intToWord(bfReserved1));
        buffer.put(intToWord(bfReserved2));
        buffer.put(intToDWord(bfOffBits));
    }

    /*
     * writeBitmapInfoHeader writes the bitmap information header
     * to the file.
     */
    private void writeBitmapInfoHeader() {

        buffer.put(intToDWord(biSize));
        buffer.put(intToDWord(biWidth));
        buffer.put(intToDWord(biHeight));
        buffer.put(intToWord(biPlanes));
        buffer.put(intToWord(biBitCount));
        buffer.put(intToDWord(biCompression));
        buffer.put(intToDWord(biSizeImage));
        buffer.put(intToDWord(biXPelsPerMeter));
        buffer.put(intToDWord(biYPelsPerMeter));
        buffer.put(intToDWord(biClrUsed));
        buffer.put(intToDWord(biClrImportant));
    }

    /*
     * intToWord converts an int to a word, where the return
     * value is stored in a 2-byte array.
     */
    private byte[] intToWord(int parValue) {

        byte[] retValue = new byte[2];
        retValue[0] = (byte) (parValue & 0x00FF);
        retValue[1] = (byte) ((parValue >> 8) & 0x00FF);

        return (retValue);
    }

    /*
     * intToDWord converts an int to a double word, where the return
     * value is stored in a 4-byte array.
     */
    private byte[] intToDWord(int parValue) {

        byte[] retValue = new byte[4];
        retValue[0] = (byte) (parValue & 0x00FF);
        retValue[1] = (byte) ((parValue >> 8) & 0x000000FF);
        retValue[2] = (byte) ((parValue >> 16) & 0x000000FF);
        retValue[3] = (byte) ((parValue >> 24) & 0x000000FF);

        return (retValue);
    }

    /**
     *
     * @param bitmap
     * @param file
     */
    static void encodeToBMP(Bitmap bitmap, File file)
        throws IOException {

        FileOutputStream out  = new FileOutputStream(file);
        new BMPUtil().saveBitmap(bitmap, out);
        out.close();
    }
}