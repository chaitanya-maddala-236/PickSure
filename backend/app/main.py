from contextlib import asynccontextmanager

from fastapi import FastAPI

from app import models  # noqa: F401
from app.api import audit, auth, inventory, packing, scanning, substitutions, sync, tasks
from app.config import settings
from app.db import Base, SessionLocal, engine
from app.seed import seed_demo


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(bind=engine)
    if settings.demo_seed:
        with SessionLocal() as db:
            seed_demo(db)
    yield


app = FastAPI(title="PickSure Fulfillment API", version="1.0.0", lifespan=lifespan)
for router in (auth.router, tasks.router, scanning.router, inventory.router, sync.router, packing.router, substitutions.router, audit.router):
    app.include_router(router, prefix="/api/v1")


@app.get("/health", tags=["Health"])
def health():
    return {"status": "ok", "service": "picksure-api"}
