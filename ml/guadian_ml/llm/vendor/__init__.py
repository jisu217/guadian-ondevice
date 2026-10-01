"""Files copied unchanged from the original GUADIAN repositories (see docs/llm/dataset.md).

They import each other as top-level modules (`import domains`), and editing them is
not allowed, so their directory is put on the import path here. Import them as
`from guadian_model.llm.vendor import judge_agent`.
"""

import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)
