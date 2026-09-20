package cdpai.gate.trial;

import cdpai.gate.win.NamedPipeServer;

/// Isolates one hypothesis: does WriteFile on an accepted named-pipe handle work when called
/// from a DIFFERENT thread than the one that ran accept()/ConnectNamedPipe? Read happens on the
/// accepting thread (proven already); write is handed to a brand new thread here.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> cdpai.gate.trial.CrossThreadWriteTrial
public class CrossThreadWriteTrial {
    public static void main(String[] args) throws Exception {
        try (var server = new NamedPipeServer("cdpai-gate-crossthread-trial")) {
            System.out.println("listening...");
            var accepted = server.accept();
            System.out.println("accepted, peer pid " + accepted.peer().pid());

            var msg = accepted.io().readFrame();
            System.out.println("read from client (on accept thread): " + msg);

            var writer = new Thread(() -> {
                System.out.println("writer thread: about to writeFrame...");
                accepted.io().writeFrame("{\"from\":\"a different thread\"}");
                System.out.println("writer thread: writeFrame RETURNED");
            }, "cross-thread-writer");
            writer.start();
            writer.join(10000);
            System.out.println("writer thread alive after 10s join? " + writer.isAlive());
        }
    }
}
