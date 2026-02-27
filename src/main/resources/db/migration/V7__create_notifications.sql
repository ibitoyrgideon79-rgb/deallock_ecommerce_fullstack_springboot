CREATE TABLE notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL,
  message VARCHAR(500) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  is_read BIT(1) NOT NULL DEFAULT b'0',
  CONSTRAINT fk_notifications_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
);
