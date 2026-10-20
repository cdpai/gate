package cdpai.gate.client;

import leasegate.transport.LeaseDeniedException;

/// cdpgate refused, or could not be reached. `hint` is cdpgate's own advice on what to change.
public final class GateDeniedException extends LeaseDeniedException {

    public GateDeniedException(String message, String hint) { super(message, hint); }
}
