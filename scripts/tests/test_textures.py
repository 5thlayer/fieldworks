# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
import subprocess
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "build-textures.py"


class TexturesTest(unittest.TestCase):
    def test_committed_sprites_match_the_generator(self):
        result = subprocess.run([sys.executable, str(SCRIPT), "--check"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
