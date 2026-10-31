"""Reuse the shared explicit platform adapters; preserve the placement/preview regression scenarios."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parents[2]
subprocess.run([sys.executable,str(root/'tools/minimal-preview/run.py'),*sys.argv[1:],'--only-builds'],check=True)
