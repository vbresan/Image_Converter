package biz.binarysolutions.imageconverter.exceptions;

/**
 *
 */
public abstract class ConvertException extends Throwable {

    /**
     *
     */
    public ConvertException() {
        super();
    }

    /**
     *
     * @param throwable
     */
    public ConvertException(Throwable throwable) {
        super(throwable.toString());
        setStackTrace(throwable.getStackTrace());
    }
}
