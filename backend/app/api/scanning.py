import json
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload

from app.db import get_db
from app.dependencies import current_employee
from app.models import AuditLog, Employee, InventoryLot, PickTask, PickTaskItem, Product
from app.schemas import LotVerifyRequest, LotVerifyResponse, ScanVerifyRequest, ScanVerifyResponse
from app.services.freshness import lot_is_dispatchable, recommended_lot

router = APIRouter(prefix="/scan", tags=["Barcode verification"])


@router.post("/verify", response_model=ScanVerifyResponse)
def verify_scan(body: ScanVerifyRequest, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    item = db.query(PickTaskItem).options(joinedload(PickTaskItem.product), joinedload(PickTaskItem.task).joinedload(PickTask.order)).filter(
        PickTaskItem.id == body.task_item_id
    ).first()
    if item is None or item.task.order.store_id != employee.store_id:
        raise HTTPException(status_code=404, detail="Picking item not found")
    if employee.role not in {"supervisor", "admin"} and item.task.employee_id != employee.id:
        raise HTTPException(status_code=403, detail="Task is assigned to another employee")
    match = db.query(Product).filter(Product.barcode == body.barcode, Product.active.is_(True)).first()
    valid = match is not None and match.id == item.sku_id
    db.add(AuditLog(id=str(uuid4()), employee_id=employee.id,
                    action="scan_verified" if valid else "scan_rejected", entity_id=item.id,
                    details=json.dumps({"expected_sku": item.product.sku, "scanned_sku": match.sku if match else None,
                                        "barcode": body.barcode})))
    db.commit()
    return ScanVerifyResponse(valid=valid, expected_sku=item.product.sku,
                              scanned_sku=match.sku if match else None,
                              product_name=match.name if match else None,
                              message="Correct product verified" if valid else "Barcode does not match the expected item")


@router.post("/verify-lot", response_model=LotVerifyResponse)
def verify_lot(body: LotVerifyRequest, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    item = db.query(PickTaskItem).options(joinedload(PickTaskItem.task).joinedload(PickTask.order)).filter(
        PickTaskItem.id == body.task_item_id
    ).first()
    if item is None or item.task.order.store_id != employee.store_id:
        raise HTTPException(status_code=404, detail="Picking item not found")
    if employee.role not in {"supervisor", "admin"} and item.task.employee_id != employee.id:
        raise HTTPException(status_code=403, detail="Task is assigned to another employee")
    lot = db.query(InventoryLot).filter(
        InventoryLot.id == body.lot_id,
        InventoryLot.store_id == employee.store_id,
        InventoryLot.sku_id == item.sku_id,
    ).first()
    expected = recommended_lot(db, employee.store_id, item.sku_id)
    valid = lot is not None and lot.lot_tag == body.lot_tag and lot_is_dispatchable(lot) and expected is not None and lot.id == expected.id
    message = "Freshest eligible lot verified" if valid else "Lot label is not the recommended safe lot for this product"
    db.add(AuditLog(id=str(uuid4()), employee_id=employee.id,
                    action="lot_verified" if valid else "lot_scan_rejected", entity_id=item.id,
                    details=json.dumps({"lot_id": body.lot_id, "lot_tag": body.lot_tag,
                                        "lot_code": lot.lot_code if lot else None,
                                        "valid": valid})))
    db.commit()
    return LotVerifyResponse(valid=valid, lot_id=body.lot_id,
                             lot_code=lot.lot_code if lot else None,
                             expires_on=lot.expires_on if lot else None,
                             available_quantity=lot.quantity if lot else 0,
                             message=message)
