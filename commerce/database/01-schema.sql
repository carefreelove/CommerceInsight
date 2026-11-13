CREATE DATABASE IF NOT EXISTS commerce_app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS commerce_data CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE commerce_app;
CREATE TABLE ci_users (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(60) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL, shop_id INT NULL
);
CREATE TABLE ci_tasks (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, scope_id INT NOT NULL,
 question VARCHAR(2000) NOT NULL, reference_date DATE NOT NULL, status VARCHAR(20) NOT NULL,
 idempotency_key VARCHAR(100) NOT NULL, retry_of VARCHAR(36), error_message VARCHAR(1000),
 plan_json LONGTEXT, report_json LONGTEXT, created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY task_idempotency(user_id,idempotency_key), KEY task_history(user_id,created_at)
);
CREATE TABLE ci_events (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, task_id VARCHAR(36) NOT NULL,
 type VARCHAR(40) NOT NULL, payload LONGTEXT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), KEY task_events(task_id,id)
);
CREATE TABLE ci_evidence (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, task_id VARCHAR(36) NOT NULL,
 sql_text TEXT NOT NULL, result_json LONGTEXT NOT NULL,
 elapsed_ms BIGINT NOT NULL, created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), KEY(task_id)
);
CREATE TABLE ci_budget (
 id INT PRIMARY KEY, committed_cny DECIMAL(12,6) NOT NULL DEFAULT 0, limit_cny DECIMAL(12,6) NOT NULL
);
INSERT INTO ci_budget VALUES(1,0,18);
CREATE TABLE ci_model_calls (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, task_id VARCHAR(36) NOT NULL,
 purpose VARCHAR(40) NOT NULL, model VARCHAR(100) NOT NULL,
 reserved_cny DECIMAL(12,6) NOT NULL, charged_cny DECIMAL(12,6),
 input_tokens INT, output_tokens INT, status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), KEY(task_id)
);
USE commerce_data;
CREATE TABLE shops(id INT PRIMARY KEY, name VARCHAR(80) NOT NULL);
CREATE TABLE products(id BIGINT PRIMARY KEY,shop_id INT NOT NULL,name VARCHAR(100) NOT NULL,category VARCHAR(40) NOT NULL,price DECIMAL(12,2) NOT NULL);
CREATE TABLE orders(id BIGINT PRIMARY KEY,shop_id INT NOT NULL,status VARCHAR(20) NOT NULL,created_at DATETIME NOT NULL,paid_at DATETIME,KEY(shop_id,paid_at));
CREATE TABLE order_items(id BIGINT PRIMARY KEY,shop_id INT NOT NULL,order_id BIGINT NOT NULL,product_id BIGINT NOT NULL,quantity INT NOT NULL,paid_amount DECIMAL(12,2) NOT NULL,KEY(order_id),KEY(product_id));
CREATE TABLE refunds(id BIGINT PRIMARY KEY,refund_no VARCHAR(50) NOT NULL,shop_id INT NOT NULL,order_item_id BIGINT NOT NULL,status VARCHAR(20) NOT NULL,amount DECIMAL(12,2) NOT NULL,succeeded_at DATETIME,KEY(order_item_id),KEY(refund_no));
