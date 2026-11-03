from datetime import date, timedelta

from sqlalchemy.orm import Session

from app.models import InventoryLot
from app.config import settings


def minimum_shelf_life_days() -> int:
    """Store policy: do not dispatch groceries with less than this much shelf life."""
    return max(0, settings.minimum_shelf_life_days)


def freshness_status(expires_on: date, today: date | None = None) -> str:
    today = today or date.today()
    if expires_on <= today:
        return "expired"
    if expires_on < today + timedelta(days=minimum_shelf_life_days()):
        return "near_expiry"
    return "safe"


def recommended_lot(db: Session, store_id: str, sku_id: str, today: date | None = None) -> InventoryLot | None:
    today = today or date.today()
    cutoff = today + timedelta(days=minimum_shelf_life_days())
    return db.query(InventoryLot).filter(
        InventoryLot.store_id == store_id,
        InventoryLot.sku_id == sku_id,
        InventoryLot.quantity > 0,
        InventoryLot.expires_on > today,
        InventoryLot.expires_on >= cutoff,
    ).order_by(InventoryLot.expires_on.asc(), InventoryLot.lot_code.asc()).first()


def lot_is_dispatchable(lot: InventoryLot, today: date | None = None) -> bool:
    return lot.quantity > 0 and freshness_status(lot.expires_on, today) == "safe"
