CREATE TABLE deals (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    title VARCHAR(255),
    link VARCHAR(500),
    client_name VARCHAR(255),
    value DECIMAL(12,2),
    description VARCHAR(2000),
    status VARCHAR(64),
    created_at TIMESTAMP,
    item_photo LONGBLOB,
    item_photo_content_type VARCHAR(100),
    CONSTRAINT fk_deals_user FOREIGN KEY (user_id) REFERENCES users(id)
);
