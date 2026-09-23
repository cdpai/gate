package cdpai.gate.access;

import java.util.*;

/// A snapshot of every visible process on the machine, from which an ancestry chain and live
/// descendant counts can be read -- the same shape ProcTree.java proved works. The count is
/// knowingly a snapshot (the design doc accepts this: a terminal that grows from one tab to forty
/// while a grant is live is a known, accepted looseness -- the anchor still dies when it closes).
public final class ProcessTree {

    final Map<Long, List<ProcessHandle>> kids = new HashMap<>();

    public static ProcessTree snapshot() {
        var tree = new ProcessTree();
        for (var p : ProcessHandle.allProcesses().toList())
            p.parent().ifPresent(par -> tree.kids.computeIfAbsent(par.pid(), k -> new ArrayList<>()).add(p));
        return tree;
    }

    public int descendantCount(long pid) {
        var n = 0;
        var stack = new ArrayDeque<Long>();
        stack.push(pid);
        while (!stack.isEmpty())
            for (var c : kids.getOrDefault(stack.pop(), List.of())) { n++; stack.push(c.pid()); }
        return n;
    }

    public List<String> childImageNames(long pid) {
        return kids.getOrDefault(pid, List.of()).stream()
            .map(c -> c.info().command().orElse("").toLowerCase())
            .map(c -> { var i = c.lastIndexOf('\\'); return i < 0 ? c : c.substring(i + 1); })
            .toList();
    }

    /// One link of a parent-pid walk, with nothing decorated on yet.
    public record RawLink(long pid, String imagePath, java.util.Optional<java.time.Instant> startInstant) {}

    /// The pure parent-pid walk, with NO full-system enumeration first -- call this before
    /// ProcessTree.snapshot(), not after. Measured on this machine: allProcesses() (which snapshot()
    /// needs, to compute descendant counts) takes 60-70ms even with only ~400 processes running.
    /// That is long enough for a genuinely short-lived shell wrapper -- one that exists only to
    /// fork/exec the next stage of a pipeline -- to have already exited by the time the walk would
    /// otherwise begin, permanently truncating the chain above it (a dead process's parent can no
    /// longer be queried at all, by anyone). Reproduced directly: the same command, walked
    /// immediately, reached the real session root seven hops up; walked after a snapshot, it broke
    /// after two hops. The chain is the part of this design that must not be shortened by an
    /// avoidable delay; descendant counts are already an accepted snapshot per the design doc, so
    /// they can safely be computed second.
    ///
    /// Where the Windows chain breaks at an MSYS or Cygwin program, whose emulated fork and exec
    /// leave dead Windows parents behind, it is continued through that installation's own process
    /// table (MsysProcessTable) to the nearest live parent.
    public static List<RawLink> rawChain(long pid) {
        var chain = new ArrayList<RawLink>();
        var seen = new HashSet<Long>();
        var h = ProcessHandle.of(pid);
        while (h.isPresent() && seen.add(h.get().pid())) {
            var info = h.get().info();
            var image = info.command().orElse("(unknown)");
            chain.add(new RawLink(h.get().pid(), image, info.startInstant()));
            var next = h.get().parent();
            var child = chain.size() > 1 ? chain.get(chain.size() - 2).pid() : -1L;
            if (next.isEmpty()) next = MsysProcessTable.parentOf(h.get().pid(), image, child).filter(p -> !seen.contains(p)).flatMap(ProcessHandle::of);
            h = next;
        }
        return chain;
    }

    /// Attaches this snapshot's descendant counts to an already-captured raw chain.
    public List<AncestryNode> decorate(List<RawLink> raw) {
        return raw.stream()
            .map(l -> new AncestryNode(l.pid(), l.imagePath(), l.startInstant(), descendantCount(l.pid())))
            .toList();
    }

    /// Convenience for read-only/diagnostic callers that don't care about the ordering above
    /// (there is already a snapshot in hand, or the timing sensitivity doesn't apply). The real
    /// connection-handling path in GateServer does NOT use this -- it calls rawChain() first.
    public List<AncestryNode> chainFrom(long pid) { return decorate(rawChain(pid)); }
}
