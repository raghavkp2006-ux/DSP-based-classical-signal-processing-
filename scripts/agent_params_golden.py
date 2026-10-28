"""Generate the decision policy grid directly from Python's decision_agent."""
import itertools
import json
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from decision_agent import decide
rows = []
for snr, stationary, activity in itertools.product((-5, 0, 4.9, 5, 10, 15, 15.1, 25), (True, False), (0.1, 0.29, 0.3, 0.8)):
    rows.append(dict(snr=snr, stationary=stationary, activity=activity, **decide(snr, stationary, activity)))
out = ROOT / 'signalchain-android/app/src/test/resources/agent_params_golden.json'
out.write_text(json.dumps(rows, indent=2) + '\n', encoding='utf-8')
print(f'{len(rows)} rows -> {out}')
