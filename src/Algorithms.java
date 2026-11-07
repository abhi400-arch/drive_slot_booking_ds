import java.util.*;

/** Greedy, dynamic programming and backtracking modules, all working on real booking data. */
class Algorithms {
    static final String RULE = "Transport licence tests are held in the morning only (09:00-12:00).";

    static boolean allowed(int slotIdx, String license) {
        return !"Transport".equals(license) || slotIdx <= 3;
    }

    // ---------------------------------------------------------------- scenario
    static class Candidate {
        int id; String name, license, preferred;
        Candidate(int id, String name, String license, String preferred) { this.id = id; this.name = name; this.license = license; this.preferred = preferred; }
        int prefIdx() { return DataStore.SLOTS.indexOf(preferred); }
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("name", name); m.put("licenseType", license); m.put("preferred", preferred);
            return m;
        }
    }

    static class Scenario {
        boolean sample; String rto, date; List<Candidate> people = new ArrayList<>(); int[] free;
        Map<String, Object> info() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", sample ? "sample" : "live");
            m.put("note", sample ? "No waiting applicants for this centre/date, so a built-in sample is used."
                                 : "Using the real waiting queue for this centre and date.");
            m.put("rto", rto); m.put("date", date);
            m.put("applicants", people.stream().map(Candidate::toMap).toList());
            m.put("slots", slotTable(free));
            m.put("rule", RULE);
            return m;
        }
    }

    static List<Map<String, Object>> slotTable(int[] free) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < free.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", DataStore.SLOTS.get(i)); m.put("free", free[i]); m.put("booked", DataStore.CAPACITY - free[i]);
            out.add(m);
        }
        return out;
    }

    static Scenario scenario(DataStore s, String rto, String date) {
        DataStore.checkRto(rto);
        DataStore.parseDate(date);
        Scenario sc = new Scenario();
        sc.rto = rto; sc.date = date;
        for (Applicant a : s.waitingApplicants())
            if (a.rto.equals(rto) && a.date.equals(date)) sc.people.add(new Candidate(a.id, a.name, a.licenseType, a.time));
        if (sc.people.isEmpty()) {
            sc.sample = true;
            sc.free = new int[]{0, 0, 0, 1, 1, 0};
            sc.people.add(new Candidate(-1, "Sample A", "LMV", "12:00"));
            sc.people.add(new Candidate(-2, "Sample B", "Transport", "14:00"));
        } else sc.free = s.freeCapacity(rto, date);
        return sc;
    }

    // ---------------------------------------------------------------- greedy
    /** Greedy: for each applicant in queue order, take the free allowed slot closest to the preferred time. */
    static Map<String, Object> greedy(Scenario sc) {
        int[] free = sc.free.clone();
        List<Map<String, Object>> steps = new ArrayList<>();
        int placed = 0;
        for (Candidate c : sc.people) {
            int best = -1;
            for (int i = 0; i < free.length; i++) {
                if (free[i] <= 0 || !allowed(i, c.license)) continue;
                if (best < 0 || Math.abs(i - c.prefIdx()) < Math.abs(best - c.prefIdx())) best = i;
            }
            Map<String, Object> st = new LinkedHashMap<>(c.toMap());
            if (best >= 0) { free[best]--; placed++; st.put("assigned", DataStore.SLOTS.get(best)); }
            else st.put("assigned", null);
            steps.add(st);
        }
        Map<String, Object> out = new LinkedHashMap<>(sc.info());
        out.put("algorithm", "greedy");
        out.put("steps", steps);
        out.put("placed", placed);
        out.put("total", sc.people.size());
        out.put("freeAfter", slotTable(free));
        out.put("explanation", "Greedy makes the locally best choice (nearest free slot) for each applicant and never reconsiders it."
                + (placed < sc.people.size() ? " Here an early choice used up a seat that a later applicant needed, so someone stays unplaced - backtracking fixes this." : ""));
        return out;
    }

    // ---------------------------------------------------------------- dynamic programming
    /** DP: minimum number of time slots whose free seats cover all waiting applicants (0/1 cover problem). */
    static Map<String, Object> dp(Scenario sc) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < sc.free.length; i++) if (sc.free[i] > 0) idx.add(i);
        int m = idx.size(), n = sc.people.size();
        final int INF = 1_000_000;
        int[][] t = new int[m + 1][n + 1];
        for (int[] row : t) Arrays.fill(row, INF);
        t[0][0] = 0;
        for (int i = 1; i <= m; i++) {
            int cap = sc.free[idx.get(i - 1)];
            for (int j = 0; j <= n; j++) {
                t[i][j] = t[i - 1][j];                               // skip this slot
                int rest = Math.max(0, j - cap);                      // use this slot: reuse earlier result
                if (t[i - 1][rest] + 1 < t[i][j]) t[i][j] = t[i - 1][rest] + 1;
            }
        }
        boolean feasible = t[m][n] < INF;
        List<String> chosen = new ArrayList<>();
        if (feasible) {
            int j = n;
            for (int i = m; i >= 1; i--) {
                if (t[i][j] != t[i - 1][j]) { chosen.add(DataStore.SLOTS.get(idx.get(i - 1))); j = Math.max(0, j - sc.free[idx.get(i - 1)]); }
            }
            Collections.reverse(chosen);
        }
        List<List<Object>> table = new ArrayList<>();
        for (int i = 0; i <= m; i++) {
            List<Object> row = new ArrayList<>();
            for (int j = 0; j <= n; j++) row.add(t[i][j] >= INF ? "∞" : t[i][j]);
            table.add(row);
        }
        List<String> rowLabels = new ArrayList<>(); rowLabels.add("no slots");
        for (int i : idx) rowLabels.add("+ " + DataStore.SLOTS.get(i) + " (" + sc.free[i] + " free)");
        Map<String, Object> out = new LinkedHashMap<>(sc.info());
        out.put("algorithm", "dynamic programming");
        out.put("demand", n);
        out.put("feasible", feasible);
        out.put("minSlots", feasible ? t[m][n] : null);
        out.put("chosenSlots", chosen);
        out.put("rowLabels", rowLabels);
        out.put("table", table);
        out.put("explanation", "table[i][j] = fewest slots among the first i usable slots that give at least j free seats. "
                + "Each cell reuses a previously computed cell. Column j = " + n + " is the answer."
                + (feasible ? "" : " The free seats are not enough for all waiting applicants."));
        return out;
    }

    // ---------------------------------------------------------------- backtracking
    private static int nodes, backtracks;
    private static final int NODE_LIMIT = 200_000;

    /** Backtracking: try slots for each applicant, undo (backtrack) when the rest cannot be placed. */
    static synchronized Map<String, Object> backtrack(Scenario sc) {
        int n = sc.people.size();
        int[] rem = sc.free.clone(), assign = new int[n];
        List<List<Integer>> order = new ArrayList<>();
        for (Candidate c : sc.people) {
            List<Integer> o = new ArrayList<>();
            for (int i = 0; i < rem.length; i++) o.add(i);
            o.sort(Comparator.comparingInt((Integer i) -> Math.abs(i - c.prefIdx())).thenComparingInt(i -> i));
            order.add(o);
        }
        nodes = 0; backtracks = 0;
        boolean solved = place(0, sc, order, rem, assign);
        List<Map<String, Object>> steps = new ArrayList<>();
        if (solved)
            for (int i = 0; i < n; i++) {
                Map<String, Object> st = new LinkedHashMap<>(sc.people.get(i).toMap());
                st.put("assigned", DataStore.SLOTS.get(assign[i]));
                steps.add(st);
            }
        Map<String, Object> out = new LinkedHashMap<>(sc.info());
        out.put("algorithm", "backtracking");
        out.put("solved", solved);
        out.put("steps", steps);
        out.put("nodesTried", nodes);
        out.put("backtracks", backtracks);
        out.put("explanation", "Each applicant tries slots from the nearest to the preferred time. If a later applicant cannot be placed, "
                + "the last choice is undone and the next slot is tried. " + RULE
                + (solved ? "" : " No valid arrangement exists with the free seats available."));
        return out;
    }

    private static boolean place(int i, Scenario sc, List<List<Integer>> order, int[] rem, int[] assign) {
        if (i == sc.people.size()) return true;
        if (nodes > NODE_LIMIT) return false;
        Candidate c = sc.people.get(i);
        for (int s : order.get(i)) {
            if (rem[s] <= 0 || !allowed(s, c.license)) continue;
            nodes++;
            rem[s]--; assign[i] = s;
            if (place(i + 1, sc, order, rem, assign)) return true;
            rem[s]++;            // undo the choice
            backtracks++;
        }
        return false;
    }

    /** Backtracking result as applicant id -> time (only for live scenarios). */
    static Map<Integer, String> allocation(Map<String, Object> bt) {
        Map<Integer, String> m = new LinkedHashMap<>();
        @SuppressWarnings("unchecked") List<Map<String, Object>> steps = (List<Map<String, Object>>) bt.get("steps");
        for (Map<String, Object> st : steps) m.put((Integer) st.get("id"), (String) st.get("assigned"));
        return m;
    }

    // ---------------------------------------------------------------- booking suggestions (greedy + graph BFS)
    /** When a slot is full: nearest free slot at the same centre (greedy), then nearby centres found with BFS. */
    static List<Map<String, Object>> suggest(DataStore s, String rto, String date, String time, String license) {
        List<Map<String, Object>> out = new ArrayList<>();
        int pref = DataStore.SLOTS.indexOf(time);
        RtoGraph.Bfs bfs = s.graph.bfs(rto);
        for (String centre : bfs.order) {
            int[] free = s.freeCapacity(centre, date);
            List<Integer> cand = new ArrayList<>();
            for (int i = 0; i < free.length; i++) if (free[i] > 0 && allowed(i, license) && !(centre.equals(rto) && i == pref)) cand.add(i);
            cand.sort(Comparator.comparingInt((Integer i) -> Math.abs(i - pref)).thenComparingInt(i -> i));
            int take = centre.equals(rto) ? 2 : 1;
            for (int k = 0; k < Math.min(take, cand.size()); k++) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rto", centre); m.put("date", date); m.put("time", DataStore.SLOTS.get(cand.get(k)));
                m.put("free", free[cand.get(k)]); m.put("hops", bfs.distance.get(centre));
                m.put("reason", centre.equals(rto) ? "Nearest free slot at the same centre" : "Free slot " + bfs.distance.get(centre) + " hop(s) away");
                out.add(m);
            }
            if (out.size() >= 4) break;
        }
        return out;
    }
}
