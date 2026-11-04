import os


class Settings:
    database_url: str = os.getenv("DATABASE_URL", "sqlite:///./picksure.db")
    jwt_secret: str = os.getenv("JWT_SECRET", "local-picksure-development-secret-change-before-deploy")
    access_token_minutes: int = int(os.getenv("ACCESS_TOKEN_MINUTES", "720"))
    minimum_shelf_life_days: int = int(os.getenv("MINIMUM_SHELF_LIFE_DAYS", "3"))
    demo_seed: bool = os.getenv("DEMO_SEED", "true").lower() == "true"
    gemini_api_key: str | None = os.getenv("GEMINI_API_KEY")
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
    jwt_algorithm: str = "HS256"


settings = Settings()
