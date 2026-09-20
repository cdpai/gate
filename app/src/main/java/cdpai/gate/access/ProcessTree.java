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

    /// From the given pid up to the root (the process with no visible parent), nearest first.
    public List<AncestryNode> chainFrom(long pid) {
        var chain = new ArrayList<AncestryNode>();
        for (var h = ProcessHandle.of(pid); h.isPresent(); h = h.get().parent()) {
            var p = h.get();
            var info = p.info();
            chain.add(new AncestryNode(p.pid(), info.command().orElse("(unknown)"),
                info.startInstant(), descendantCount(p.pid())));
        }
        return chain;
    }
}
