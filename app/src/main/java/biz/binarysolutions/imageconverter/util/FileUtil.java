package biz.binarysolutions.imageconverter.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
public class FileUtil {

    /**
     *
     * @param inStream
     * @param to
     */
    public static void copy(InputStream inStream, File to) throws IOException {

        FileOutputStream outStream = null;

        try {

            outStream = new FileOutputStream(to);

            byte[] buffer = new byte[8 * 1024];
            int bytesRead;

            while ((bytesRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }

        } finally {
            if (inStream != null) {
                inStream.close();
            }
            if (outStream != null) {
                outStream.close();
            }
        }
    }

    /**
     *
     * @return
     */
    public static File getCachedFile
        (
            InputStream is,
            File        parent,
            String      filename
        )
            throws IOException {

        if (is == null) {
            throw new IOException("Re-opened input stream is null.");
        }

        File cachedFile = new File(parent, filename);
        FileUtil.copy(is, cachedFile);

        return cachedFile;
    }
}
