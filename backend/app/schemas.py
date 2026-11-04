from datetime import date, datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class LoginRequest(BaseModel):
    email: str
    password: str


class EmployeeView(BaseModel):
    id: str
    name: str
    role: str
    store_id: str


class LoginResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    employee: EmployeeView


class TaskItemView(BaseModel):
    id: str
    task_id: str
    sku: str
    product_name: str
    barcode: str
    requested_qty: int
    picked_qty: int
    status: str
    aisle: str
    rack: str
    shelf: str
    recommended_lot_id: str | None = None
    lot_code: str | None = None
    lot_tag: str | None = None
    expires_on: date | None = None
    lot_status: Literal["ready", "near_expiry_only", "no_stock"] = "no_stock"


class TaskView(BaseModel):
    id: str
    order_id: str
    status: str
    priority: str
    item_count: int
    picked_count: int


class ScanVerifyRequest(BaseModel):
    task_item_id: str
    barcode: str


class ScanVerifyResponse(BaseModel):
    valid: bool
    expected_sku: str
    scanned_sku: str | None = None
    product_name: str | None = None
    message: str


class LotVerifyRequest(BaseModel):
    task_item_id: str
    lot_id: str
    lot_tag: str


class LotVerifyResponse(BaseModel):
    valid: bool
    lot_id: str
    lot_code: str | None = None
    expires_on: date | None = None
    available_quantity: int = 0
    message: str


class FreshnessLotView(BaseModel):
    sku: str
    product_name: str
    lot_code: str
    lot_tag: str
    expires_on: date
    quantity: int
    status: Literal["safe", "near_expiry", "expired"]
    aisle: str
    rack: str
    shelf: str


class FreshnessSummary(BaseModel):
    minimum_shelf_life_days: int
    safe_units: int
    near_expiry_units: int
    expired_units: int
    lots: list[FreshnessLotView]


class FreshnessAdviceAction(BaseModel):
    lot_code: str
    recommendation: Literal["markdown_review", "donation_review", "quarantine_review"]
    reason: str


class FreshnessAdvice(BaseModel):
    provider: Literal["gemini", "rules"]
    model: str | None = None
    summary: str
    actions: list[FreshnessAdviceAction]


class InventoryView(BaseModel):
    sku: str
    product_name: str
    on_hand: int


class SyncEventIn(BaseModel):
    event_uuid: UUID
    event_type: Literal["PICK", "EXCEPTION", "SUBSTITUTION", "PACK"]
    task_id: str
    task_item_id: str | None = None
    sku: str | None = None
    barcode: str | None = None
    lot_id: str | None = None
    lot_tag: str | None = None
    quantity: int = Field(default=0, ge=0)
    reason: str | None = Field(default=None, max_length=240)
    replacement_sku: str | None = None
    approval_status: Literal["pending", "approved", "rejected"] | None = None
    created_at: datetime | None = None


class SyncBatchIn(BaseModel):
    events: list[SyncEventIn] = Field(min_length=1, max_length=100)


class SyncEventResult(BaseModel):
    event_uuid: UUID
    status: Literal["accepted", "duplicate", "rejected"]
    message: str
    server_event_id: str | None = None


class SyncBatchOut(BaseModel):
    results: list[SyncEventResult]


class ExceptionRequest(BaseModel):
    reason: str = Field(min_length=2, max_length=240)
    event_uuid: UUID


class SubstitutionRequest(BaseModel):
    task_item_id: str
    replacement_sku: str
    reason: str | None = Field(default=None, max_length=240)
    event_uuid: UUID | None = None
    approval_status: Literal["pending", "approved", "rejected"] = "pending"


class ApprovalDecision(BaseModel):
    decision: Literal["approved", "rejected"]


class SubstitutionView(BaseModel):
    id: str
    task_item_id: str
    original_sku: str
    replacement_sku: str
    reason: str | None = None
    approval_status: str


class PackingRequest(BaseModel):
    checklist_complete: bool
    event_uuid: UUID | None = None


class AuditView(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    employee_id: str
    action: str
    entity_id: str | None
    details: str
    timestamp: datetime
