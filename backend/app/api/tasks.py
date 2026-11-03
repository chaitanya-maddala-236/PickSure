from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, joinedload

from app.db import get_db
from app.dependencies import current_employee
from app.models import Employee, InventoryLot, PickTask, PickTaskItem, ShelfLocation
from app.schemas import TaskItemView, TaskView
from app.services.freshness import freshness_status, recommended_lot

router = APIRouter(tags=["Tasks"])


def _permitted(task: PickTask, employee: Employee) -> bool:
    return task.order.store_id == employee.store_id and (employee.role in {"supervisor", "admin"} or task.employee_id == employee.id)


@router.get("/tasks", response_model=list[TaskView])
def list_tasks(db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    query = db.query(PickTask).options(joinedload(PickTask.order), joinedload(PickTask.items))
    if employee.role not in {"supervisor", "admin"}:
        query = query.filter(PickTask.employee_id == employee.id)
    tasks = query.join(PickTask.order).filter(PickTask.order.has(store_id=employee.store_id)).all()
    return [TaskView(
        id=task.id,
        order_id=task.order_id,
        status=task.status,
        priority=task.order.priority,
        item_count=len(task.items),
        picked_count=sum(item.picked_qty for item in task.items),
    ) for task in tasks]


@router.get("/tasks/{task_id}", response_model=TaskView)
def get_task(task_id: str, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    task = db.query(PickTask).options(joinedload(PickTask.order), joinedload(PickTask.items)).filter(PickTask.id == task_id).first()
    if task is None or not _permitted(task, employee):
        raise HTTPException(status_code=404, detail="Task not found")
    return TaskView(id=task.id, order_id=task.order_id, status=task.status, priority=task.order.priority,
                    item_count=len(task.items), picked_count=sum(item.picked_qty for item in task.items))


@router.get("/tasks/{task_id}/items", response_model=list[TaskItemView])
def list_items(task_id: str, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    task = db.query(PickTask).options(joinedload(PickTask.order)).filter(PickTask.id == task_id).first()
    if task is None or not _permitted(task, employee):
        raise HTTPException(status_code=404, detail="Task not found")
    rows = db.query(PickTaskItem, ShelfLocation).options(joinedload(PickTaskItem.product)).join(
        ShelfLocation, ShelfLocation.sku_id == PickTaskItem.sku_id
    ).filter(PickTaskItem.task_id == task_id).all()
    result = []
    for item, location in rows:
        lot = recommended_lot(db, task.order.store_id, item.sku_id)
        available_lots = db.query(InventoryLot).filter(
            InventoryLot.store_id == task.order.store_id,
            InventoryLot.sku_id == item.sku_id,
            InventoryLot.quantity > 0,
        ).all()
        has_near_expiry = any(freshness_status(candidate.expires_on) == "near_expiry" for candidate in available_lots)
        result.append(TaskItemView(
            id=item.id, task_id=item.task_id, sku=item.product.sku, product_name=item.product.name,
            barcode=item.product.barcode, requested_qty=item.requested_qty, picked_qty=item.picked_qty,
            status=item.status, aisle=location.aisle, rack=location.rack, shelf=location.shelf,
            recommended_lot_id=lot.id if lot else None, lot_code=lot.lot_code if lot else None,
            lot_tag=lot.lot_tag if lot else None, expires_on=lot.expires_on if lot else None,
            lot_status="ready" if lot else "near_expiry_only" if has_near_expiry else "no_stock",
        ))
    return result
