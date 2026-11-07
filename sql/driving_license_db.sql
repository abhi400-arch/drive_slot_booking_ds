-- DriveSlot / Driving License Slot Booking System
-- MySQL 8.x
CREATE DATABASE IF NOT EXISTS driving_license_db;
USE driving_license_db;

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash CHAR(64) NOT NULL,
    role ENUM('ADMIN','APPLICANT') NOT NULL DEFAULT 'APPLICANT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Demo accounts used by the project login screen.
-- SHA-256(admin123) and SHA-256(applicant123)
INSERT IGNORE INTO users(username,password_hash,role) VALUES
('admin','240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9','ADMIN'),
('applicant','494db03e59d528a739dd7d2503bdb07fcac78b2d5beb3e50ece2e5971a67291b','APPLICANT');
