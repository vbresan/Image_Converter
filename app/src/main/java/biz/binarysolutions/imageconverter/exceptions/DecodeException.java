package biz.binarysolutions.imageconverter.exceptions;

import java.io.IOException;

/**
 *
 */
public class DecodeException extends ConvertException {

    /**
     *
     */
    public DecodeException() {
        super();
    }

    /**
     *
     * @param exception
     */
    public DecodeException(IOException exception) {
        super(exception);
    }
}
