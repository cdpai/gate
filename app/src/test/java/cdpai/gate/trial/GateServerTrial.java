package cdpai.gate.trial;

import cdpai.gate.GateServer;
import cdpai.gate.access.ConsoleApproval;
import cdpai.gate.win.VivaldiLauncher;

/// The real GateServer, running against a scratch Vivaldi. Approval prompts go to stdin/stdout
/// as usual -- for automated testing, pipe an endless stream of blank lines in (each one accepts
/// the suggested anchor) so the REAL approval code path still runs rather than being bypassed.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> cdpai.gate.trial.GateServerTrial <vivaldi.exe> <scratch-user-data-dir> <pipe-name>
public class GateServerTrial {
    public static void main(String[] args) throws Exception {
        var exe = args[0];
        var scratchProfile = args[1];
        var pipeName = args[2];

        System.out.println("launching (scratch profile): " + scratchProfile);
        var vivaldi = VivaldiLauncher.launch(exe, scratchProfile);
        System.out.println("vivaldi pid: " + vivaldi.pid);

        var server = new GateServer(vivaldi, pipeName, new ConsoleApproval());
        System.out.println("cdpgate listening on \\\\.\\pipe\\" + pipeName + " ...");
        server.run();
    }
}
