ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN lockout_until TIMESTAMP NULL;

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(64) NOT NULL,
    email VARCHAR(255),
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    details VARCHAR(255),
    success BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);
