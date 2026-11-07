import java.util.*;

/** Linear search and binary search over applicant records, counting comparisons. */
class Searching {
    static class Result {
        List<Applicant> matches = new ArrayList<>();
        int comparisons;
        String note = "";
    }

    static String fieldValue(Applicant a, String field) {
        switch (field) {
            case "id": return String.valueOf(a.id);
            case "name": return a.name;
            case "phone": return a.phone;
            default: throw new IllegalArgumentException("Search field must be id, name or phone.");
        }
    }

    /** Linear search: check every record one by one. Partial (contains) match, case-insensitive. O(n). */
    static Result linear(List<Applicant> list, String field, String q) {
        Result r = new Result();
        String needle = q.toLowerCase();
        for (Applicant a : list) {
            r.comparisons++;
            if (fieldValue(a, field).toLowerCase().contains(needle)) r.matches.add(a);
        }
        r.note = "Scanned records one by one from the head of the list.";
        return r;
    }

    /** Binary search on a list ALREADY sorted by the same comparator. Exact match. O(log n). */
    static Result binary(List<Applicant> sorted, Comparator<Applicant> c, Applicant probe) {
        Result r = new Result();
        int lo = 0, hi = sorted.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            r.comparisons++;
            int cmp = c.compare(sorted.get(mid), probe);
            if (cmp == 0) {
                // expand to neighbours with the same key (e.g. two applicants with the same name)
                int l = mid, h = mid;
                while (l > 0 && c.compare(sorted.get(l - 1), probe) == 0) l--;
                while (h < sorted.size() - 1 && c.compare(sorted.get(h + 1), probe) == 0) h++;
                for (int i = l; i <= h; i++) r.matches.add(sorted.get(i));
                break;
            }
            if (cmp < 0) lo = mid + 1; else hi = mid - 1;
        }
        r.note = "Records were first merge-sorted, then the middle element was compared repeatedly.";
        return r;
    }
}
