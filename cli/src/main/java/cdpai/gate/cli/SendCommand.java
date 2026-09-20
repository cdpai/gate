package cdpai.gate.cli;

import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import cdpai.gate.client.GateClient;

/// One CDP method call, one matching reply, printed to stdout as JSON. This is the whole shape an
/// agent needs: "cdpg send Browser.getVersion" or "cdpg send Page.navigate '{"url":"..."}'".
/// A fresh pipe connection per call, same as any consumer -- the FIRST call from a given
/// (executable, anchor) pair prompts for approval, every later one within the granted window
/// passes straight through (GateClient handles none of this; it is entirely cdpgate's own
/// re-attestation, which is exactly the point: this CLI needed no session/token logic at all).
@Command(name = "send", description = "Send one CDP method call and print the matching reply.")
final class SendCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "CDP method, e.g. Browser.getVersion") String method;
    @Parameters(index = "1", arity = "0..1", defaultValue = "{}", description = "params, as a JSON object")
    String paramsJson;
    @Option(names = "--session", description = "sessionId to target a specific attached session") String sessionId;
    @Option(names = "--pipe", defaultValue = GateClient.PIPE_NAME) String pipe;
    @Option(names = "--timeout", defaultValue = "10000", description = "milliseconds to wait for the reply")
    long timeoutMs;

    @Override public Integer call() throws Exception {
        var mapper = new ObjectMapper();
        var req = mapper.createObjectNode();
        req.put("id", 1);
        req.put("method", method);
        req.set("params", mapper.readTree(paramsJson));
        if (sessionId != null) req.put("sessionId", sessionId);

        try (var client = new GateClient(pipe)) {
            client.send(req.toString());

            var watchdog = new Thread(() -> {
                try { Thread.sleep(timeoutMs); } catch (InterruptedException ignored) { return; }
                client.cancelPendingIo();
            });
            watchdog.setDaemon(true);
            watchdog.start();

            try {
                String raw;
                while ((raw = client.receive()) != null) {
                    var obj = mapper.readTree(raw);
                    if (obj.has("id") && obj.get("id").asLong() == 1) {
                        watchdog.interrupt();
                        System.out.println(obj.toString());
                        return obj.has("error") ? 1 : 0;
                    }
                }
            } catch (Exception e) {
                System.err.println("no reply within " + timeoutMs + "ms (or connection closed): " + e.getMessage());
                return 2;
            }
            System.err.println("connection closed with no reply");
            return 2;
        }
    }
}
