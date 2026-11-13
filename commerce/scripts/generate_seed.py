"""Generate deterministic, synthetic commerce fixtures. Run from the repository root."""
from pathlib import Path
from datetime import datetime, timedelta
from decimal import Decimal
import random

rng = random.Random(20261113)
lines = ["USE commerce_data;"]
def insert(table, values):
    def literal(value):
        if value is None:
            return 'NULL'
        if isinstance(value, (int, Decimal)):
            return str(value)
        return "'" + str(value).replace("'", "''") + "'"
    lines.append(f"INSERT INTO {table} VALUES (" + ','.join(map(literal, values)) + ');')
for shop in [1, 2]:
    insert('shops', [shop, ['杭州旗舰店', '上海精选店'][shop-1]])
    for category, name in enumerate(['数码', '家居', '服饰', '新品'], 1):
        for n in range(1, 4):
            product = shop*100 + category*10 + n
            insert('products', [product, shop, f'{name}商品{n}', name, Decimal(category*35+n*10)])
order_id = item_id = refund_id = 0
for month in range(5, 11):
    for shop in [1, 2]:
        for n in range(48):
            order_id += 1
            paid = n % 11 != 0
            created = datetime(2026, month, 1) + timedelta(days=n%27, hours=n%23)
            insert('orders', [order_id, shop, 'PAID' if paid else 'CANCELLED', created, created if paid else None])
            for offset in range(1+(n%3 == 0)):
                item_id += 1
                category = (n+offset)%3+1
                product = shop*100+category*10+rng.randint(1,3)
                amount = Decimal(rng.randint(20,300))+Decimal('0.50')
                insert('order_items', [item_id, shop, order_id, product, 1, amount])
                if paid and (n+offset)%7 == 0:
                    refund_id += 1
                    # One external refund may have repeated ingestion records.
                    values = [refund_id, f'RF-{item_id}-1', shop, item_id, 'SUCCESS', amount/2, created+timedelta(days=3)]
                    insert('refunds', values)
                    if n%14 == 0:
                        refund_id += 1
                        values[0] = refund_id
                        insert('refunds', values)
                    if n%21 == 0:
                        refund_id += 1
                        insert('refunds', [refund_id, f'RF-{item_id}-2', shop, item_id, 'SUCCESS', amount/4, created+timedelta(days=5)])
                if paid and n%13 == 0:
                    refund_id += 1
                    insert('refunds', [refund_id, f'FAILED-{item_id}', shop, item_id, 'FAILED', amount, created+timedelta(days=1)])
Path('commerce/database/02-seed.sql').write_text('\n'.join(lines)+'\n')
print(f'Generated {order_id} orders, {item_id} items, {refund_id} refund ingestion records')
