/** Error that is turned into an HTTP status + JSON message. */
class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    final int status;
    ApiException(int status, String message) { super(message); this.status = status; }
}
