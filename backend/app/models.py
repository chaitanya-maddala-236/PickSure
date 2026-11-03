from datetime import date, datetime, timezone

from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Store(Base):
    __tablename__ = "stores"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    location: Mapped[str] = mapped_column(String(200), nullable=False)


class Employee(Base):
    __tablename__ = "employees"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(256), nullable=False)
    role: Mapped[str] = mapped_column(String(24), nullable=False, default="picker")
    store_id: Mapped[str] = mapped_column(ForeignKey("stores.id"), nullable=False)
    active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)


class Product(Base):
    __tablename__ = "products"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    sku: Mapped[str] = mapped_column(String(80), unique=True, index=True, nullable=False)
    name: Mapped[str] = mapped_column(String(160), nullable=False)
    barcode: Mapped[str] = mapped_column(String(80), unique=True, index=True, nullable=False)
    active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)


class ShelfLocation(Base):
    __tablename__ = "shelf_locations"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    sku_id: Mapped[str] = mapped_column(ForeignKey("products.id"), unique=True, nullable=False)
    aisle: Mapped[str] = mapped_column(String(24), nullable=False)
    rack: Mapped[str] = mapped_column(String(24), nullable=False)
    shelf: Mapped[str] = mapped_column(String(24), nullable=False)


class Inventory(Base):
    __tablename__ = "inventory"
    __table_args__ = (UniqueConstraint("store_id", "sku_id", name="uq_inventory_store_sku"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    store_id: Mapped[str] = mapped_column(ForeignKey("stores.id"), nullable=False)
    sku_id: Mapped[str] = mapped_column(ForeignKey("products.id"), nullable=False)
    on_hand: Mapped[int] = mapped_column(Integer, default=0, nullable=False)


class InventoryLot(Base):
    __tablename__ = "inventory_lots"
    __table_args__ = (
        UniqueConstraint("store_id", "sku_id", "lot_code", name="uq_inventory_lot_code"),
        UniqueConstraint("lot_tag", name="uq_inventory_lot_tag"),
    )
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    store_id: Mapped[str] = mapped_column(ForeignKey("stores.id"), nullable=False)
    sku_id: Mapped[str] = mapped_column(ForeignKey("products.id"), nullable=False)
    lot_code: Mapped[str] = mapped_column(String(80), nullable=False)
    lot_tag: Mapped[str] = mapped_column(String(120), nullable=False)
    expires_on: Mapped[date] = mapped_column(Date, nullable=False)
    quantity: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    aisle: Mapped[str] = mapped_column(String(24), nullable=False)
    rack: Mapped[str] = mapped_column(String(24), nullable=False)
    shelf: Mapped[str] = mapped_column(String(24), nullable=False)


class FulfillmentOrder(Base):
    __tablename__ = "orders"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    customer_id: Mapped[str] = mapped_column(String(80), nullable=False)
    store_id: Mapped[str] = mapped_column(ForeignKey("stores.id"), nullable=False)
    status: Mapped[str] = mapped_column(String(24), default="picking", nullable=False)
    priority: Mapped[str] = mapped_column(String(24), default="normal", nullable=False)


class PickTask(Base):
    __tablename__ = "pick_tasks"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    order_id: Mapped[str] = mapped_column(ForeignKey("orders.id"), unique=True, nullable=False)
    employee_id: Mapped[str] = mapped_column(ForeignKey("employees.id"), nullable=False)
    status: Mapped[str] = mapped_column(String(24), default="assigned", nullable=False)
    order = relationship("FulfillmentOrder")
    items = relationship("PickTaskItem", back_populates="task", cascade="all, delete-orphan")


class PickTaskItem(Base):
    __tablename__ = "pick_task_items"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    task_id: Mapped[str] = mapped_column(ForeignKey("pick_tasks.id"), index=True, nullable=False)
    sku_id: Mapped[str] = mapped_column(ForeignKey("products.id"), nullable=False)
    requested_qty: Mapped[int] = mapped_column(Integer, nullable=False)
    picked_qty: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    status: Mapped[str] = mapped_column(String(24), default="open", nullable=False)
    product = relationship("Product")
    task = relationship("PickTask", back_populates="items")


class PickEvent(Base):
    __tablename__ = "pick_events"
    __table_args__ = (UniqueConstraint("event_uuid", name="uq_pick_event_uuid"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    task_item_id: Mapped[str] = mapped_column(ForeignKey("pick_task_items.id"), nullable=False)
    lot_id: Mapped[str | None] = mapped_column(ForeignKey("inventory_lots.id"))
    employee_id: Mapped[str] = mapped_column(ForeignKey("employees.id"), nullable=False)
    event_uuid: Mapped[str] = mapped_column(String(36), index=True, nullable=False)
    event_type: Mapped[str] = mapped_column(String(24), nullable=False)
    quantity: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    barcode: Mapped[str | None] = mapped_column(String(80))
    reason: Mapped[str | None] = mapped_column(String(240))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, nullable=False)


class SyncEvent(Base):
    __tablename__ = "sync_events"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    event_uuid: Mapped[str] = mapped_column(String(36), unique=True, index=True, nullable=False)
    employee_id: Mapped[str] = mapped_column(ForeignKey("employees.id"), nullable=False)
    sync_status: Mapped[str] = mapped_column(String(24), nullable=False)
    result_json: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, nullable=False)


class Substitution(Base):
    __tablename__ = "substitutions"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    task_item_id: Mapped[str] = mapped_column(ForeignKey("pick_task_items.id"), nullable=False)
    original_sku: Mapped[str] = mapped_column(String(80), nullable=False)
    replacement_sku: Mapped[str] = mapped_column(String(80), nullable=False)
    reason: Mapped[str | None] = mapped_column(String(240))
    approval_status: Mapped[str] = mapped_column(String(24), nullable=False)
    approved_by: Mapped[str | None] = mapped_column(ForeignKey("employees.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, nullable=False)


class PackingRecord(Base):
    __tablename__ = "packing_records"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    order_id: Mapped[str] = mapped_column(ForeignKey("orders.id"), nullable=False)
    packer_id: Mapped[str] = mapped_column(ForeignKey("employees.id"), nullable=False)
    status: Mapped[str] = mapped_column(String(24), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, nullable=False)


class AuditLog(Base):
    __tablename__ = "audit_logs"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    employee_id: Mapped[str] = mapped_column(ForeignKey("employees.id"), nullable=False)
    action: Mapped[str] = mapped_column(String(80), nullable=False)
    entity_id: Mapped[str | None] = mapped_column(String(36))
    details: Mapped[str] = mapped_column(Text, default="{}", nullable=False)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, nullable=False)
