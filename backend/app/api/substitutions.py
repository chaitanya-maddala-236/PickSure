import json
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db import get_db
from app.dependencies import supervisor_or_admin
from app.models import AuditLog, Employee, PickTask, PickTaskItem, Product, Substitution
from app.schemas import ApprovalDecision, SubstitutionView

router = APIRouter(prefix="/substitutions", tags=["Substitutions"])


@router.get("/pending", response_model=list[SubstitutionView])
def pending_substitutions(db: Session = Depends(get_db), employee: Employee = Depends(supervisor_or_admin)):
    pending = db.query(Substitution).filter(Substitution.approval_status == "pending").order_by(Substitution.created_at.asc()).all()
    visible = []
    for row in pending:
        item = db.get(PickTaskItem, row.task_item_id)
        task = db.get(PickTask, item.task_id) if item else None
        if task and task.order.store_id == employee.store_id:
            visible.append(SubstitutionView(id=row.id, task_item_id=row.task_item_id, original_sku=row.original_sku,
                                            replacement_sku=row.replacement_sku, reason=row.reason, approval_status=row.approval_status))
    return visible


@router.post("/{substitution_id}/approve", response_model=SubstitutionView)
def decide_substitution(substitution_id: str, body: ApprovalDecision, db: Session = Depends(get_db), employee: Employee = Depends(supervisor_or_admin)):
    row = db.query(Substitution).filter(Substitution.id == substitution_id).with_for_update(of=Substitution).first()
    if row is None:
        raise HTTPException(status_code=404, detail="Substitution not found")
    item = db.get(PickTaskItem, row.task_item_id)
    task = db.get(PickTask, item.task_id) if item else None
    if item is None or task is None or task.order.store_id != employee.store_id:
        raise HTTPException(status_code=404, detail="Substitution not found")
    if row.approval_status != "pending":
        raise HTTPException(status_code=409, detail="Substitution has already been reviewed")
    row.approval_status = body.decision
    row.approved_by = employee.id
    if body.decision == "approved":
        replacement = db.query(Product).filter(Product.sku == row.replacement_sku, Product.active.is_(True)).first()
        if replacement is None:
            raise HTTPException(status_code=409, detail="Replacement SKU is no longer available")
        item.sku_id = replacement.id
        item.status = "substitution_approved"
    else:
        item.status = "open"
    db.add(AuditLog(id=str(uuid4()), employee_id=employee.id, action=f"substitution_{body.decision}", entity_id=row.id,
                    details=json.dumps({"task_item_id": item.id, "replacement_sku": row.replacement_sku, "reason": row.reason})))
    db.commit()
    return SubstitutionView(id=row.id, task_item_id=item.id, original_sku=row.original_sku,
                            replacement_sku=row.replacement_sku, reason=row.reason, approval_status=row.approval_status)
