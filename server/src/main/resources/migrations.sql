-- Create User Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create Permission Table
CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

-- Create User-Permission Junction Table
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id BIGINT,
    permission_id BIGINT,
    PRIMARY KEY (user_id, permission_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- Create User Preferences Table
CREATE TABLE IF NOT EXISTS user_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    preferences TEXT,
    user_id BIGINT UNIQUE,
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Insert Permissions
INSERT IGNORE INTO permissions (name) VALUES 
('PORTFOLIO_READ'), ('PORTFOLIO_CREATE'), ('PORTFOLIO_UPDATE'), ('PORTFOLIO_DELETE'),
('WATCHLIST_READ'), ('WATCHLIST_CREATE'), ('WATCHLIST_UPDATE'), ('WATCHLIST_DELETE'),
('BOARD_READ'), ('BOARD_CREATE'), ('BOARD_UPDATE'), ('BOARD_DELETE'),
('PORTFOLIO_EXECUTE_ADD'), ('WATCHLIST_EXECUTE_ADD'),
('ADMIN');

-- Note: Admin user creation is handled by DataInitializer on startup if not exists.
