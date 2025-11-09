package biz.binarysolutions.imageconverter.exceptions;

import java.io.IOException;

public class ExportException extends ConvertException {

    public ExportException() {
        super();
    }

    public ExportException(IOException e) {
        super(e);
    }
}
