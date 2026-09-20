package cdpai.gate.trial;

import cdpai.gate.client.GateClient;

/// A consumer of the real GateServer/CdpHub. Run several of these AT ONCE, from separate
/// processes, all using id=1 for their first request -- if the hub's id-remapping is broken,
/// this is what would show it: a reply meant for one consumer showing up at another, or a
/// consumer hanging forever waiting for a reply that got misrouted.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> cdpai.gate.trial.HubConsumerTrial <label> <pipe-name>
public class HubConsumerTrial {
    public static void main(String[] args) throws Exception {
        var label = args[0];
        var pipeName = args[1];
        try (var client = new GateClient(pipeName)) {
            client.send("{\"id\":1,\"method\":\"Browser.getVersion\"}");
            System.out.println("[" + label + "] reply to id=1: " + client.receive());

            client.send("{\"id\":1,\"method\":\"Target.getTargets\"}");
            System.out.println("[" + label + "] reply to id=1 (again, same id reused): " + client.receive());
        }
    }
}
