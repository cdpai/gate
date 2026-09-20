package cdpai.gate.trial;

import cdpai.gate.client.GateClient;

/// Companion to CrossThreadWriteTrial: sends one message, then waits to read the reply.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> cdpai.gate.trial.CrossThreadReadClientTrial
public class CrossThreadReadClientTrial {
    public static void main(String[] args) throws Exception {
        try (var client = new GateClient("cdpai-gate-crossthread-trial")) {
            client.send("{\"hello\":\"world\"}");
            System.out.println("sent, waiting for reply...");
            System.out.println("reply: " + client.receive());
        }
    }
}
