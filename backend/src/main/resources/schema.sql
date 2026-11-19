CREATE TABLE IF NOT EXISTS cem_user (
    username VARCHAR(64) PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(80) NOT NULL
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cem_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kind VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT '',
    owner VARCHAR(80) NOT NULL,
    related_id BIGINT NULL,
    amount DECIMAL(16,2) NULL,
    due_date DATE NULL,
    details JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kind_status (kind, status, id),
    INDEX idx_kind_owner (kind, owner),
    CONSTRAINT fk_cem_related FOREIGN KEY (related_id) REFERENCES cem_record(id)
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cem_initialization (
    name VARCHAR(32) PRIMARY KEY
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cem_department (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(400) NOT NULL DEFAULT ''
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cem_employee (
    username VARCHAR(64) PRIMARY KEY,
    department_id BIGINT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    session_version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (username) REFERENCES cem_user(username),
    FOREIGN KEY (department_id) REFERENCES cem_department(id)
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cem_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(400) NOT NULL DEFAULT ''
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cem_role_permission (
    role_id BIGINT NOT NULL,
    permission VARCHAR(80) NOT NULL,
    PRIMARY KEY (role_id, permission),
    FOREIGN KEY (role_id) REFERENCES cem_role(id) ON DELETE CASCADE
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cem_employee_role (
    username VARCHAR(64) NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (username, role_id),
    FOREIGN KEY (username) REFERENCES cem_employee(username),
    FOREIGN KEY (role_id) REFERENCES cem_role(id)
) DEFAULT CHARSET=utf8mb4;
