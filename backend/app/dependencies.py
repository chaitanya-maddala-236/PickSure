from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
from sqlalchemy.orm import Session

from app.config import settings
from app.db import get_db
from app.models import Employee

bearer = HTTPBearer(auto_error=False)


def current_employee(credentials: HTTPAuthorizationCredentials | None = Depends(bearer), db: Session = Depends(get_db)) -> Employee:
    if credentials is None:
        raise HTTPException(status_code=401, detail="Sign in is required")
    try:
        payload = jwt.decode(credentials.credentials, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
        employee_id = payload.get("sub")
    except JWTError as exc:
        raise HTTPException(status_code=401, detail="Invalid or expired access token") from exc
    employee = db.get(Employee, employee_id)
    if employee is None or not employee.active:
        raise HTTPException(status_code=401, detail="Employee account is unavailable")
    return employee


def supervisor_or_admin(employee: Employee = Depends(current_employee)) -> Employee:
    if employee.role not in {"supervisor", "admin"}:
        raise HTTPException(status_code=403, detail="Supervisor access is required")
    return employee
