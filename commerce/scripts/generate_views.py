from pathlib import Path
parts=['SET NAMES utf8mb4;']
for scope in ['all','1','2']:
    schema=f'commerce_scope_{scope}'
    parts.append(f'CREATE DATABASE IF NOT EXISTS {schema};')
    for table, columns in {'shops':'id,name','products':'id,shop_id,name,category,price','orders':'id,shop_id,status,created_at,paid_at','order_items':'id,shop_id,order_id,product_id,quantity,paid_amount'}.items():
        condition='' if scope=='all' else f' WHERE {"id" if table=="shops" else "shop_id"}={scope}'
        parts.append(f'CREATE SQL SECURITY DEFINER VIEW {schema}.{table} AS SELECT {columns} FROM commerce_data.{table}{condition};')
    condition='' if scope=='all' else f' AND shop_id={scope}'
    # Deduplicate external refund numbers inside the protected view.
    parts.append(f'''CREATE SQL SECURITY DEFINER VIEW {schema}.refunds AS
SELECT MIN(id) AS id, refund_no, shop_id, order_item_id, status, MAX(amount) AS amount, MAX(succeeded_at) AS succeeded_at
FROM commerce_data.refunds WHERE status='SUCCESS'{condition}
GROUP BY refund_no,shop_id,order_item_id,status;''')
    parts.append(f"CREATE USER 'ci_read_{scope}'@'%' IDENTIFIED BY 'ci_read_local_demo';")
    parts.append(f"GRANT SELECT ON {schema}.* TO 'ci_read_{scope}'@'%';")
parts += ["CREATE USER 'ci_app'@'%' IDENTIFIED BY 'ci_app_local_demo';", "GRANT SELECT,INSERT,UPDATE,DELETE ON commerce_app.* TO 'ci_app'@'%';"]
Path('commerce/database/03-views.sql').write_text('\n'.join(parts)+'\n')
