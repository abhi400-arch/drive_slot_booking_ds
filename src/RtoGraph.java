import java.util.*;

/** Undirected graph of RTO centres stored as an adjacency list. */
class RtoGraph {
    private final Map<String, List<String>> adj = new LinkedHashMap<>();

    void addVertex(String v) { adj.putIfAbsent(v, new ArrayList<>()); }

    void addEdge(String a, String b) {
        addVertex(a); addVertex(b);
        if (!adj.get(a).contains(b)) adj.get(a).add(b);
        if (!adj.get(b).contains(a)) adj.get(b).add(a);
    }

    boolean has(String v) { return adj.containsKey(v); }
    List<String> neighbours(String v) { return adj.getOrDefault(v, List.of()); }
    Map<String, List<String>> adjacency() { return adj; }

    /** BFS result: visit order plus hop distance from the start vertex. */
    static class Bfs { List<String> order = new ArrayList<>(); Map<String, Integer> distance = new LinkedHashMap<>(); }

    Bfs bfs(String start) {
        Bfs r = new Bfs();
        if (!adj.containsKey(start)) return r;
        Queue<String> q = new ArrayDeque<>();
        q.add(start); r.distance.put(start, 0);
        while (!q.isEmpty()) {
            String u = q.poll();
            r.order.add(u);
            for (String v : adj.get(u)) {
                if (!r.distance.containsKey(v)) { r.distance.put(v, r.distance.get(u) + 1); q.add(v); }
            }
        }
        return r;
    }
}
