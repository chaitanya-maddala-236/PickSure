from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db import get_db
from app.dependencies import current_employee
from app.models import Employee, PickTask
from app.schemas import PackingRequest, SyncBatchIn, SyncBatchOut, SyncEventIn
from app.api.sync import _process_one

router = APIRouter(tags=["Packing"])


@router.post("/tasks/{task_id}/pack", response_model=SyncBatchOut)
def pack_task(task_id: str, body: PackingRequest, db: Session = Depends(get_db), employee: Employee = Depends(current_employee)):
    if not body.checklist_complete:
        raise HTTPException(status_code=422, detail="Complete the packing checklist first")
    task = db.get(PickTask, task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="Task not found")
    event = SyncEventIn(event_uuid=body.event_uuid or uuid4(), event_type="PACK", task_id=task_id)
    return SyncBatchOut(results=[_process_one(db, event, employee)])
