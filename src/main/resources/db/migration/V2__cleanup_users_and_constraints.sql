ALTER TABLE users
    DROP COLUMN confirm_password;

ALTER TABLE users
    ADD CONSTRAINT uq_users_email UNIQUE (email);

ALTER TABLE users
    ADD CONSTRAINT uq_users_username UNIQUE (username);
