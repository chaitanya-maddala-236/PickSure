from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import get_db
from app.dependencies import current_employee, supervisor_or_admin
from app.models import AuditLog, Employee
from app.schemas import AuditView

router = APIRouter(prefix="/audit-logs", tags=["Audit"])


@router.get("", response_model=list[AuditView])
def audit_logs(db: Session = Depends(get_db), employee: Employee = Depends(supervisor_or_admin)):
    return db.query(AuditLog).join(Employee, Employee.id == AuditLog.employee_id).filter(
        Employee.store_id == employee.store_id
    ).order_by(AuditLog.timestamp.desc()).limit(250).all()
