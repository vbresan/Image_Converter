package biz.binarysolutions.imageconverter.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtil {

    public static void copy(InputStream in, File to) throws IOException {

        try (FileOutputStream out = new FileOutputStream(to)) {

            byte[] buffer = new byte[8 * 1024];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}
