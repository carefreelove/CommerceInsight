SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS commerce_scope_all;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_all.shops AS SELECT id,name FROM commerce_data.shops;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_all.products AS SELECT id,shop_id,name,category,price FROM commerce_data.products;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_all.orders AS SELECT id,shop_id,status,created_at,paid_at FROM commerce_data.orders;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_all.order_items AS SELECT id,shop_id,order_id,product_id,quantity,paid_amount FROM commerce_data.order_items;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_all.refunds AS
SELECT MIN(id) AS id, refund_no, shop_id, order_item_id, status, MAX(amount) AS amount, MAX(succeeded_at) AS succeeded_at
FROM commerce_data.refunds WHERE status='SUCCESS'
GROUP BY refund_no,shop_id,order_item_id,status;
CREATE USER 'ci_read_all'@'%' IDENTIFIED BY 'ci_read_local_demo';
GRANT SELECT ON commerce_scope_all.* TO 'ci_read_all'@'%';
CREATE DATABASE IF NOT EXISTS commerce_scope_1;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_1.shops AS SELECT id,name FROM commerce_data.shops WHERE id=1;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_1.products AS SELECT id,shop_id,name,category,price FROM commerce_data.products WHERE shop_id=1;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_1.orders AS SELECT id,shop_id,status,created_at,paid_at FROM commerce_data.orders WHERE shop_id=1;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_1.order_items AS SELECT id,shop_id,order_id,product_id,quantity,paid_amount FROM commerce_data.order_items WHERE shop_id=1;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_1.refunds AS
SELECT MIN(id) AS id, refund_no, shop_id, order_item_id, status, MAX(amount) AS amount, MAX(succeeded_at) AS succeeded_at
FROM commerce_data.refunds WHERE status='SUCCESS' AND shop_id=1
GROUP BY refund_no,shop_id,order_item_id,status;
CREATE USER 'ci_read_1'@'%' IDENTIFIED BY 'ci_read_local_demo';
GRANT SELECT ON commerce_scope_1.* TO 'ci_read_1'@'%';
CREATE DATABASE IF NOT EXISTS commerce_scope_2;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_2.shops AS SELECT id,name FROM commerce_data.shops WHERE id=2;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_2.products AS SELECT id,shop_id,name,category,price FROM commerce_data.products WHERE shop_id=2;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_2.orders AS SELECT id,shop_id,status,created_at,paid_at FROM commerce_data.orders WHERE shop_id=2;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_2.order_items AS SELECT id,shop_id,order_id,product_id,quantity,paid_amount FROM commerce_data.order_items WHERE shop_id=2;
CREATE SQL SECURITY DEFINER VIEW commerce_scope_2.refunds AS
SELECT MIN(id) AS id, refund_no, shop_id, order_item_id, status, MAX(amount) AS amount, MAX(succeeded_at) AS succeeded_at
FROM commerce_data.refunds WHERE status='SUCCESS' AND shop_id=2
GROUP BY refund_no,shop_id,order_item_id,status;
CREATE USER 'ci_read_2'@'%' IDENTIFIED BY 'ci_read_local_demo';
GRANT SELECT ON commerce_scope_2.* TO 'ci_read_2'@'%';
CREATE USER 'ci_app'@'%' IDENTIFIED BY 'ci_app_local_demo';
GRANT SELECT,INSERT,UPDATE,DELETE ON commerce_app.* TO 'ci_app'@'%';
