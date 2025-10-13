"""``python -m lexdrift``."""

from __future__ import annotations

import sys

from lexdrift.cli import build_cli
from lexdrift.cli.main import main

if __name__ == "__main__":
    build_cli()
    sys.exit(main())
