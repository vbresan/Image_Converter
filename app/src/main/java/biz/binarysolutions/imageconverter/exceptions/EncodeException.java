package biz.binarysolutions.imageconverter.exceptions;

import java.util.List;

/**
 *
 */
public class EncodeException extends ConvertException {

    private List<Integer> directories = null;

    /**
     *
     */
    public EncodeException() {
        super();
    }

    /**
     *
     * @param exception
     */
    public EncodeException(Exception exception) {
        super(exception);
    }

    /**
     *
     * @param directories
     */
    public EncodeException(List<Integer> directories) {
        super();
        this.directories = directories;
    }

    /**
     *
     * @return
     */
    public List<Integer> getDirectories() {
        return directories;
    }
}
