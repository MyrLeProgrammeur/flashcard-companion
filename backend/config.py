from pathlib import Path

import yaml


def load_config(config_path: Path = Path(__file__).parent / "config.yaml") -> dict:
    with open(config_path) as f:
        cfg = yaml.safe_load(f)
    for key in ("apkg_dir", "pdf_dir", "data_dir"):
        cfg["paths"][key] = str(Path(cfg["paths"][key]).expanduser())
    return cfg
