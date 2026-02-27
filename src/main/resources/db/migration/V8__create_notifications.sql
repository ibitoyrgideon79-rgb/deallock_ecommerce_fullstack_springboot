CREATE TABLE notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  message VARCHAR(1000) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  `read` BIT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
