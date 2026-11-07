import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** FIFO queue implemented with linked nodes. Used for applicants waiting for a full slot. */
class LinkedQueue<T> {
    private static class Node<T> { T val; Node<T> next; Node(T v) { val = v; } }
    private Node<T> head, tail;
    private int size;

    void enqueue(T v) {
        Node<T> n = new Node<>(v);
        if (tail == null) head = tail = n; else { tail.next = n; tail = n; }
        size++;
    }

    T dequeue() {
        if (head == null) return null;
        T v = head.val; head = head.next;
        if (head == null) tail = null;
        size--;
        return v;
    }

    T peek() { return head == null ? null : head.val; }

    /** Remove and return the FIRST (oldest) element that matches - keeps FIFO order per slot. */
    T removeFirstMatching(Predicate<T> p) {
        Node<T> prev = null;
        for (Node<T> cur = head; cur != null; prev = cur, cur = cur.next) {
            if (p.test(cur.val)) {
                if (prev == null) head = cur.next; else prev.next = cur.next;
                if (cur == tail) tail = prev;
                size--;
                return cur.val;
            }
        }
        return null;
    }

    boolean remove(T v) { return removeFirstMatching(x -> x.equals(v)) != null; }
    boolean isEmpty() { return head == null; }
    int size() { return size; }

    /** Items from the front (oldest) to the back (newest). */
    List<T> toList() {
        List<T> out = new ArrayList<>();
        for (Node<T> n = head; n != null; n = n.next) out.add(n.val);
        return out;
    }
}
