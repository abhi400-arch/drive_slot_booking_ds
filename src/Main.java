import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * HTTP server + REST API for the Driving License Slot Booking Management
 * System.
 */
public class Main {
    static final int PORT = 8080;
    static DataStore store;
    static Path webRoot;
    static Database db;
    /** token -> {username, role} */
    static final Map<String, String[]> sessions = new ConcurrentHashMap<>();

    interface Handler {
        Object handle(Req r) throws Exception;
    }

    static class Req {
        String method, path, user, role;
        Map<String, String> query = new HashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();

        /** value from the query string, falling back to the JSON body */
        String q(String k, String def) {
            String v = query.get(k);
            if (v == null || v.isBlank())
                v = Json.str(body, k);
            return v == null || v.isBlank() ? def : v.trim();
        }

        void requireAdmin() {
            if (!"ADMIN".equals(role))
                throw new ApiException(403, "Only an administrator can do this.");
        }

        void requireMethod(String... ms) {
            for (String m : ms)
                if (m.equalsIgnoreCase(method))
                    return;
            throw new ApiException(405, "Method not allowed.");
        }

        /** id from the JSON body or the query string */
        int id() {
            String v = Json.str(body, "id");
            if (v.isEmpty())
                v = q("id", "");
            if (!v.matches("\\d+"))
                throw new ApiException(400, "A numeric applicant id is required.");
            return Integer.parseInt(v);
        }
    }

