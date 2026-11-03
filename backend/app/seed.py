from datetime import date, timedelta

from app.models import Employee, FulfillmentOrder, Inventory, InventoryLot, PickTask, PickTaskItem, Product, ShelfLocation, Store
from app.security import hash_password

STORE_ID = "store-central-01"
PICKER_ID = "employee-picker-01"
SUPERVISOR_ID = "employee-supervisor-01"


def _ensure_lots(db):
    locations = {row.sku_id: row for row in db.query(ShelfLocation).all()}
    for product in db.query(Product).all():
        inventory = db.query(Inventory).filter(Inventory.store_id == STORE_ID, Inventory.sku_id == product.id).first()
        if inventory is None:
            inventory = Inventory(id=f"inventory-{product.id}", store_id=STORE_ID, sku_id=product.id, on_hand=12)
            db.add(inventory)
        if db.query(InventoryLot).filter(InventoryLot.store_id == STORE_ID, InventoryLot.sku_id == product.id).count() == 0:
            location = locations.get(product.id)
            if location is None:
                continue
            code = product.sku.split("-")[0]
            near_quantity = min(2, max(0, inventory.on_hand))
            db.add_all([
                InventoryLot(id=f"lot-{product.id}-near", store_id=STORE_ID, sku_id=product.id,
                    lot_code=f"{code}-{date.today().year}-NEAR", lot_tag=f"PICKSURE-LOT-{code}-NEAR-01",
                    expires_on=date.today() + timedelta(days=2), quantity=near_quantity,
                    aisle=location.aisle, rack=location.rack, shelf=location.shelf),
                InventoryLot(id=f"lot-{product.id}-fresh", store_id=STORE_ID, sku_id=product.id,
                    lot_code=f"{code}-{date.today().year}-FRESH", lot_tag=f"PICKSURE-LOT-{code}-FRESH-01",
                    expires_on=date.today() + timedelta(days=14), quantity=max(0, inventory.on_hand - near_quantity),
                    aisle=location.aisle, rack=location.rack, shelf=location.shelf),
            ])


def seed_demo(db):
    if db.get(Store, STORE_ID):
        _ensure_lots(db)
        db.commit()
        return
    db.add(Store(id=STORE_ID, name="Central Dark Store", location="Bengaluru"))
    db.flush()
    db.add_all([
        Employee(id=PICKER_ID, name="Aarav Patel", email="picker@picksure.demo", password_hash=hash_password("PickSure123!"), role="picker", store_id=STORE_ID),
        Employee(id=SUPERVISOR_ID, name="Mira Shah", email="supervisor@picksure.demo", password_hash=hash_password("PickSure123!"), role="supervisor", store_id=STORE_ID),
    ])
    products = [
        Product(id="sku-milk", sku="MILK-AMUL-500", name="Amul Milk 500 ml", barcode="8901262150012"),
        Product(id="sku-bread", sku="BREAD-WHEAT-400", name="Whole Wheat Bread 400 g", barcode="8901058850019"),
        Product(id="sku-eggs", sku="EGG-FARM-6", name="Farm Eggs 6 pack", barcode="8901725123406"),
        Product(id="sku-butter", sku="BUTTER-AMUL-100", name="Amul Butter 100 g", barcode="8901262150029"),
        Product(id="sku-yogurt", sku="YOGURT-PLAIN-400", name="Plain Yogurt 400 g", barcode="8901058850101"),
    ]
    db.add_all(products)
    db.flush()
    db.add_all([Inventory(id=f"inventory-{product.id}", store_id=STORE_ID, sku_id=product.id, on_hand=12) for product in products])
    db.add_all([
        ShelfLocation(id="loc-milk", sku_id="sku-milk", aisle="A1", rack="R1", shelf="S2"),
        ShelfLocation(id="loc-bread", sku_id="sku-bread", aisle="A1", rack="R2", shelf="S1"),
        ShelfLocation(id="loc-eggs", sku_id="sku-eggs", aisle="A2", rack="R1", shelf="S3"),
        ShelfLocation(id="loc-butter", sku_id="sku-butter", aisle="A2", rack="R2", shelf="S1"),
        ShelfLocation(id="loc-yogurt", sku_id="sku-yogurt", aisle="A1", rack="R1", shelf="S3"),
    ])
    db.flush()
    _ensure_lots(db)
    orders = [
        FulfillmentOrder(id="order-101", customer_id="customer-101", store_id=STORE_ID, priority="high"),
        FulfillmentOrder(id="order-102", customer_id="customer-102", store_id=STORE_ID, priority="normal"),
        FulfillmentOrder(id="order-103", customer_id="customer-103", store_id=STORE_ID, priority="normal"),
    ]
    db.add_all(orders)
    db.flush()
    tasks = [
        PickTask(id="task-101", order_id="order-101", employee_id=PICKER_ID),
        PickTask(id="task-102", order_id="order-102", employee_id=PICKER_ID),
        PickTask(id="task-103", order_id="order-103", employee_id=PICKER_ID),
    ]
    db.add_all(tasks)
    db.flush()
    db.add_all([
        PickTaskItem(id="item-101-milk", task_id="task-101", sku_id="sku-milk", requested_qty=1),
        PickTaskItem(id="item-101-bread", task_id="task-101", sku_id="sku-bread", requested_qty=1),
        PickTaskItem(id="item-102-milk", task_id="task-102", sku_id="sku-milk", requested_qty=1),
        PickTaskItem(id="item-102-eggs", task_id="task-102", sku_id="sku-eggs", requested_qty=1),
        PickTaskItem(id="item-103-bread", task_id="task-103", sku_id="sku-bread", requested_qty=1),
        PickTaskItem(id="item-103-butter", task_id="task-103", sku_id="sku-butter", requested_qty=1),
    ])
    db.commit()
