package cdpai.gate.trial;

import cdpai.gate.client.GateClient;

/// Companion to PipeServerTrial. Run this from a genuinely separate process (a separate terminal,
/// or a separate `java` invocation) so the pid the server reports is this process's real pid --
/// not something the two sides agreed on in advance, which is the whole point of the design.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> cdpai.gate.trial.PipeClientTrial
public class PipeClientTrial {
    public static void main(String[] args) throws Exception {
        var ownPid = ProcessHandle.current().pid();
        System.out.println("own pid (what the server should report back): " + ownPid);
        try (var client = new GateClient("cdpai-gate-trial")) {
            client.send("{\"hello\":\"from pid " + ownPid + "\"}");
            var reply = client.receive();
            System.out.println("server replied : " + reply);
            System.out.println(reply.contains(String.valueOf(ownPid))
                ? "*** MATCH -- kernel-attested pid equals this process's real pid ***"
                : "*** MISMATCH -- something is wrong ***");
        }
    }
}
