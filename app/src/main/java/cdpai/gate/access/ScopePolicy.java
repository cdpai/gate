package cdpai.gate.access;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/// Enforcement of a Scope over CDP traffic, as pure functions so it is testable without a browser.
/// Domains match a target url's host exactly or as a subdomain, never as a pattern. Profiles are
/// held as on-disk folder names; `dirOf` turns a target's per-launch browserContextId into the
/// folder, and a context it cannot name is outside every profile scope -- unknown fails closed.
public final class ScopePolicy {

    public record TargetMeta(String url, String type, String browserContextId) {}

    /// Checked before a command goes upstream: where it may navigate (createTarget, navigate),
    /// what it may attach to (by the target's known url and profile), and it may not make fresh
    /// browser contexts to escape a profile scope. Everything else passes through.
    public static Optional<String> deny(String method, JsonNode params, Scope scope, Map<String, TargetMeta> known,
                                        Function<String, String> dirOf) {
        if (scope.unscoped()) return Optional.empty();
        return switch (method) {
            case "Target.createTarget", "Page.navigate" -> {
                var url = params.path("url").asText(null);
                if (url != null && !allowsUrl(scope, url)) yield Optional.of("cdpgate: url not in the approved scope: " + url);
                var ctx = params.path("browserContextId").asText(null);
                yield ctx != null && !allowsProfile(scope, dirOf.apply(ctx))
                    ? Optional.of("cdpgate: profile not in the approved scope") : Optional.empty();
            }
            case "Target.attachToTarget", "Target.activateTarget", "Target.closeTarget", "Target.getTargetInfo" -> {
                var meta = known.get(params.path("targetId").asText(""));
                yield meta == null || !allowsTarget(scope, meta, dirOf)
                    ? Optional.of("cdpgate: target not in the approved scope") : Optional.empty();
            }
            case "Target.createBrowserContext", "Target.disposeBrowserContext", "Browser.close" ->
                Optional.of("cdpgate: " + method + " is not available to a scoped connection");
            default -> Optional.empty();
        };
    }

    public static boolean allowsUrl(Scope scope, String url) {
        if (!scope.domainsScoped()) return true;
        if (url.isEmpty() || url.startsWith("about:")) return true;
        var host = host(url);
        return host != null && scope.domains().stream().anyMatch(d -> matchesHost(host, d));
    }

    public static boolean allowsProfile(Scope scope, String dir) {
        return !scope.profilesScoped() || (dir != null && scope.profiles().contains(dir));
    }

    public static boolean allowsTarget(Scope scope, TargetMeta meta, Function<String, String> dirOf) {
        return allowsUrl(scope, meta.url()) && allowsProfile(scope, dirOf.apply(meta.browserContextId()));
    }

    /// An out-of-scope target is invisible to a scoped consumer, not merely unreachable.
    public static JsonNode filterTargetInfos(JsonNode infos, Scope scope, ObjectMapper mapper, Function<String, String> dirOf) {
        if (scope.unscoped() || !infos.isArray()) return infos;
        var out = mapper.createArrayNode();
        for (var t : infos) if (allowsTarget(scope, metaOf(t), dirOf)) out.add(t);
        return out;
    }

    public static boolean allowsTargetInfo(JsonNode info, Scope scope, Function<String, String> dirOf) {
        return scope.unscoped() || allowsTarget(scope, metaOf(info), dirOf);
    }

    public static TargetMeta metaOf(JsonNode info) {
        return new TargetMeta(info.path("url").asText(""), info.path("type").asText(""),
            info.hasNonNull("browserContextId") ? info.get("browserContextId").asText() : null);
    }

    static boolean matchesHost(String host, String domain) {
        var h = host.toLowerCase(Locale.ROOT);
        var d = domain.toLowerCase(Locale.ROOT);
        return h.equals(d) || h.endsWith("." + d);
    }

    static String host(String url) {
        try { return new URI(url).getHost(); } catch (Exception e) { return null; }
    }

    private ScopePolicy() {}
}
