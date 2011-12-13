package com.trendmicro.mist;

public class MistException extends Exception {
    static final long serialVersionUID = 7402496603188032952L;

    public static final String ALREADY_ATTACHED = "already attached";

    public static final String INCOMPATIBLE_TYPE_SINK = "incompatible type, session is mounted as producer";
    public static final String INCOMPATIBLE_TYPE_SOURCE = "incompatible type, session is mounted as consumer";

    public static final String INVALID_MESSAGE_SIZE = "invalid message size";

    public static final String UNABLE_TO_PARSE_MIST_MESSAGE = "unable to parse mist message block";

    public static final String ALREADY_MOUNTED = "exchange already mounted";

    public static final String EMPTY_SESSION = "empty session";

    public static String sizeTooLarge(int size) {
        return String.format("sending %d bytes not allowed (exceeds %d bytes)", size, Daemon.MAX_TRANSMIT_MESSAGE_SIZE);
    }

    public static String exchangeNotFound(String exchange) {
        return String.format("exchange name `%s' not found", exchange);
    }

    public MistException(String message) {
        super(message);
    }

    public MistException(String message, Throwable cause) {
        super(message, cause);
    }

    public MistException(Throwable cause, String format, Object... objs) {
        super(String.format(format, objs), cause);
    }
}
