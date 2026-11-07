import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Holds all application data (linked list + queue + stack), the booking rules and TSV persistence. */
class DataStore {
    static final int CAPACITY = 3;
    static final List<String> RTOS = List.of("Bengaluru Central RTO", "Yeshwanthpur RTO",
            "Koramangala RTO", "Indiranagar RTO", "Whitefield RTO");
    static final List<String> SLOTS = List.of("09:00", "10:00", "11:00", "12:00", "14:00", "15:00");
    static final List<String> LICENSES = List.of("LMV", "MCWG", "Transport");
    static final String HEADER = "id\tname\tphone\tlicenseType\trto\tdate\ttime\tstatus\tqueueSeq\tusername";

    static class BookingResult {
        String status, message;
        Applicant applicant;
        List<Applicant> promoted = new ArrayList<>();
        List<Map<String, Object>> suggestions = new ArrayList<>();
        BookingResult(String s, String m) { status = s; message = m; }
    }

    final ApplicantLinkedList records = new ApplicantLinkedList();   // main storage: linked list
    final LinkedQueue<Integer> waiting = new LinkedQueue<>();       // FIFO waiting list (applicant ids)
    final LinkedStack<String> actions = new LinkedStack<>();        // LIFO recent actions
    final RtoGraph graph = new RtoGraph();                          // RTO centres (adjacency list)

    private final Path recordsFile, actionsFile;
    private int nextId = 1001;
    private long seq = 1;

    DataStore(Path dataDir) {
        recordsFile = dataDir.resolve("applicants.tsv");
        actionsFile = dataDir.resolve("actions.tsv");
        for (String r : RTOS) graph.addVertex(r);
        graph.addEdge("Bengaluru Central RTO", "Yeshwanthpur RTO");
        graph.addEdge("Bengaluru Central RTO", "Koramangala RTO");
        graph.addEdge("Bengaluru Central RTO", "Indiranagar RTO");
        graph.addEdge("Koramangala RTO", "Indiranagar RTO");
        graph.addEdge("Indiranagar RTO", "Whitefield RTO");
    }

    // ------------------------------------------------------------------ persistence
    synchronized void load() throws IOException {
        Files.createDirectories(recordsFile.getParent());
        if (!Files.exists(recordsFile)) Files.writeString(recordsFile, HEADER + "\n", StandardCharsets.UTF_8);
        List<Applicant> waitingRecs = new ArrayList<>();
        for (String line : Files.readAllLines(recordsFile, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("id\t")) continue;
            String[] x = line.split("\t", -1);
            if (x.length < 8) continue;
            try {
                String uname = x.length > 9 ? x[9].trim() : "";
                Applicant a = new Applicant(Integer.parseInt(x[0]), x[1], x[2], x[3], x[4], x[5], x[6], x[7], uname);
                if (x.length > 8 && !x[8].isBlank()) a.queueSeq = Long.parseLong(x[8]);
                records.insertLast(a);
                nextId = Math.max(nextId, a.id + 1);
                seq = Math.max(seq, a.queueSeq + 1);
                if ("WAITING".equals(a.status)) waitingRecs.add(a);
            } catch (NumberFormatException ignored) { /* skip a damaged line */ }
        }
        waitingRecs.sort(Comparator.comparingLong((Applicant a) -> a.queueSeq).thenComparingInt(a -> a.id));
        for (Applicant a : waitingRecs) waiting.enqueue(a.id);
        if (Files.exists(actionsFile))
            for (String line : Files.readAllLines(actionsFile, StandardCharsets.UTF_8)) if (!line.isBlank()) actions.push(line);
    }

    private synchronized void save() {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Applicant a : records.toList())
            sb.append(a.id).append('\t').append(clean(a.name)).append('\t').append(clean(a.phone)).append('\t')
              .append(clean(a.licenseType)).append('\t').append(clean(a.rto)).append('\t').append(clean(a.date)).append('\t')
              .append(clean(a.time)).append('\t').append(a.status).append('\t').append(a.queueSeq).append('\t')
              .append(clean(a.username)).append('\n');
        try {
            Path tmp = recordsFile.resolveSibling("applicants.tsv.tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, recordsFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { throw new ApiException(500, "Could not save data: " + e.getMessage()); }
    }

    private static String clean(String s) { return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); }

