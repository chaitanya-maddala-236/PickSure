from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db import get_db
from app.dependencies import current_employee
from app.models import Employee, Inventory, InventoryLot, Product, ShelfLocation
from app.schemas import FreshnessAdvice, FreshnessLotView, FreshnessSummary, InventoryView
from app.services.freshness import freshness_status, minimum_shelf_life_days
from app.services.freshness_advisor import advise_on_freshness

router = APIRouter(prefix="/inventory", tags=["Inventory"])


@router.get("/freshness", response_model=FreshnessSummary)
def freshness_summary(db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    rows = db.query(InventoryLot, Product, ShelfLocation).join(
        Product, Product.id == InventoryLot.sku_id
    ).join(ShelfLocation, ShelfLocation.sku_id == Product.id).filter(
        InventoryLot.store_id == employee.store_id,
        InventoryLot.quantity > 0,
    ).order_by(InventoryLot.expires_on.asc()).all()
    lots = [FreshnessLotView(
        sku=product.sku, product_name=product.name, lot_code=lot.lot_code, lot_tag=lot.lot_tag, expires_on=lot.expires_on,
        quantity=lot.quantity, status=freshness_status(lot.expires_on), aisle=location.aisle,
        rack=location.rack, shelf=location.shelf,
    ) for lot, product, location in rows]
    return FreshnessSummary(
        minimum_shelf_life_days=minimum_shelf_life_days(),
        safe_units=sum(lot.quantity for lot in lots if lot.status == "safe"),
        near_expiry_units=sum(lot.quantity for lot in lots if lot.status == "near_expiry"),
        expired_units=sum(lot.quantity for lot in lots if lot.status == "expired"),
        lots=lots,
    )


@router.get("/freshness/advice", response_model=FreshnessAdvice)
def freshness_advice(db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    rows = db.query(InventoryLot, Product).join(Product, Product.id == InventoryLot.sku_id).filter(
        InventoryLot.store_id == employee.store_id,
        InventoryLot.quantity > 0,
    ).order_by(InventoryLot.expires_on.asc()).all()
    lots = [{
        "sku": product.sku,
        "product_name": product.name,
        "lot_code": lot.lot_code,
        "expires_on": lot.expires_on.isoformat(),
        "quantity": lot.quantity,
        "status": freshness_status(lot.expires_on),
    } for lot, product in rows]
    return advise_on_freshness(lots)


@router.get("/{sku}", response_model=InventoryView)
def get_inventory(sku: str, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    row = db.query(Inventory, Product).join(Product, Product.id == Inventory.sku_id).filter(
        Inventory.store_id == employee.store_id, Product.sku == sku
    ).first()
    if row is None:
        raise HTTPException(status_code=404, detail="Inventory SKU not found")
    inventory, product = row
    return InventoryView(sku=product.sku, product_name=product.name, on_hand=inventory.on_hand)
