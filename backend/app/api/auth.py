from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db import get_db
from app.models import Employee
from app.schemas import EmployeeView, LoginRequest, LoginResponse
from app.security import create_access_token, verify_password

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/login", response_model=LoginResponse)
def login(body: LoginRequest, db: Session = Depends(get_db)):
    employee = db.query(Employee).filter(Employee.email == body.email.lower()).first()
    if employee is None or not employee.active or not verify_password(body.password, employee.password_hash):
        raise HTTPException(status_code=401, detail="Email or password is incorrect")
    return LoginResponse(
        access_token=create_access_token(employee.id, employee.role, employee.store_id),
        employee=EmployeeView(id=employee.id, name=employee.name, role=employee.role, store_id=employee.store_id),
    )
