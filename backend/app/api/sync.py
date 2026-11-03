import json
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload

from app.db import get_db
from app.dependencies import current_employee
from app.models import AuditLog, Employee, Inventory, InventoryLot, PackingRecord, PickEvent, PickTask, PickTaskItem, Product, Substitution, SyncEvent, utcnow
from app.schemas import ExceptionRequest, SubstitutionRequest, SyncBatchIn, SyncBatchOut, SyncEventIn, SyncEventResult
from app.services.freshness import lot_is_dispatchable, recommended_lot

router = APIRouter(tags=["Offline synchronization"])


def _task_item(db: Session, event, employee: Employee):
    item = db.query(PickTaskItem).options(joinedload(PickTaskItem.product), joinedload(PickTaskItem.task).joinedload(PickTask.order)).filter(
        PickTaskItem.id == event.task_item_id
    ).with_for_update(of=PickTaskItem).first()
    if item is None or item.task_id != event.task_id:
        raise ValueError("Picking item does not belong to this task")
    if item.task.order.store_id != employee.store_id:
        raise ValueError("Task belongs to another store")
    if employee.role not in {"supervisor", "admin"} and item.task.employee_id != employee.id:
        raise ValueError("Task is assigned to another employee")
    return item


def _reject(db: Session, event, employee: Employee, message: str) -> SyncEventResult:
    result = {"event_uuid": str(event.event_uuid), "status": "rejected", "message": message, "server_event_id": None}
    row = SyncEvent(id=str(uuid4()), event_uuid=str(event.event_uuid), employee_id=employee.id,
                    sync_status="rejected", result_json=json.dumps(result))
    db.add(row)
    db.add(AuditLog(id=str(uuid4()), employee_id=employee.id, action="sync_event_rejected",
                    entity_id=event.task_item_id or event.task_id,
                    details=json.dumps({"event_uuid": str(event.event_uuid), "event_type": event.event_type, "reason": message})))
    db.commit()
    return SyncEventResult(**result)


def _process_one(db: Session, event, employee: Employee) -> SyncEventResult:
    event_uuid = str(event.event_uuid)
    prior = db.query(SyncEvent).filter(SyncEvent.event_uuid == event_uuid).first()
    if prior:
        old = json.loads(prior.result_json)
        status = "rejected" if prior.sync_status == "rejected" else "duplicate"
        return SyncEventResult(event_uuid=event.event_uuid, status=status, message=old.get("message", "Event was already processed"), server_event_id=old.get("server_event_id"))

    try:
        task = db.query(PickTask).options(joinedload(PickTask.order)).filter(PickTask.id == event.task_id).with_for_update(of=PickTask).first()
        if task is None or task.order.store_id != employee.store_id:
            raise ValueError("Task is unavailable for this employee")
        if employee.role not in {"supervisor", "admin"} and task.employee_id != employee.id:
            raise ValueError("Task is assigned to another employee")

        event_row_id = str(uuid4())
        if event.event_type == "PACK":
            items = db.query(PickTaskItem).filter(PickTaskItem.task_id == task.id).all()
            if any(item.status not in {"picked", "substituted", "unavailable"} for item in items):
                raise ValueError("Every line must be picked or resolved before packing")
            existing = db.query(PackingRecord).filter(PackingRecord.order_id == task.order_id, PackingRecord.status == "packed").first()
            if not existing:
                db.add(PackingRecord(id=event_row_id, order_id=task.order_id, packer_id=employee.id, status="packed"))
            task.status = "completed"
            task.order.status = "packed"
            action = "order_packed"
            entity_id = task.order_id
        else:
            if not event.task_item_id:
                raise ValueError("A task item is required for this event")
            item = _task_item(db, event, employee)
            if event.event_type == "PICK":
                if task.status == "completed" or task.order.status == "packed":
                    raise ValueError("This order is already packed")
                if item.status in {"unavailable", "substituted", "substitution_pending"}:
                    raise ValueError("This line is already resolved or awaiting substitution approval")
                if event.quantity < 1:
                    raise ValueError("Pick quantity must be at least one")
                if event.barcode != item.product.barcode:
                    raise ValueError("Barcode does not match the expected SKU")
                if not event.lot_id or not event.lot_tag:
                    raise ValueError("Scan the recommended lot label before submitting this pick")
                if item.picked_qty + event.quantity > item.requested_qty:
                    raise ValueError("Pick quantity exceeds the requested amount")
                lot = db.query(InventoryLot).filter(
                    InventoryLot.id == event.lot_id,
                    InventoryLot.store_id == task.order.store_id,
                    InventoryLot.sku_id == item.sku_id,
                ).with_for_update().first()
                expected_lot = recommended_lot(db, task.order.store_id, item.sku_id)
                if lot is None or lot.lot_tag != event.lot_tag:
                    raise ValueError("Lot label does not match this product")
                if not lot_is_dispatchable(lot):
                    raise ValueError("Lot is expired, too near expiry, or out of stock")
                if expected_lot is None or expected_lot.id != lot.id:
                    raise ValueError("The recommended lot changed. Refresh the task and scan the new lot label")
                if lot.quantity < event.quantity:
                    raise ValueError("Insufficient stock in the verified lot")
                inventory = db.query(Inventory).filter(Inventory.store_id == task.order.store_id,
                    Inventory.sku_id == item.sku_id).with_for_update().first()
                if inventory is None or inventory.on_hand < event.quantity:
                    raise ValueError("Insufficient available inventory for this SKU")
                inventory.on_hand -= event.quantity
                lot.quantity -= event.quantity
                item.picked_qty += event.quantity
                item.status = "picked" if item.picked_qty == item.requested_qty else "open"
                task.status = "in_progress"
                db.add(PickEvent(id=event_row_id, task_item_id=item.id, lot_id=lot.id, employee_id=employee.id, event_uuid=event_uuid,
                                 event_type="PICK", quantity=event.quantity, barcode=event.barcode,
                                 created_at=event.created_at or utcnow()))
                action = "item_picked"
            elif event.event_type == "EXCEPTION":
                if item.picked_qty > 0 or item.status in {"unavailable", "substituted", "substitution_pending", "substitution_approved"}:
                    raise ValueError("This line is already resolved or has been partially picked")
                if not event.reason or not event.reason.strip():
                    raise ValueError("An exception reason is required")
                item.status = "unavailable"
                db.add(PickEvent(id=event_row_id, task_item_id=item.id, employee_id=employee.id, event_uuid=event_uuid,
                                 event_type="EXCEPTION", quantity=0, reason=event.reason, created_at=event.created_at or utcnow()))
                action = "item_unavailable"
            elif event.event_type == "SUBSTITUTION":
                if item.picked_qty > 0 or item.status in {"unavailable", "substituted", "substitution_pending", "substitution_approved"}:
                    raise ValueError("This line is already resolved or has been partially picked")
                if not event.replacement_sku:
                    raise ValueError("A replacement SKU is required")
                replacement = db.query(Product).filter(Product.sku == event.replacement_sku, Product.active.is_(True)).first()
                if replacement is None:
                    raise ValueError("Replacement SKU was not found")
                approval = event.approval_status or "pending"
                if approval == "approved" and employee.role not in {"supervisor", "admin"}:
                    raise ValueError("Only a supervisor can approve a substitution")
                db.add(Substitution(id=event_row_id, task_item_id=item.id, original_sku=item.product.sku,
                                    replacement_sku=replacement.sku, reason=event.reason, approval_status=approval,
                                    approved_by=employee.id if approval == "approved" else None))
                if approval == "approved":
                    item.sku_id = replacement.id
                    item.status = "substitution_approved"
                elif approval == "pending":
                    item.status = "substitution_pending"
                db.add(PickEvent(id=str(uuid4()), task_item_id=item.id, employee_id=employee.id, event_uuid=event_uuid,
                                 event_type="SUBSTITUTION", quantity=0, reason=f"replacement:{replacement.sku}", created_at=event.created_at or utcnow()))
                action = "substitution_recorded"
            else:
                raise ValueError("Unsupported event type")
            entity_id = item.id

        db.add(AuditLog(id=str(uuid4()), employee_id=employee.id, action=action, entity_id=entity_id,
                        details=json.dumps({"event_uuid": event_uuid, "event_type": event.event_type,
                                            "lot_id": event.lot_id if event.event_type == "PICK" else None,
                                            "lot_tag": event.lot_tag if event.event_type == "PICK" else None})))
        accepted = {"event_uuid": event_uuid, "status": "accepted", "message": "Event applied", "server_event_id": event_row_id}
        db.add(SyncEvent(id=str(uuid4()), event_uuid=event_uuid, employee_id=employee.id,
                         sync_status="accepted", result_json=json.dumps(accepted)))
        db.commit()
        return SyncEventResult(**accepted)
    except ValueError as exc:
        db.rollback()
        return _reject(db, event, employee, str(exc))
    except Exception:
        db.rollback()
        raise


