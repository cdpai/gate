package cdpai.gate.trial;

import cdpai.gate.win.VivaldiLauncher;

/// Manual proof that VivaldiLauncher (the real code, not PipeSpike.java) still launches Vivaldi
/// in pipe mode and lets Java own fd3/fd4. Uses a SCRATCH profile only -- this must never touch
/// the user's real Vivaldi session, which is a separate, deliberate cutover step for later.
/// java --enable-native-access=ALL-UNNAMED -cp <classes> cdpai.gate.trial.VivaldiLaunchTrial <vivaldi.exe> <scratch-user-data-dir>
public class VivaldiLaunchTrial {
    public static void main(String[] args) throws Exception {
        var exe = args[0];
        var scratchProfile = args[1];
        System.out.println("launching (scratch profile): " + scratchProfile);

        var vivaldi = VivaldiLauncher.launch(exe, scratchProfile);
        System.out.println("child pid: " + vivaldi.pid);
        System.out.println("isAlive : " + vivaldi.isAlive());

        vivaldi.cdp.writeFrame("{\"id\":1,\"method\":\"Browser.getVersion\"}");
        var reply = vivaldi.cdp.readFrame();
        System.out.println("*** CDP REPLY OVER OWNED FD3/FD4 ***");
        System.out.println(reply.length() > 400 ? reply.substring(0, 400) + " ..." : reply);

        System.out.println("shutting down gracefully...");
        vivaldi.shutdownGracefully(8000);
        Thread.sleep(1500);
        System.out.println("isAlive after shutdown: " + vivaldi.isAlive());
        vivaldi.close();
    }
}
