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

    # ⚠️ 로거 계층은 점(.)으로만 이어진다 — "langchain"은 langchain.* 만 덮고
    # langchain_anthropic / langchain_google_genai 는 별개의 최상위 로거다. 밑줄 패키지는
    # 하나씩 적어야 한다. ("google"은 점 패키지라 google.genai·google.auth를 함께 덮는다.)
    for noisy in (
        "anthropic",
        "google",
        "httpx",
        "httpcore",
        "urllib3",
        "langchain",
        "langchain_anthropic",
        "langchain_google_genai",
    ):
        logging.getLogger(noisy).setLevel(logging.WARNING)
