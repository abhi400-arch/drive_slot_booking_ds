import java.util.*;

/** Binary Search Tree keyed on applicant id. Duplicate ids are ignored. */
class BST {
    static class Node {
        int key; Applicant data; Node left, right;
        Node(Applicant a) { key = a.id; data = a; }
    }

    Node root;
    private int size;

    void insert(Applicant a) { root = insert(root, a); }
    private Node insert(Node n, Applicant a) {
        if (n == null) { size++; return new Node(a); }
        if (a.id < n.key) n.left = insert(n.left, a);
        else if (a.id > n.key) n.right = insert(n.right, a);
        return n;
    }

    /** Result of a BST lookup: the applicant (or null) and the keys visited on the way down. */
    static class SearchResult { Applicant found; List<Integer> path = new ArrayList<>(); }

    SearchResult search(int key) {
        SearchResult r = new SearchResult();
        Node n = root;
        while (n != null) {
            r.path.add(n.key);
            if (key == n.key) { r.found = n.data; break; }
            n = key < n.key ? n.left : n.right;
        }
        return r;
    }

    List<Integer> preorder()  { List<Integer> o = new ArrayList<>(); pre(root, o);  return o; }
    List<Integer> inorder()   { List<Integer> o = new ArrayList<>(); in(root, o);   return o; }
    List<Integer> postorder() { List<Integer> o = new ArrayList<>(); post(root, o); return o; }
    private void pre(Node n, List<Integer> o)  { if (n == null) return; o.add(n.key); pre(n.left, o); pre(n.right, o); }
    private void in(Node n, List<Integer> o)   { if (n == null) return; in(n.left, o); o.add(n.key); in(n.right, o); }
    private void post(Node n, List<Integer> o) { if (n == null) return; post(n.left, o); post(n.right, o); o.add(n.key); }

    int height() { return height(root); }
    private int height(Node n) { return n == null ? 0 : 1 + Math.max(height(n.left), height(n.right)); }
    int size() { return size; }

    Map<String, Object> toMap() { return toMap(root); }
    private Map<String, Object> toMap(Node n) {
        if (n == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", n.key); m.put("left", toMap(n.left)); m.put("right", toMap(n.right));
        return m;
    }
}
