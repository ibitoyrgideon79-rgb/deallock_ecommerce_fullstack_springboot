ALTER TABLE users
    ADD COLUMN profile_image LONGBLOB,
    ADD COLUMN profile_image_content_type VARCHAR(100);
