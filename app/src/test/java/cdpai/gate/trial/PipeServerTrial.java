package cdpai.gate.trial;

import cdpai.gate.win.NamedPipeServer;

/// Manual end-to-end proof for the piece the spike findings left untested: a real client process
/// connects, and GetNamedPipeClientProcessId names it correctly. Run alongside PipeClientTrial
/// from a SEPARATE process -- same-process calls would prove nothing about peer attestation.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> cdpai.gate.trial.PipeServerTrial
public class PipeServerTrial {
    public static void main(String[] args) throws Exception {
        System.out.println("own pid (for cross-check): " + ProcessHandle.current().pid());
        try (var server = new NamedPipeServer("cdpai-gate-trial")) {
            System.out.println("listening on \\\\.\\pipe\\cdpai-gate-trial ...");
            var accepted = server.accept();
            var peer = accepted.peer();
            System.out.println("*** ACCEPTED ***");
            System.out.println("peer pid       : " + peer.pid());
            System.out.println("peer image     : " + peer.imagePath());
            System.out.println("peer started   : " + peer.startInstant());

            var msg = accepted.io().readFrame();
            System.out.println("received frame : " + msg);
            accepted.io().writeFrame("{\"ack\":true,\"sawPid\":" + peer.pid() + "}");
            accepted.io().close();
        }
    }
}
