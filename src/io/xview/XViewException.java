package io.xview;

public class XViewException extends RuntimeException {
    public XViewException() {
        super();
    }

    public XViewException(String message) {
        super(message);
    }

    public XViewException(String message, Throwable cause) {
        super(message, cause);
    }

    public XViewException(Throwable cause) {
        super(cause);
    }

    protected XViewException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
