import java.util.*;

/** Merge sort and insertion sort over applicant records, counting comparisons. */
class Sorting {
    static class Result {
        List<Applicant> sorted; long comparisons; long moves; double millis; String algorithm;
    }

    /** Comparator for a sortable field: id, name, date, status, rto. */
    static Comparator<Applicant> by(String field) {
        switch (field == null ? "id" : field) {
            case "name":   return Comparator.comparing((Applicant a) -> a.name.toLowerCase());
            case "date":   return Comparator.comparing((Applicant a) -> a.date).thenComparing(a -> a.time);
            case "status": return Comparator.comparing((Applicant a) -> a.status);
            case "rto":    return Comparator.comparing((Applicant a) -> a.rto);
            case "phone":  return Comparator.comparing((Applicant a) -> a.phone);
            case "id":     return Comparator.comparingInt((Applicant a) -> a.id);
            default: throw new IllegalArgumentException("Cannot sort by '" + field + "'. Use id, name, date, status or rto.");
        }
    }

    private static class Counter { long cmp, mv; }

    static Result mergeSort(List<Applicant> in, Comparator<Applicant> c) {
        Result r = new Result(); r.algorithm = "merge";
        Applicant[] a = in.toArray(new Applicant[0]);
        Counter k = new Counter();
        long t0 = System.nanoTime();
        if (a.length > 1) mergeSort(a, new Applicant[a.length], 0, a.length - 1, c, k);
        r.millis = (System.nanoTime() - t0) / 1e6;
        r.sorted = new ArrayList<>(Arrays.asList(a)); r.comparisons = k.cmp; r.moves = k.mv;
        return r;
    }

    private static void mergeSort(Applicant[] a, Applicant[] tmp, int lo, int hi, Comparator<Applicant> c, Counter k) {
        if (lo >= hi) return;
        int mid = (lo + hi) >>> 1;
        mergeSort(a, tmp, lo, mid, c, k);
        mergeSort(a, tmp, mid + 1, hi, c, k);
        int i = lo, j = mid + 1, t = lo;
        while (i <= mid && j <= hi) {
            k.cmp++;
            tmp[t++] = c.compare(a[i], a[j]) <= 0 ? a[i++] : a[j++]; // <= keeps the sort stable
            k.mv++;
        }
        while (i <= mid) { tmp[t++] = a[i++]; k.mv++; }
        while (j <= hi)  { tmp[t++] = a[j++]; k.mv++; }
        for (int x = lo; x <= hi; x++) a[x] = tmp[x];
    }

    static Result insertionSort(List<Applicant> in, Comparator<Applicant> c) {
        Result r = new Result(); r.algorithm = "insertion";
        Applicant[] a = in.toArray(new Applicant[0]);
        Counter k = new Counter();
        long t0 = System.nanoTime();
        for (int i = 1; i < a.length; i++) {
            Applicant key = a[i]; int j = i - 1;
            while (j >= 0) {
                k.cmp++;
                if (c.compare(a[j], key) > 0) { a[j + 1] = a[j]; k.mv++; j--; } else break;
            }
            a[j + 1] = key;
        }
        r.millis = (System.nanoTime() - t0) / 1e6;
        r.sorted = new ArrayList<>(Arrays.asList(a)); r.comparisons = k.cmp; r.moves = k.mv;
        return r;
    }
}