@router.post("/sync/events", response_model=SyncBatchOut)
def sync_events(body: SyncBatchIn, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    return SyncBatchOut(results=[_process_one(db, event, employee) for event in body.events])


@router.get("/sync/events/{event_uuid}")
def get_sync_event(event_uuid: str, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    row = db.query(SyncEvent).filter(SyncEvent.event_uuid == event_uuid).first()
    owner = db.get(Employee, row.employee_id) if row else None
    if row is None or owner is None or (row.employee_id != employee.id and
                                        (employee.role not in {"supervisor", "admin"} or owner.store_id != employee.store_id)):
        raise HTTPException(status_code=404, detail="Sync event not found")
    return json.loads(row.result_json)


@router.post("/pick-events", response_model=SyncBatchOut)
def record_pick(body: SyncBatchIn, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    if any(event.event_type != "PICK" for event in body.events):
        raise HTTPException(status_code=422, detail="This endpoint accepts PICK events only")
    return SyncBatchOut(results=[_process_one(db, event, employee) for event in body.events])


@router.post("/items/{item_id}/exception", response_model=SyncBatchOut)
def report_exception(item_id: str, request: ExceptionRequest, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    item = db.get(PickTaskItem, item_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Picking item not found")
    event = SyncEventIn(event_uuid=request.event_uuid, event_type="EXCEPTION", task_id=item.task_id,
                        task_item_id=item_id, reason=request.reason)
    return SyncBatchOut(results=[_process_one(db, event, employee)])


@router.post("/substitutions", response_model=SyncBatchOut)
def request_substitution(request: SubstitutionRequest, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    item = db.get(PickTaskItem, request.task_item_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Picking item not found")
    event = SyncEventIn(event_uuid=request.event_uuid or uuid4(), event_type="SUBSTITUTION", task_id=item.task_id,
                        task_item_id=item.id, replacement_sku=request.replacement_sku,
                        reason=request.reason, approval_status=request.approval_status)
    return SyncBatchOut(results=[_process_one(db, event, employee)])
