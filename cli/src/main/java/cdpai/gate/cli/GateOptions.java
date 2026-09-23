package cdpai.gate.cli;

import java.util.List;

import picocli.CommandLine.Option;

import cdpai.gate.client.GateClient;
import cdpai.gate.client.GateKey;
import cdpai.gate.client.ScopeRequest;

/// How a cdpg command asks cdpgate for access. Scope what you need: `--profiles` and `--domains`
/// narrow the approval and let it run longer; with neither, the approval is short and the human
/// must also type the passphrase. List the profiles first with `cdpg profiles`, which needs no
/// approval.
final class GateOptions {

    @Option(names = "--pipe", defaultValue = GateClient.PIPE_NAME, description = "cdpgate's pipe name (default: ${DEFAULT-VALUE})")
    String pipe;

    @Option(names = "--app", defaultValue = "cdpg", description = "Name shown to the human in the approval window (default: ${DEFAULT-VALUE})")
    String app;

    @Option(names = "--profiles", split = ",", description = "Profiles by name or folder, as `cdpg profiles` lists them, e.g. --profiles Work,\"Profile 3\"")
    List<String> profiles;

    @Option(names = "--domains", split = ",", description = "Plain hosts, subdomains included, no wildcards, e.g. --domains youtube.com,google.com")
    List<String> domains;

    @Option(names = "--minutes", description = "How long you want the approval for; shown to the human, capped by cdpgate")
    Integer minutes;

    @Option(names = "--key", description = "Use (or create) the keyed identity ~/cdpai/gate/keys/<name>.json instead of the process tree."
        + " For long-running apps; a keyed approval scoped by profiles and domains can last 30 days.")
    String key;

    ScopeRequest scope() { return new ScopeRequest(domains, profiles, minutes); }

    GateClient connect() {
        if (profiles == null && domains == null)
            System.err.println("cdpg: asking for every profile and every website. The approval will be short and needs the passphrase;"
                + " pass --profiles and --domains to ask for less (list profiles with: cdpg profiles)");
        return GateClient.connect(pipe, app, scope(), key == null ? null : GateKey.loadOrCreate(key));
    }
}
