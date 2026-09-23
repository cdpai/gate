package cdpai.gate.client;

/// cdpgate refused, or could not be reached. `hint` is cdpgate's own advice on what to change.
public final class GateDeniedException extends RuntimeException {

    public final String hint;

    public GateDeniedException(String message, String hint) {
        super(hint == null ? message : message + " -- " + hint);
        this.hint = hint;
    }
}
