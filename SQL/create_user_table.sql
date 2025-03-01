CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,           -- Unique ID for each user
    email_addr VARCHAR(254) UNIQUE NOT NULL,          -- Email address, must be unique
    handle VARCHAR(25) UNIQUE NOT NULL,               -- Display username
    user_type ENUM('BASIC', 'SUPERUSER', 'OWNER') NOT NULL,  -- User type
    status ENUM('ACTIVE', 'INACTIVE', 'PERM_BAN') NOT NULL,  -- User status
    mvp_tier int not null,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP    -- When the user was created
);