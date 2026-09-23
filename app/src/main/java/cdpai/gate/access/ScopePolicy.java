package cdpai.gate.access;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

    /// Cookie calls sent to the browser itself (no session) act on one profile's whole jar: the
    /// context named by `browserContextId`, or, when none is named, the profile the browser was
    /// LAUNCHED with -- not the last-used default that createTarget follows. The launch profile
    /// cannot be named by id at all ("Failed to find browser context"), so a call naming it has the
    /// id removed. Both measured on Vivaldi 2026-09-23.
    /// Cookies are scoped by PROFILE, never by domain -- a profile shares one jar across every site
    /// (YouTube needs google.com's account cookies; any site can offer Google sign-in), so a
    /// domain-filtered jar would break real sites in ways that are hard to debug. Domains scope tabs.
    /// Calls on an attached tab's session already act in that tab's (approved) profile.
    static final Set<String> STORAGE_COOKIES = Set.of("Storage.getCookies", "Storage.setCookies", "Storage.clearCookies");
    static final Set<String> NETWORK_COOKIES = Set.of("Network.getAllCookies", "Network.getCookies", "Network.setCookie",
        "Network.setCookies", "Network.deleteCookies", "Network.clearBrowserCookies");

    public static boolean isCookieCall(String method) { return STORAGE_COOKIES.contains(method) || NETWORK_COOKIES.contains(method); }

    /// Keeps a browser-level cookie call inside the approved profiles. A Storage call naming no
    /// profile, when the default context is outside the scope and exactly one profile is approved,
    /// is routed to that profile by writing its context into `params`; otherwise it is refused.
    public static Optional<String> cookieJar(String method, ObjectNode params, Scope scope, String launchContext,
                                             Function<String, String> dirOf, Function<String, String> contextOf) {
        if (!isCookieCall(method)) return Optional.empty();
        var named = params.path("browserContextId").asText(null);
        var namesLaunch = named != null && named.equals(launchContext);
        var jar = named != null ? named : launchContext;
        if (!scope.profilesScoped() || allowsProfile(scope, dirOfNullable(dirOf, jar))) {
            if (namesLaunch) params.remove("browserContextId");
            return Optional.empty();
        }
        if (named != null) return Optional.of("cdpgate: cookies of a profile outside the approved scope");
        if (NETWORK_COOKIES.contains(method))
            return Optional.of("cdpgate: " + method + " on the browser reads the launch profile, which is outside the approved scope;"
                + " use Storage.getCookies (cdpgate routes it to the approved profile) or send it on an attached tab's session");
        var only = scope.profiles().size() == 1 ? contextOf.apply(scope.profiles().get(0)) : null;
        if (only == null) return Optional.of("cdpgate: name the profile -- pass browserContextId of an approved profile");
        params.put("browserContextId", only);
        return Optional.empty();
    }

    static String dirOfNullable(Function<String, String> dirOf, String ctx) { return ctx == null ? null : dirOf.apply(ctx); }

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
