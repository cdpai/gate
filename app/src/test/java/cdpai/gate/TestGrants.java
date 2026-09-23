package cdpai.gate;

import cdpai.gate.access.AncestryNode;
import cdpai.gate.access.Scope;

/// Lets a UI trial put a grant into a GateServer without a real connection.
public final class TestGrants {
    public static void add(GateServer server, String client, AncestryNode anchor, int minutes, Scope scope) {
        server.grants.create(client, anchor, minutes, minutes, scope);
    }

    private TestGrants() {}
}
