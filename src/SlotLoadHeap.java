import java.util.*;

/** Min-heap used to rank appointment slots by current booking load. */
class SlotLoadHeap {
    static class Slot {
        String rto, date, time;
        int booked, free;
        Slot(String rto, String date, String time, int booked, int capacity) {
            this.rto = rto; this.date = date; this.time = time;
            this.booked = booked; this.free = Math.max(0, capacity - booked);
        }
        Map<String,Object> toMap() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("rto", rto); m.put("date", date); m.put("time", time);
            m.put("booked", booked); m.put("free", free);
            return m;
        }
    }

    private final PriorityQueue<Slot> heap = new PriorityQueue<>(
        Comparator.comparingInt((Slot s) -> s.booked).thenComparing(s -> s.time));

    void add(Slot s) { heap.offer(s); }
    Slot poll() { return heap.poll(); }
    boolean isEmpty() { return heap.isEmpty(); }

    static List<Slot> rank(DataStore store, String rto, String date) {
        SlotLoadHeap h = new SlotLoadHeap();
        for (String time : DataStore.SLOTS)
            h.add(new Slot(rto, date, time, store.slotCount(rto, date, time), DataStore.CAPACITY));
        List<Slot> out = new ArrayList<>();
        while (!h.isEmpty()) out.add(h.poll());
        return out;
    }
}
