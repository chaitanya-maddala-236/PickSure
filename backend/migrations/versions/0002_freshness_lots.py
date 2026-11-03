"""Add expiry-tracked inventory lots and lot traceability to pick events.

Revision ID: 0002_freshness_lots
Revises: 0001_initial
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy import inspect

revision = "0002_freshness_lots"
down_revision = "0001_initial"
branch_labels = None
depends_on = None


def upgrade():
    bind = op.get_bind()
    inspector = inspect(bind)
    tables = set(inspector.get_table_names())
    if "inventory_lots" not in tables:
        op.create_table(
            "inventory_lots",
            sa.Column("id", sa.String(length=36), nullable=False),
            sa.Column("store_id", sa.String(length=36), nullable=False),
            sa.Column("sku_id", sa.String(length=36), nullable=False),
            sa.Column("lot_code", sa.String(length=80), nullable=False),
            sa.Column("lot_tag", sa.String(length=120), nullable=False),
            sa.Column("expires_on", sa.Date(), nullable=False),
            sa.Column("quantity", sa.Integer(), nullable=False),
            sa.Column("aisle", sa.String(length=24), nullable=False),
            sa.Column("rack", sa.String(length=24), nullable=False),
            sa.Column("shelf", sa.String(length=24), nullable=False),
            sa.ForeignKeyConstraint(["sku_id"], ["products.id"]),
            sa.ForeignKeyConstraint(["store_id"], ["stores.id"]),
            sa.PrimaryKeyConstraint("id"),
            sa.UniqueConstraint("store_id", "sku_id", "lot_code", name="uq_inventory_lot_code"),
            sa.UniqueConstraint("lot_tag", name="uq_inventory_lot_tag"),
        )

    if "pick_events" in tables and "lot_id" not in {column["name"] for column in inspector.get_columns("pick_events")}:
        if bind.dialect.name == "sqlite":
            with op.batch_alter_table("pick_events") as batch:
                batch.add_column(sa.Column("lot_id", sa.String(length=36), nullable=True))
                batch.create_foreign_key("fk_pick_events_lot_id_inventory_lots", "inventory_lots", ["lot_id"], ["id"])
        else:
            op.add_column("pick_events", sa.Column("lot_id", sa.String(length=36), nullable=True))
            op.create_foreign_key("fk_pick_events_lot_id_inventory_lots", "pick_events", "inventory_lots", ["lot_id"], ["id"])


def downgrade():
    bind = op.get_bind()
    inspector = inspect(bind)
    if "pick_events" in inspector.get_table_names() and "lot_id" in {column["name"] for column in inspector.get_columns("pick_events")}:
        if bind.dialect.name == "sqlite":
            with op.batch_alter_table("pick_events") as batch:
                batch.drop_column("lot_id")
        else:
            op.drop_constraint("fk_pick_events_lot_id_inventory_lots", "pick_events", type_="foreignkey")
            op.drop_column("pick_events", "lot_id")
    if "inventory_lots" in inspect(bind).get_table_names():
        op.drop_table("inventory_lots")