    static Path findBaseDir() {
        Path[] candidates = new Path[] {
                Paths.get("."),
                Paths.get("final_dsactual"),
                Paths.get(".."),
                Paths.get("..", "final_dsactual")
        };
        for (Path c : candidates) {
            if (Files.isDirectory(c.resolve("web"))) {
                return c.toAbsolutePath().normalize();
            }
        }
        try {
            var loc = Main.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null) {
                Path p = Paths.get(loc.toURI()).toAbsolutePath().normalize();
                while (p != null) {
                    if (Files.isDirectory(p.resolve("web")))
                        return p;
                    if (Files.isDirectory(p.resolve("final_dsactual").resolve("web")))
                        return p.resolve("final_dsactual");
                    p = p.getParent();
                }
            }
        } catch (Exception ignored) {
        }
        return Paths.get(".").toAbsolutePath().normalize();
    }

    public static void main(String[] args) throws Exception {
        Path base = findBaseDir();
        webRoot = base.resolve("web");
        store = new DataStore(base.resolve("data"));
        store.load();
        db = new Database();
        db.initialize();

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
        } catch (java.net.BindException e) {
            System.err.println("Port " + PORT + " is already in use. Stop the other program and run again.");
            return;
        }

        route(server, "/api/login", false, Main::login);
        route(server, "/api/register", false, Main::register);
        route(server, "/api/meta", false, Main::meta);
        route(server, "/api/dashboard", true, Main::dashboard);
        route(server, "/api/applicants", true, Main::applicants);
        route(server, "/api/book", true, Main::book);
        route(server, "/api/cancel", true, Main::cancel);
        pdfRoute(server, "/api/confirmation-pdf");
        route(server, "/api/slots", true, Main::slots);
        route(server, "/api/search", true, Main::search);
        route(server, "/api/sort", true, Main::sort);
        route(server, "/api/compare", true, Main::compare);
        route(server, "/api/dsa", true, Main::dsaOverview);
        route(server, "/api/dsa/expression", true, Main::expression);
        route(server, "/api/dsa/bfs", true, Main::bfs);
        route(server, "/api/dsa/dfs", true, Main::dfs);
        route(server, "/api/smart/document-check", true, Main::documentCheck);
        route(server, "/api/smart/slot-score", true, Main::slotScore);
        route(server, "/api/smart/wait-time", true, Main::waitTime);
        route(server, "/api/dsa/heap", true, Main::heap);
        route(server, "/api/algorithms/", true, Main::algorithms);
        server.createContext("/", Main::staticFiles);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Driving License Slot Booking System running at http://localhost:" + PORT);
        System.out.println("Web folder: " + webRoot + "   Data folder: " + base.resolve("data"));
    }

    // ------------------------------------------------------------------ plumbing

    static void pdfRoute(HttpServer server, String path) {
        server.createContext(path, ex -> {
            try {
                String token = ex.getRequestHeaders().getFirst("X-Token");
                String[] s = sessions.get(token == null ? "" : token);
                if (s == null) {
                    send(ex, 401, Json.write(err("Please sign in again.")));
                    return;
                }
                String raw = ex.getRequestURI().getRawQuery();
                int id = -1;
                if (raw != null)
                    for (String kv : raw.split("&")) {
                        int i = kv.indexOf('=');
                        String k = URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), StandardCharsets.UTF_8);
                        String v = i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
                        if ("id".equals(k) && v.matches("\\d+"))
                            id = Integer.parseInt(v);
                    }
                if (id < 0) {
                    send(ex, 400, Json.write(err("A numeric applicant id is required.")));
                    return;
                }
                Applicant a = store.find(id);
                if (a == null) {
                    send(ex, 404, Json.write(err("Applicant not found.")));
                    return;
                }
                if (!"ADMIN".equals(s[1]) && !DataStore.isOwner(a, s[0])) {
                    send(ex, 403, Json.write(err("Access denied: You are only authorized to download your own appointment pass.")));
                    return;
                }
                if (!"CONFIRMED".equals(a.status)) {
                    send(ex, 409, Json.write(err("A slot confirmation PDF is available only for confirmed bookings.")));
                    return;
                }
                byte[] pdf = confirmationPdf(a);
                ex.getResponseHeaders().set("Content-Type", "application/pdf");
                ex.getResponseHeaders().set("Content-Disposition",
                        "attachment; filename=DriveSlot-Confirmation-" + a.id + ".pdf");
                ex.getResponseHeaders().set("Cache-Control", "no-store");
                ex.sendResponseHeaders(200, pdf.length);
                try (OutputStream o = ex.getResponseBody()) {
                    o.write(pdf);
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (!ex.getResponseHeaders().containsKey("Content-Type"))
                    send(ex, 500, Json.write(err("Could not generate PDF.")));
            }
        });
    }

    static byte[] confirmationPdf(Applicant a) {
        String code = verificationCode(a);
        String[] lines = {
                "DRIVING LICENCE SLOT CONFIRMATION",
                "Digital Slot Pass | DriveSlot",
                "",
                "Booking ID       : DL-" + a.id,
                "Applicant Name   : " + a.name,
                "Phone            : " + maskPhone(a.phone),
                "Licence Type     : " + a.licenseType,
                "RTO Centre       : " + a.rto,
                "Appointment Date : " + a.date,
                "Appointment Time : " + a.time,
                "Status            : CONFIRMED",
                "Verification Code: " + code,
                "",
                "IMPORTANT INSTRUCTIONS",
                "1. Arrive 15 minutes before the scheduled appointment.",
                "2. Carry your original licence/application documents.",
                "3. Show this confirmation at the RTO entry desk.",
                "4. This document is digitally generated by the project.",
                "",
                "Generated by DriveSlot DSA Appointment System"
        };
        StringBuilder content = new StringBuilder();
        content.append("BT /F1 22 Tf 72 750 Td (").append(pdfEsc(lines[0])).append(") Tj /F1 11 Tf 0 -28 Td (")
                .append(pdfEsc(lines[1])).append(") Tj ");
        for (int i = 2; i < lines.length; i++) {
            content.append("0 -22 Td (").append(pdfEsc(lines[i])).append(") Tj ");
        }
        content.append("ET");
        String stream = content.toString();
        String[] objs = new String[5];
        objs[0] = "<< /Type /Catalog /Pages 2 0 R >>";
        objs[1] = "<< /Type /Pages /Kids [3 0 R] /Count 1 >>";
        objs[2] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>";
        objs[3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>";
        objs[4] = "<< /Length " + stream.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + stream
                + "\nendstream";
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objs.length; i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objs[i]).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++)
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
        pdf.append("trailer << /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    static String pdfEsc(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replaceAll("[^\\x20-\\x7E]", "?");
    }

    static String maskPhone(String p) {
        return p == null || p.length() < 4 ? "**********" : "******" + p.substring(p.length() - 4);
    }

    static String verificationCode(Applicant a) {
        int h = Math.abs(Objects.hash(a.id, a.name, a.date, a.time));
        return String.format(Locale.ROOT, "%04X-%02d", h & 0xFFFF, (h / 65536) % 100);
    }

    static void route(HttpServer server, String path, boolean auth, Handler h) {
        server.createContext(path, ex -> {
            int code = 200;
            Object result;
            try {
                Req r = readRequest(ex);
                if (auth) {
                    String[] s = sessions.get(ex.getRequestHeaders().getFirst("X-Token") == null ? ""
                            : ex.getRequestHeaders().getFirst("X-Token"));
                    if (s == null)
                        throw new ApiException(401, "Please sign in again.");
                    r.user = s[0];
                    r.role = s[1];
                }
                result = h.handle(r);
            } catch (ApiException e) {
                code = e.status;
                result = err(e.getMessage());
            } catch (IllegalArgumentException e) {
                code = 400;
                result = err(e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                code = 500;
                result = err("Unexpected server error.");
            }
            send(ex, code, Json.write(result));
        });
    }

    static Map<String, Object> err(String m) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("success", false);
        e.put("error", m);
        return e;
    }

    static Req readRequest(HttpExchange ex) throws IOException {
        Req r = new Req();
        r.method = ex.getRequestMethod().toUpperCase();
        r.path = ex.getRequestURI().getPath();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw != null)
            for (String kv : raw.split("&")) {
                if (kv.isEmpty())
                    continue;
                int i = kv.indexOf('=');
                String k = URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), StandardCharsets.UTF_8);
                String v = i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
                r.query.put(k, v);
            }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        r.body = Json.parseObject(body);
        return r;
    }

    static void send(HttpExchange e, int code, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        e.getResponseHeaders().set("Cache-Control", "no-store");
        e.sendResponseHeaders(code, b.length);
        try (OutputStream o = e.getResponseBody()) {
            o.write(b);
        }
    }

    static void staticFiles(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/"))
            path = "/index.html";
        Path file = webRoot.resolve(path.substring(1)).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            send(ex, 404, Json.write(err("Not found")));
            return;
        }
        String n = file.getFileName().toString();
        String type = n.endsWith(".html") ? "text/html"
                : n.endsWith(".css") ? "text/css"
                        : n.endsWith(".js") ? "application/javascript"
                                : n.endsWith(".svg") ? "image/svg+xml"
                                        : n.endsWith(".png") ? "image/png"
                                                : n.endsWith(".ico") ? "image/x-icon" : "application/octet-stream";
        byte[] b = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
        }
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2)
            m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ------------------------------------------------------------------ handlers
    static String hash(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte v : b)
                out.append(String.format("%02x", v));
            return out.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Object login(Req r) {
        r.requireMethod("POST");
        String u = Json.str(r.body, "username").trim(), p = Json.str(r.body, "password");
        if (u.isBlank() || p.isBlank())
            throw new ApiException(400, "Username and password are required.");
        String[] rec = db.authenticate(u, hash(p));
        if (rec == null)
            throw new ApiException(401, "Invalid username or password.");
        String token = UUID.randomUUID().toString();
        sessions.put(token, new String[] { u, rec[1] });
        return map("success", true, "role", rec[1], "username", u, "token", token);
    }

    static Object register(Req r) {
        r.requireMethod("POST");
        String u = Json.str(r.body, "username").trim(), p = Json.str(r.body, "password");
        if (!u.matches("[A-Za-z][A-Za-z0-9_]{3,19}"))
            throw new ApiException(400, "Username must be 4-20 characters and start with a letter.");
        if (p.length() < 6)
            throw new ApiException(400, "Password must contain at least 6 characters.");
        if (!db.createApplicantUser(u, hash(p)))
            throw new ApiException(409, "Username already exists.");
        return map("success", true, "message", "Applicant account created in MySQL. You can now sign in.");
    }

    static Object meta(Req r) {
        return map("rtoCentres", DataStore.RTOS, "slots", DataStore.SLOTS, "licenseTypes", DataStore.LICENSES,
                "capacity", DataStore.CAPACITY, "graph", store.graph.adjacency(), "rule", Algorithms.RULE,
                "today", LocalDate.now().toString());
    }

    static Object dashboard(Req r) {
        List<Map<String, Object>> perRto = new ArrayList<>();
        List<Applicant> all = store.list();
        for (String rto : DataStore.RTOS) {
            int c = 0, w = 0;
            for (Applicant a : all)
                if (a.rto.equals(rto)) {
                    if ("CONFIRMED".equals(a.status))
                        c++;
                    else if ("WAITING".equals(a.status))
                        w++;
                }
            perRto.add(map("rto", rto, "confirmed", c, "waiting", w, "neighbours", store.graph.neighbours(rto)));
        }
        List<String> recent = store.actions.toList();
        return map("totalApplicants", all.size(), "confirmed", store.count("CONFIRMED"), "waiting",
                store.count("WAITING"),
                "cancelled", store.count("CANCELLED"), "draft", store.count("DRAFT"), "rtoCentres", DataStore.RTOS,
                "perRto", perRto,
                "recentActions", recent.subList(0, Math.min(6, recent.size())));
    }

    static Object applicants(Req r) {
        switch (r.method) {
            case "GET":
                if ("ADMIN".equals(r.role)) {
                    return store.list();
                }
                return store.listForUser(r.user);
            case "POST":
                r.requireAdmin();
                return store.create(r.body);
            case "PUT":
                r.requireAdmin();
                return store.update(r.body);
            case "DELETE": {
                r.requireAdmin();
                List<Applicant> promoted = store.delete(r.id());
                return map("success", true, "promoted", promoted);
            }
            default:
                throw new ApiException(405, "Method not allowed.");
        }
    }

    static Object book(Req r) {
        r.requireMethod("POST");
        DataStore.BookingResult b = store.book(r.body, r.user);
        return map("success", true, "status", b.status, "message", b.message, "applicant", b.applicant, "suggestions",
                b.suggestions);
    }

    static Object cancel(Req r) {
        r.requireMethod("POST");
        Applicant a = store.find(r.id());
        if (a == null)
            throw new ApiException(404, "Applicant not found.");
        if (!"ADMIN".equals(r.role) && !DataStore.isOwner(a, r.user))
            throw new ApiException(403, "Access denied: You can only cancel your own booking.");
        DataStore.BookingResult b = store.cancel(r.id());
        return map("success", true, "status", b.status, "message", b.message, "applicant", b.applicant, "promoted",
                b.promoted);
    }

    static Object slots(Req r) {
        String rto = r.q("rto", DataStore.RTOS.get(0)), date = r.q("date", LocalDate.now().toString());
        DataStore.checkRto(rto);
        DataStore.parseDate(date);
        return Algorithms.slotTable(store.freeCapacity(rto, date));
    }

    /**
     * Build a balanced BST from applicants (insert the middle id first, then
     * recurse on both halves).
     */
    static BST buildBst(List<Applicant> list) {
        List<Applicant> sorted = Sorting.mergeSort(list, Sorting.by("id")).sorted;
        BST t = new BST();
        insertMid(t, sorted, 0, sorted.size() - 1);
        return t;
    }

    private static void insertMid(BST t, List<Applicant> a, int lo, int hi) {
        if (lo > hi)
            return;
        int mid = (lo + hi) >>> 1;
        t.insert(a.get(mid));
        insertMid(t, a, lo, mid - 1);
        insertMid(t, a, mid + 1, hi);
    }

    static Object search(Req r) {
        String mode = r.q("mode", "linear"), field = r.q("field", "name"), q = r.q("q", "");
        if (q.isEmpty())
            throw new ApiException(400, "Enter a value to search for.");
        List<Applicant> list = store.list();
        Map<String, Object> out = map("mode", mode, "field", field, "query", q, "total", list.size());
        switch (mode) {
            case "linear": {
                Searching.Result s = Searching.linear(list, field, q);
                out.put("matches", s.matches);
                out.put("comparisons", s.comparisons);
                out.put("note", s.note);
                out.put("complexity", "O(n)");
                break;
            }
            case "trie": {
                Trie t = Trie.from(list);
                Searching.Result tr = new Searching.Result();
                tr.matches.addAll(t.prefix(q));
                tr.comparisons = q.length();
                tr.note = "Followed the prefix characters through a Trie; no full record scan is required.";
                out.put("matches", tr.matches);
                out.put("comparisons", tr.comparisons);
                out.put("note", tr.note);
                out.put("complexity", "O(p + k), p = prefix length, k = returned matches");
                break;
            }
            case "binary": {
                if (!List.of("id", "name", "phone").contains(field))
                    throw new ApiException(400, "Binary search works on id, name or phone.");
                Comparator<Applicant> c = Sorting.by(field);
                Sorting.Result sorted = Sorting.mergeSort(list, c);
                Applicant probe = new Applicant(0, "", "", "", "", "", "", "");
                if (field.equals("id")) {
                    if (!q.matches("\\d+"))
                        throw new ApiException(400, "Applicant id must be a number.");
                    probe.id = Integer.parseInt(q);
                } else if (field.equals("name"))
                    probe.name = q;
                else
                    probe.phone = q;
                Searching.Result s = Searching.binary(sorted.sorted, c, probe);
                out.put("matches", s.matches);
                out.put("comparisons", s.comparisons);
                out.put("sortComparisons", sorted.comparisons);
                out.put("note", s.note + " Exact match.");
                out.put("complexity", "O(log n) search after an O(n log n) sort");
                break;
            }
            case "bst": {
                if (!field.equals("id"))
                    throw new ApiException(400, "BST search works on the applicant id.");
                if (!q.matches("\\d+"))
                    throw new ApiException(400, "Applicant id must be a number.");
                BST t = buildBst(list);
                BST.SearchResult s = t.search(Integer.parseInt(q));
                out.put("matches", s.found == null ? List.of() : List.of(s.found));
                out.put("comparisons", s.path.size());
                out.put("path", s.path);
                out.put("treeHeight", t.height());
                out.put("note",
                        "Walked down the tree: go left if the id is smaller, right if larger. Path of ids visited is shown.");
                out.put("complexity", "O(log n) on a balanced tree");
                break;
            }
            default:
                throw new ApiException(400, "Mode must be linear, binary or bst.");
        }
        return out;
    }

    static Object sort(Req r) {
        String key = r.q("key", "id"), algo = r.q("algo", "merge"), order = r.q("order", "asc");
        Comparator<Applicant> c = Sorting.by(key);
        if (order.equals("desc"))
            c = c.reversed();
        List<Applicant> list = store.list();
        Sorting.Result s;
        if (algo.equals("merge"))
            s = Sorting.mergeSort(list, c);
        else if (algo.equals("insertion"))
            s = Sorting.insertionSort(list, c);
        else
            throw new ApiException(400, "Algorithm must be merge or insertion.");
        return map("algorithm", algo, "key", key, "order", order, "comparisons", s.comparisons, "moves", s.moves,
                "millis", s.millis, "records", s.sorted);
    }

    static Object compare(Req r) {
        String key = r.q("key", "id");
        Comparator<Applicant> c = Sorting.by(key);
        List<Applicant> list = store.list();
        Sorting.Result m = Sorting.mergeSort(list, c), i = Sorting.insertionSort(list, c);
        // run on the already-sorted data too, to show insertion sort's best case
        Sorting.Result m2 = Sorting.mergeSort(m.sorted, c), i2 = Sorting.insertionSort(m.sorted, c);
        int n = list.size();
        return map("key", key, "n", n,
                "merge",
                map("comparisons", m.comparisons, "moves", m.moves, "millis", m.millis, "comparisonsOnSorted",
                        m2.comparisons, "complexity", "O(n log n) always"),
                "insertion",
                map("comparisons", i.comparisons, "moves", i.moves, "millis", i.millis, "comparisonsOnSorted",
                        i2.comparisons, "complexity", "O(n^2) worst, O(n) best"),
                "linearSearchWorst", n, "binarySearchWorst",
                n == 0 ? 0 : (int) (Math.floor(Math.log(n) / Math.log(2)) + 1));
    }

    static Object dsaOverview(Req r) {
        List<Applicant> all = store.list();
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Applicant a : all)
            nodes.add(map("id", a.id, "name", a.name, "status", a.status));
        List<String> stack = store.actions.toList();
        List<Map<String, Object>> queue = new ArrayList<>();
        for (Applicant a : store.waitingApplicants())
            queue.add(map("id", a.id, "name", a.name, "rto", a.rto, "date", a.date, "time", a.time));

        BinaryTree bt = new BinaryTree();
        for (String rto : DataStore.RTOS)
            bt.insertLevelOrder(rto);
        BST bst = buildBst(all);
        RtoGraph.Bfs bfs = store.graph.bfs(DataStore.RTOS.get(0));

        return map(
                "linkedList", map("size", all.size(), "nodes", nodes),
                "stack",
                map("size", stack.size(), "top", stack.isEmpty() ? null : stack.get(0), "items",
                        stack.subList(0, Math.min(15, stack.size()))),
                "queue", map("size", queue.size(), "items", queue),
                "binaryTree", map("size", bt.size(), "height", bt.height(), "tree", bt.toMap(),
                        "preorder", bt.preorder(), "inorder", bt.inorder(), "postorder", bt.postorder()),
                "bst", map("size", bst.size(), "height", bst.height(), "tree", bst.toMap(),
                        "preorder", bst.preorder(), "inorder", bst.inorder(), "postorder", bst.postorder()),
                "graph",
                map("adjacency", store.graph.adjacency(), "start", DataStore.RTOS.get(0), "order", bfs.order,
                        "distance", bfs.distance),
                "heap", heap(defaultHeapRequest()),
                "expression", expression(defaultExpr()));
    }

    static Req defaultHeapRequest() {
        Req q = new Req();
        q.query.put("rto", DataStore.RTOS.get(0));
        q.query.put("date", LocalDate.now().plusDays(1).toString());
        return q;
    }

    static Req defaultExpr() {
        Req q = new Req();
        q.query.put("expr", "(8 + 2) * 3 - 4");
        return q;
    }

    static Object expression(Req r) {
        String expr = r.q("expr", "");
        ExpressionEvaluator.Result e = ExpressionEvaluator.evaluate(expr);
        return map("expression", expr, "postfix", e.postfix, "result", e.value);
    }

    static Object bfs(Req r) {
        String start = r.q("start", DataStore.RTOS.get(0));
        if (!store.graph.has(start))
            throw new ApiException(400, "Unknown RTO centre.");
        RtoGraph.Bfs b = store.graph.bfs(start);
        return map("start", start, "order", b.order, "distance", b.distance, "adjacency", store.graph.adjacency());
    }

    static Object dfs(Req r) {
        String start = r.q("start", DataStore.RTOS.get(0));
        if (!store.graph.has(start))
            throw new ApiException(400, "Unknown RTO centre.");
        List<String> order = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        dfsVisit(start, seen, order);
        return map("start", start, "order", order, "adjacency", store.graph.adjacency(),
                "note", "Explores one connected verification-centre route as deeply as possible before backtracking.",
                "complexity", "O(V + E)");
    }

    static void dfsVisit(String node, Set<String> seen, List<String> order) {
        if (!seen.add(node))
            return;
        order.add(node);
        for (String n : store.graph.neighbours(node))
            dfsVisit(n, seen, order);
    }

    static Object documentCheck(Req r) {
        boolean photo = Boolean.parseBoolean(r.q("photo", "false"));
        boolean id = Boolean.parseBoolean(r.q("id", "false"));
        boolean address = Boolean.parseBoolean(r.q("address", "false"));
        boolean application = Boolean.parseBoolean(r.q("application", "false"));
        int score = (photo ? 25 : 0) + (id ? 30 : 0) + (address ? 20 : 0) + (application ? 25 : 0);
        return map("score", score, "status", score >= 90 ? "READY" : score >= 60 ? "ALMOST READY" : "ACTION NEEDED",
                "missing", List
                        .of(!photo ? "Passport photo" : "", !id ? "Identity proof" : "",
                                !address ? "Address proof" : "", !application ? "Application receipt" : "")
                        .stream().filter(x -> !x.isEmpty()).toList());
    }

    static Object slotScore(Req r) {
        String rto = r.q("rto", DataStore.RTOS.get(0)), date = r.q("date", LocalDate.now().plusDays(1).toString());
        DataStore.checkRto(rto);
        DataStore.parseDate(date);
        List<SlotLoadHeap.Slot> ranked = SlotLoadHeap.rank(store, rto, date);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            var x = ranked.get(i);
            int score = Math.max(0, 100 - x.booked * 33);
            out.add(map("time", x.time, "booked", x.booked, "free", x.free, "score", score, "label",
                    score >= 67 ? "GOOD" : score >= 34 ? "MODERATE" : "BUSY"));
        }
        return map("rto", rto, "date", date, "slots", out);
    }

    static Object waitTime(Req r) {
        String rto = r.q("rto", DataStore.RTOS.get(0)), date = r.q("date", LocalDate.now().plusDays(1).toString());
        int waiting = 0, confirmed = 0;
        for (Applicant a : store.list())
            if (a.rto.equals(rto) && a.date.equals(date)) {
                if ("WAITING".equals(a.status))
                    waiting++;
                if ("CONFIRMED".equals(a.status))
                    confirmed++;
            }
        int minutes = waiting * 15;
        return map("rto", rto, "date", date, "waiting", waiting, "confirmed", confirmed, "minutes", minutes, "level",
                minutes >= 60 ? "HIGH" : minutes >= 30 ? "MODERATE" : "LOW", "message",
                minutes == 0 ? "No waiting queue currently." : "Estimated wait based on current queue length.");
    }

    static Object heap(Req r) {
        String rto = r.q("rto", DataStore.RTOS.get(0));
        String date = r.q("date", LocalDate.now().plusDays(1).toString());
        if (!DataStore.RTOS.contains(rto))
            throw new ApiException(400, "Unknown RTO centre.");
        DataStore.parseDate(date);
        List<SlotLoadHeap.Slot> ranked = SlotLoadHeap.rank(store, rto, date);
        List<Map<String, Object>> slots = new ArrayList<>();
        for (SlotLoadHeap.Slot x : ranked)
            slots.add(x.toMap());
        return map("rto", rto, "date", date, "slots", slots,
                "note", "Min-heap repeatedly removes the least-loaded appointment slot first.",
                "complexity", "O(s log s), where s = number of time slots");
    }

    static Object algorithms(Req r) {
        String name = r.path.substring(r.path.lastIndexOf('/') + 1);
        String rto = r.q("rto", null), date = r.q("date", null);
        if (rto == null || date == null) { // default to the first waiting applicant's slot
            List<Applicant> w = store.waitingApplicants();
            if (rto == null)
                rto = w.isEmpty() ? DataStore.RTOS.get(0) : w.get(0).rto;
            if (date == null)
                date = w.isEmpty() ? LocalDate.now().plusDays(1).toString() : w.get(0).date;
        }
        if (name.equals("allocate")) {
            r.requireMethod("POST");
            r.requireAdmin();
            Algorithms.Scenario sc = Algorithms.scenario(store, rto, date);
            if (sc.sample)
                throw new ApiException(409, "There are no waiting applicants for this centre and date.");
            Map<String, Object> bt = Algorithms.backtrack(sc);
            if (!Boolean.TRUE.equals(bt.get("solved")))
                throw new ApiException(409, "Not enough free seats to place every waiting applicant.");
            List<Applicant> moved = store.applyAllocation(rto, date, Algorithms.allocation(bt));
            return map("success", true, "message",
                    moved.size() + " waiting applicant(s) were moved to free slots and confirmed.", "moved", moved,
                    "backtracks", bt.get("backtracks"));
        }
        r.requireMethod("GET");
        Algorithms.Scenario sc = Algorithms.scenario(store, rto, date);
        switch (name) {
            case "greedy":
                return Algorithms.greedy(sc);
            case "dp":
                return Algorithms.dp(sc);
            case "backtrack":
                return Algorithms.backtrack(sc);
            default:
                throw new ApiException(404, "Unknown algorithm.");
        }
    }
}
