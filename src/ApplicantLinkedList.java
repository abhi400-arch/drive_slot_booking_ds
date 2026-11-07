import java.util.ArrayList;
import java.util.List;

/** Singly linked list that stores every applicant record (the main storage structure). */
class ApplicantLinkedList {
    private static class Node {
        Applicant data; Node next;
        Node(Applicant d) { data = d; }
    }

    private Node head, tail;
    private int size;

    /** Insert at the end - O(1) thanks to the tail pointer. */
    void insertLast(Applicant a) {
        Node n = new Node(a);
        if (head == null) head = tail = n;
        else { tail.next = n; tail = n; }
        size++;
    }

    /** Insert at the beginning - O(1). */
    void insertFirst(Applicant a) {
        Node n = new Node(a);
        n.next = head; head = n;
        if (tail == null) tail = n;
        size++;
    }

    /** Delete the node holding the given applicant id - O(n). */
    boolean deleteById(int id) {
        Node prev = null, cur = head;
        while (cur != null) {
            if (cur.data.id == id) {
                if (prev == null) head = cur.next; else prev.next = cur.next;
                if (cur == tail) tail = prev;
                size--;
                return true;
            }
            prev = cur; cur = cur.next;
        }
        return false;
    }

    /** Find by id by walking the list - O(n). */
    Applicant findById(int id) {
        for (Node n = head; n != null; n = n.next) if (n.data.id == id) return n.data;
        return null;
    }

    /** Traverse head -> tail and collect the records. */
    List<Applicant> toList() {
        List<Applicant> out = new ArrayList<>();
        for (Node n = head; n != null; n = n.next) out.add(n.data);
        return out;
    }

    int size() { return size; }
}
