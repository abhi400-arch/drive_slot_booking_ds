# DriveSlot — Advanced DSA Driving Licence Slot Booking System

Java DSA project with a modern web UI and **MySQL-backed applicant/admin login credentials**.

## 1. MySQL setup

Install/start MySQL 8.x and make sure the MySQL service is running.

Option A — automatic setup:
- The application creates `driving_license_db` and the `users` table automatically when it starts.
- Default MySQL connection: `localhost:3306`, user `root`, password `root`.

Option B — SQL setup:
- Open `sql/driving_license_db.sql` in MySQL Workbench and execute it.

If your MySQL password is not `root`, set these environment variables before starting:

```bat
set DRIVESLOT_DB_HOST=localhost
set DRIVESLOT_DB_PORT=3306
set DRIVESLOT_DB_USER=root
set DRIVESLOT_DB_PASSWORD=YOUR_MYSQL_PASSWORD
```

## 2. MySQL JDBC driver

The project uses MySQL Connector/J. If using IntelliJ, add **MySQL Connector/J** to the project libraries.

If using Maven, import the included `pom.xml`; Maven will download the JDBC driver automatically.

## 3. Run with IntelliJ IDEA

1. Open this `final_dsactual` folder.
2. Add the MySQL Connector/J dependency (or reload Maven from `pom.xml`).
3. Mark `src` as Sources Root.
4. Run `Main.java`.
5. Open `http://localhost:8080`.

## 4. Run from Command Prompt with Maven

```bat
mvn clean compile
mvn exec:java
```

Or, if you already have the Connector/J JAR in `lib`:

```bat
javac -cp "lib\\mysql-connector-j.jar" -d out src\\*.java
java -cp "out;lib\\mysql-connector-j.jar" Main
```

## MySQL credential table

`users` stores:
- `id`
- `username`
- `password_hash` (SHA-256, never plain text)
- `role` (`ADMIN` or `APPLICANT`)
- `created_at`

New applicant registrations are inserted directly into this MySQL table. Login reads the credentials directly from MySQL.

Demo accounts:
- Admin: `admin / admin123`
- Applicant: `applicant / applicant123`

## DSA modules
- Applicant Register — linked list
- Recent Activity — stack
- Appointment Processing — queue
- Applicant ID Directory — BST
- Nearby Centre Search — BFS
- Verification Route Explorer — DFS
- Smart Slot Finder — min-heap / priority queue
- Search — linear, binary and Trie
- Sorting — merge and insertion sort
- Smart Scheduling — greedy, dynamic programming and backtracking

## Advanced Smart Service Lab
1. Document Readiness Check
2. Fair Slot Score
3. Live Waiting-Time Estimate
4. Demand Pulse
5. Automatic Queue Recovery
6. Digital Slot Pass & Active Bookings

## Security & Data Privacy (Applicant Booking Isolation)
- **Role-Based Access Control (RBAC):**
  - **Applicants:** Strictly isolated to their own bookings. When signed in, an applicant can only see, manage, and download gate passes for appointments associated with their account. The API enforces ownership checks on all endpoints (`/api/applicants`, `/api/confirmation-pdf`, `/api/cancel`).
  - **Administrators:** Have privileged access to the executive dashboard, city-wide RTO quota management, complete applicant directory, and algorithmic telemetry tools.
