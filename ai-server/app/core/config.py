import logging
import logging.handlers
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[3]


def setup_logging() -> None:
    log_dir = ROOT_DIR / "logs"
    log_dir.mkdir(exist_ok=True)
    fmt = logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s")

    file_handler = logging.handlers.TimedRotatingFileHandler(
        log_dir / "app.log",
        when="midnight",
        backupCount=7,
        encoding="utf-8",
    )
    file_handler.setFormatter(fmt)

    root = logging.getLogger()
    root.setLevel(logging.INFO)
    root.addHandler(file_handler)

    for noisy in ("openai", "httpx", "httpcore", "urllib3", "langchain"):
        logging.getLogger(noisy).setLevel(logging.WARNING)
