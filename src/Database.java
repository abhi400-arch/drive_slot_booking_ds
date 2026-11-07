import java.sql.*;

/**
 * MySQL-backed credential database for applicant/admin accounts.
 *
 * MySQL:
 * Host: localhost
 * Port: 3306
 * Database: driving_license_db
 * Username: root
 * Password: ABhishekt@2006
 */
class Database {

    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "driving_license_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Abhishekt@2006";

    private static final String SERVER_URL =
            "jdbc:mysql://" + DB_HOST + ":" + DB_PORT
                    + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static final String URL =
            "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";


    /**
     * Creates the database and users table if they don't already exist.
     */
    void initialize() throws Exception {

        // Load MySQL JDBC driver
        Class.forName("com.mysql.cj.jdbc.Driver");

        // Create database
        try (Connection connection =
                     DriverManager.getConnection(
                             SERVER_URL,
                             DB_USER,
                             DB_PASSWORD);
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS " + DB_NAME
            );
        }

        // Create users table
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            String createTable =
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "username VARCHAR(50) NOT NULL UNIQUE, " +
                            "password_hash CHAR(64) NOT NULL, " +
                            "role ENUM('ADMIN','APPLICANT') " +
                            "NOT NULL DEFAULT 'APPLICANT', " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ") ENGINE=InnoDB";

            statement.executeUpdate(createTable);

            // Create default accounts
            seedIfMissing(
                    connection,
                    "admin",
                    Main.hash("admin123"),
                    "ADMIN"
            );

            seedIfMissing(
                    connection,
                    "applicant",
                    Main.hash("applicant123"),
                    "APPLICANT"
            );
        }

        System.out.println(
                "MySQL database connected successfully!"
        );

        System.out.println(
                "Database: " + DB_NAME
        );
    }


    /**
     * Inserts a default user only if the username doesn't exist.
     */
    private void seedIfMissing(
            Connection connection,
            String username,
            String passwordHash,
            String role) throws SQLException {

        String sql =
                "INSERT IGNORE INTO users " +
                        "(username, password_hash, role) " +
                        "VALUES (?, ?, ?)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, role);

            statement.executeUpdate();
        }
    }


    /**
     * Creates a connection to the application database.
     */
    private Connection getConnection()
            throws SQLException {

        return DriverManager.getConnection(
                URL,
                DB_USER,
                DB_PASSWORD
        );
    }


    /**
     * Checks applicant/admin login credentials.
     *
     * Returns:
     * {passwordHash, role}
     *
     * Returns null if login fails.
     */
    String[] authenticate(
            String username,
            String passwordHash) {

        String sql =
                "SELECT password_hash, role " +
                        "FROM users " +
                        "WHERE username = ? " +
                        "LIMIT 1";

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    return null;
                }

                String storedHash =
                        result.getString("password_hash");

                String role =
                        result.getString("role");

                if (!passwordHash.equalsIgnoreCase(storedHash)) {
                    return null;
                }

                return new String[]{
                        storedHash,
                        role
                };
            }

        } catch (SQLException e) {

            e.printStackTrace();

            throw new ApiException(
                    500,
                    "MySQL login failed. " +
                            "Check that MySQL is running and " +
                            "the username/password are correct."
            );
        }
    }


    /**
     * Creates a new applicant account in MySQL.
     */
    boolean createApplicantUser(
            String username,
            String passwordHash) {

        String sql =
                "INSERT INTO users " +
                        "(username, password_hash, role) " +
                        "VALUES (?, ?, 'APPLICANT')";

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);
            statement.setString(2, passwordHash);

            statement.executeUpdate();

            return true;

        } catch (SQLIntegrityConstraintViolationException e) {

            // Username already exists
            return false;

        } catch (SQLException e) {

            e.printStackTrace();

            throw new ApiException(
                    500,
                    "Could not create applicant account in MySQL."
            );
        }
    }
}