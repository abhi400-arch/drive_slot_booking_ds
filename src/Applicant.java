import java.util.LinkedHashMap;
import java.util.Map;

/** One applicant / booking record. */
class Applicant {
    int id;
    String name, phone, licenseType, rto, date, time, status, username;
    long queueSeq; // position marker used to rebuild the FIFO waiting queue after a restart

    Applicant(int id, String name, String phone, String licenseType, String rto,
              String date, String time, String status, String username) {
        this.id = id; this.name = name; this.phone = phone; this.licenseType = licenseType;
        this.rto = rto; this.date = date; this.time = time; this.status = status;
        this.username = username == null ? "" : username;
    }

    Applicant(int id, String name, String phone, String licenseType, String rto,
              String date, String time, String status) {
        this(id, name, phone, licenseType, rto, date, time, status, "");
    }

    boolean isActive() { return "CONFIRMED".equals(status) || "WAITING".equals(status); }

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", name); m.put("phone", phone);
        m.put("licenseType", licenseType); m.put("rto", rto);
        m.put("date", date); m.put("time", time); m.put("status", status);
        m.put("username", username);
        return m;
    }
}
