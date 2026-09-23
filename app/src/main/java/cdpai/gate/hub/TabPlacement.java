package cdpai.gate.hub;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.access.Scope;

/// Where a new tab goes. Measured on Vivaldi under the pipe: Target.createTarget puts a tab in the
/// browser's current default context -- the profile used last -- and accepts an explicit
/// browserContextId only when it IS that default ("Failed to find browser context" for any other
/// real profile). So a tab meant for the default profile is created normally, and a tab meant for
/// any other profile is opened by hand-off -- `vivaldi.exe --profile-directory=<dir> <url>`, which
/// works for every profile but brings its window forward -- and the reply names the tab that appeared.
/// A profile-scoped consumer that names no context gets its first profile.
public final class TabPlacement {

    final Supplier<String> defaultContext;
    final Function<String, String> dirOf, contextOf;
    final BiFunction<String, String, CompletableFuture<String>> openByHandoff;

    public TabPlacement(Supplier<String> defaultContext, Function<String, String> dirOf, Function<String, String> contextOf,
                        BiFunction<String, String, CompletableFuture<String>> openByHandoff) {
        this.defaultContext = defaultContext;
        this.dirOf = dirOf;
        this.contextOf = contextOf;
        this.openByHandoff = openByHandoff;
    }

    /// Adjusts a createTarget in place; returns a future when cdpgate opens the tab itself and the
    /// message must not go upstream. Runs on the requesting consumer's own thread, so asking the
    /// browser for its default context here blocks nobody else.
    CompletableFuture<String> place(ObjectNode msg, Scope scope) {
        if (!msg.path("method").asText("").equals("Target.createTarget") || !(msg.path("params") instanceof ObjectNode p)) return null;
        var ctx = p.path("browserContextId").asText(null);
        if (ctx == null && scope.profilesScoped() && !scope.profiles().isEmpty()) ctx = contextOf.apply(scope.profiles().getFirst());
        if (ctx == null) return null;
        if (ctx.equals(defaultContext.get())) { p.put("browserContextId", ctx); return null; }
        return openByHandoff.apply(dirOf.apply(ctx), p.path("url").asText("about:blank"));
    }
}