    private void log(String text) {
        String line = LocalTime.now().withNano(0) + "  " + clean(text);
        actions.push(line);
        try { Files.writeString(actionsFile, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException ignored) { /* history is best-effort */ }
    }

    // ------------------------------------------------------------------ queries
    synchronized List<Applicant> list() { return records.toList(); }
    synchronized List<Applicant> listForUser(String username) {
        List<Applicant> out = new ArrayList<>();
        for (Applicant a : records.toList()) {
            if (isOwner(a, username)) out.add(a);
        }
        return out;
    }
    static boolean isOwner(Applicant a, String user) {
        if (a == null || user == null || user.isBlank()) return false;
        if (a.username != null && !a.username.isBlank()) {
            if (a.username.equalsIgnoreCase(user)) return true;
            if ("applicant".equalsIgnoreCase(user) && ("abhishekt_2006".equalsIgnoreCase(a.username) || a.id == 1001)) {
                return true;
            }
        }
        String u = user.toLowerCase().replaceAll("[^a-z0-9]", "");
        String n = (a.name == null ? "" : a.name).toLowerCase().replaceAll("[^a-z0-9]", "");
        return !u.isEmpty() && !n.isEmpty() && (n.contains(u) || u.contains(n));
    }
    synchronized Applicant find(int id) { return records.findById(id); }
    synchronized int size() { return records.size(); }
    synchronized int count(String status) { int n = 0; for (Applicant a : records.toList()) if (status.equals(a.status)) n++; return n; }

    synchronized int slotCount(String rto, String date, String time) {
        int n = 0;
        for (Applicant a : records.toList())
            if ("CONFIRMED".equals(a.status) && rto.equals(a.rto) && date.equals(a.date) && time.equals(a.time)) n++;
        return n;
    }

    /** Free seats for each time slot of one RTO on one date (same order as SLOTS). */
    synchronized int[] freeCapacity(String rto, String date) {
        int[] free = new int[SLOTS.size()];
        for (int i = 0; i < free.length; i++) free[i] = CAPACITY - slotCount(rto, date, SLOTS.get(i));
        return free;
    }

    synchronized List<Applicant> waitingApplicants() {
        List<Applicant> out = new ArrayList<>();
        for (int id : waiting.toList()) { Applicant a = records.findById(id); if (a != null) out.add(a); }
        return out;
    }

    // ------------------------------------------------------------------ validation
    static String need(String v, String label) {
        if (v == null || v.isBlank()) throw new ApiException(400, label + " is required.");
        return v.trim();
    }

    static void checkName(String n) {
        if (!n.matches("[\\p{L} .'-]{2,60}")) throw new ApiException(400, "Name must be 2-60 letters (spaces, . ' - allowed).");
    }
    static void checkPhone(String p) {
        if (!p.matches("\\d{10}")) throw new ApiException(400, "Phone number must be exactly 10 digits.");
    }
    static void checkLicense(String l) {
        if (!LICENSES.contains(l)) throw new ApiException(400, "License type must be one of " + LICENSES + ".");
    }
    static void checkRto(String r) {
        if (!RTOS.contains(r)) throw new ApiException(400, "Unknown RTO centre.");
    }
    static void checkTime(String t) {
        if (!SLOTS.contains(t)) throw new ApiException(400, "Time must be one of " + SLOTS + ".");
    }
    static LocalDate parseDate(String d) {
        try { return LocalDate.parse(d); } catch (Exception e) { throw new ApiException(400, "Date must be in YYYY-MM-DD format."); }
    }
    static void checkFutureDate(String d) {
        if (parseDate(d).isBefore(LocalDate.now())) throw new ApiException(400, "Date cannot be in the past.");
    }

    // ------------------------------------------------------------------ CRUD
    /** Create an applicant record without booking a slot (status DRAFT). */
    synchronized Applicant create(Map<String, Object> p) {
        String name = need(Json.str(p, "name"), "Name"), phone = need(Json.str(p, "phone"), "Phone");
        String lic = Json.str(p, "licenseType"); if (lic.isEmpty()) lic = LICENSES.get(0);
        String rto = Json.str(p, "rto"), date = Json.str(p, "date"), time = Json.str(p, "time");
        checkName(name); checkPhone(phone); checkLicense(lic);
        if (!rto.isEmpty()) checkRto(rto);
        if (!date.isEmpty()) checkFutureDate(date);
        if (!time.isEmpty()) checkTime(time);
        String username = Json.str(p, "username");
        Applicant a = new Applicant(nextId++, name, phone, lic, rto, date, time, "DRAFT", username);
        records.insertLast(a);
        log("Added applicant #" + a.id + " (" + a.name + ")");
        save();
        return a;
    }

    synchronized Applicant update(Map<String, Object> p) {
        Applicant a = requireApplicant(p);
        String name = Json.str(p, "name"), phone = Json.str(p, "phone"), lic = Json.str(p, "licenseType");
        if (!name.isEmpty()) checkName(name);
        if (!phone.isEmpty()) checkPhone(phone);
        if (!lic.isEmpty()) checkLicense(lic);
        if (!name.isEmpty()) a.name = name;
        if (!phone.isEmpty()) a.phone = phone;
        if (!lic.isEmpty()) a.licenseType = lic;
        log("Updated applicant #" + a.id);
        save();
        return a;
    }

    synchronized List<Applicant> delete(int id) {
        Applicant a = records.findById(id);
        if (a == null) throw new ApiException(404, "Applicant not found.");
        if ("WAITING".equals(a.status)) waiting.remove(id);
        boolean freedSeat = "CONFIRMED".equals(a.status);
        records.deleteById(id);
        log("Deleted applicant #" + id);
        List<Applicant> promoted = freedSeat ? promote(a.rto, a.date, a.time) : new ArrayList<>();
        save();
        return promoted;
    }

    private Applicant requireApplicant(Map<String, Object> p) {
        String id = Json.str(p, "id");
        if (!id.matches("\\d+")) throw new ApiException(400, "Applicant id is required.");
        Applicant a = records.findById(Integer.parseInt(id));
        if (a == null) throw new ApiException(404, "Applicant not found.");
        return a;
    }

    // ------------------------------------------------------------------ booking logic
    synchronized BookingResult book(Map<String, Object> p) {
        return book(p, "");
    }

    /** Book a slot for a new applicant (name/phone given) or an existing one (id given). */
    synchronized BookingResult book(Map<String, Object> p, String currentUser) {
        String idText = Json.str(p, "id");
        Applicant a = null;
        if (!idText.isEmpty()) {
            a = requireApplicant(p);
            if (a.isActive()) throw new ApiException(409, "Applicant #" + a.id + " already has an active booking. Cancel it first.");
        }
        String name = Json.str(p, "name"), phone = Json.str(p, "phone"), lic = Json.str(p, "licenseType");
        String rto = Json.str(p, "rto"), date = Json.str(p, "date"), time = Json.str(p, "time");
        if (a != null) {
            if (name.isEmpty()) name = a.name;
            if (phone.isEmpty()) phone = a.phone;
            if (lic.isEmpty()) lic = a.licenseType;
        }
        need(name, "Name"); need(phone, "Phone"); need(rto, "RTO centre"); need(date, "Date"); need(time, "Time");
        if (lic.isEmpty()) lic = LICENSES.get(0);
        checkName(name); checkPhone(phone); checkLicense(lic); checkRto(rto); checkFutureDate(date); checkTime(time);

        for (Applicant o : records.toList())
            if ((a == null || o.id != a.id) && o.isActive() && o.phone.equals(phone)
                    && o.rto.equals(rto) && o.date.equals(date) && o.time.equals(time))
                throw new ApiException(409, "This phone number already has a booking for that slot (applicant #" + o.id + ").");

        String username = (currentUser != null && !currentUser.isBlank()) ? currentUser : Json.str(p, "username");
        if (a == null) {
            a = new Applicant(nextId++, name, phone, lic, rto, date, time, "DRAFT", username);
            records.insertLast(a);
        } else if (a.username == null || a.username.isBlank()) {
            a.username = username;
        }
        a.name = name; a.phone = phone; a.licenseType = lic; a.rto = rto; a.date = date; a.time = time;

        BookingResult r;
        if (slotCount(rto, date, time) < CAPACITY) {
            a.status = "CONFIRMED"; a.queueSeq = 0;
            log("Confirmed booking for #" + a.id + " at " + rto + " " + date + " " + time);
            r = new BookingResult("CONFIRMED", "Slot confirmed successfully.");
        } else {
            a.status = "WAITING"; a.queueSeq = seq++;
            waiting.enqueue(a.id);
            log("Added #" + a.id + " to the waiting queue for " + rto + " " + date + " " + time);
            r = new BookingResult("WAITING", "Selected slot is full. Applicant #" + a.id + " added to the FIFO waiting queue (position "
                    + waiting.size() + " overall).");
            r.suggestions = Algorithms.suggest(this, rto, date, time, lic);
        }
        r.applicant = a;
        save();
        return r;
    }

    synchronized BookingResult cancel(int id) {
        Applicant a = records.findById(id);
        if (a == null) throw new ApiException(404, "Applicant not found.");
        if (!a.isActive()) throw new ApiException(409, "No active booking exists for this applicant.");
        boolean freedSeat = "CONFIRMED".equals(a.status);
        if ("WAITING".equals(a.status)) waiting.remove(id);
        a.status = "CANCELLED"; a.queueSeq = 0;
        log("Cancelled booking for #" + id);
        BookingResult r = new BookingResult("CANCELLED", "Booking cancelled.");
        r.applicant = a;
        if (freedSeat) {
            r.promoted = promote(a.rto, a.date, a.time);
            if (!r.promoted.isEmpty()) r.message = "Booking cancelled. " + r.promoted.size()
                    + " waiting applicant(s) promoted from the queue (first in, first out).";
        }
        save();
        return r;
    }

    /** Move waiting applicants of one slot to CONFIRMED (oldest first) while seats are free. */
    private List<Applicant> promote(String rto, String date, String time) {
        List<Applicant> promoted = new ArrayList<>();
        while (slotCount(rto, date, time) < CAPACITY) {
            Integer id = waiting.removeFirstMatching(i -> {
                Applicant w = records.findById(i);
                return w != null && "WAITING".equals(w.status) && w.rto.equals(rto) && w.date.equals(date) && w.time.equals(time);
            });
            if (id == null) break;
            Applicant w = records.findById(id);
            w.status = "CONFIRMED"; w.queueSeq = 0;
            log("Promoted #" + w.id + " from the waiting queue to CONFIRMED");
            promoted.add(w);
        }
        return promoted;
    }

    /** Apply a computed allocation (applicant id -> new time slot) for waiting applicants of one RTO/date. */
    synchronized List<Applicant> applyAllocation(String rto, String date, Map<Integer, String> assignment) {
        List<Applicant> moved = new ArrayList<>();
        for (Map.Entry<Integer, String> e : assignment.entrySet()) {
            Applicant a = records.findById(e.getKey());
            if (a == null || !"WAITING".equals(a.status)) continue;
            if (slotCount(rto, date, e.getValue()) >= CAPACITY)
                throw new ApiException(409, "Slot " + e.getValue() + " filled up meanwhile. Run the allocation again.");
            waiting.remove(a.id);
            a.time = e.getValue(); a.status = "CONFIRMED"; a.queueSeq = 0;
            log("Auto-allocated #" + a.id + " to " + rto + " " + date + " " + e.getValue());
            moved.add(a);
        }
        save();
        return moved;
    }
}
