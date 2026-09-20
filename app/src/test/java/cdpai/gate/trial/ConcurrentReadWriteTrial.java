package cdpai.gate.trial;

import cdpai.gate.win.NamedPipeServer;

/// Narrower isolation than CrossThreadWriteTrial: starts a SECOND blocking readFrame() (waiting
/// for a message that never comes) on the accept thread BEFORE the writer thread attempts its
/// write on the same duplex handle -- exactly the shape of the real hang (the consumer-handler
/// thread's next readFrame() is already pending when the hub tries to write the reply).
/// java --enable-native-access=ALL-UNNAMED -cp <classes> cdpai.gate.trial.ConcurrentReadWriteTrial
public class ConcurrentReadWriteTrial {
    public static void main(String[] args) throws Exception {
        try (var server = new NamedPipeServer("cdpai-gate-concurrent-trial")) {
            System.out.println("listening...");
            var accepted = server.accept();
            System.out.println("accepted, peer pid " + accepted.peer().pid());

            var msg = accepted.io().readFrame();
            System.out.println("read message 1: " + msg);

            var pendingReader = new Thread(() -> {
                System.out.println("pending-reader: calling readFrame() for message 2 (none coming)...");
                var m2 = accepted.io().readFrame();
                System.out.println("pending-reader: got message 2: " + m2);
            }, "pending-reader");
            pendingReader.start();
            Thread.sleep(500);   // make sure the second readFrame() is genuinely blocked first

            var writer = new Thread(() -> {
                System.out.println("writer: about to writeFrame WHILE a read is pending...");
                accepted.io().writeFrame("{\"reply\":\"to message 1\"}");
                System.out.println("writer: writeFrame RETURNED");
            }, "writer");
            writer.start();
            writer.join(10000);
            System.out.println("writer alive after 10s join? " + writer.isAlive());
        }
    }
}
