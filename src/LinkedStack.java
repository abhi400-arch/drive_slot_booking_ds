import java.util.ArrayList;
import java.util.List;

/** LIFO stack implemented with linked nodes. Used for the "recent actions" history. */
class LinkedStack<T> {
    private static class Node<T> { T val; Node<T> next; Node(T v, Node<T> n) { val = v; next = n; } }
    private Node<T> top;
    private int size;

    void push(T v) { top = new Node<>(v, top); size++; }
    T pop() { if (top == null) return null; T v = top.val; top = top.next; size--; return v; }
    T peek() { return top == null ? null : top.val; }
    boolean isEmpty() { return top == null; }
    int size() { return size; }

    /** Items from the top (newest) to the bottom (oldest). */
    List<T> toList() {
        List<T> out = new ArrayList<>();
        for (Node<T> n = top; n != null; n = n.next) out.add(n.val);
        return out;
    }
}
