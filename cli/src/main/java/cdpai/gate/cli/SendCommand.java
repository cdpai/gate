package cdpai.gate.cli;

import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import cdpai.gate.client.GateClient;
import cdpai.gate.client.GateDeniedException;

/// One CDP call, one reply, printed as JSON on stdout. The first call from a terminal or agent
/// session waits for a human to approve it; later calls from the same session go straight through
/// until the approval ends. The approval itself goes to stderr, so stdout carries only the reply.
@Command(name = "send", description = "Send one CDP method call and print its reply as JSON.")
final class SendCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "CDP method, e.g. Target.getTargets") String method;
    @Parameters(index = "1", arity = "0..1", defaultValue = "{}", description = "params as a JSON object") String paramsJson;
    @Option(names = "--target", description = "targetId (from Target.getTargets) to run the method in: attaches, sends, detaches") String targetId;
    @Option(names = "--timeout", defaultValue = "15000", description = "ms to wait for the reply, counted after approval (default: ${DEFAULT-VALUE})")
    long timeoutMs;
    @Mixin GateOptions gate;

    @Override public Integer call() throws Exception {
        var mapper = new ObjectMapper();
        var req = mapper.createObjectNode().put("id", 1).put("method", method);
        req.set("params", mapper.readTree(paramsJson));
        try (var client = gate.connect()) {
            System.err.println("cdpg: approved " + client.grant());
            if (targetId != null) {
                var sid = attach(client, mapper);
                if (sid == null) return 1;
                req.put("sessionId", sid);
            }
            client.send(req.toString());
            var watchdog = Thread.ofVirtual().start(() -> {
                try { Thread.sleep(timeoutMs); client.cancelPendingIo(); } catch (InterruptedException ignored) {}
            });
            try {
                String raw;
                while ((raw = client.receive()) != null) {
                    var obj = mapper.readTree(raw);
                    if (obj.path("id").asLong(-1) == 1) {
                        watchdog.interrupt();
                        System.out.println(obj);
                        return obj.has("error") ? 1 : 0;
                    }
                }
            } catch (Exception e) {
                System.err.println("cdpg: no reply within " + timeoutMs + " ms: " + e.getMessage());
                return 2;
            }
            System.err.println("cdpg: connection closed with no reply");
            return 2;
        } catch (GateDeniedException e) {
            System.err.println("cdpg: " + e.getMessage());
            return 3;
        }
    }

    String attach(GateClient client, ObjectMapper mapper) throws Exception {
        client.send(mapper.createObjectNode().put("id", 100).put("method", "Target.attachToTarget")
            .set("params", mapper.createObjectNode().put("targetId", targetId).put("flatten", true)).toString());
        String raw;
        while ((raw = client.receive()) != null) {
            var obj = mapper.readTree(raw);
            if (obj.path("id").asLong(-1) != 100) continue;
            if (obj.has("error")) { System.out.println(obj); return null; }
            return obj.path("result").path("sessionId").asText();
        }
        return null;
    }
}
