"""Recognize pages a bot defense served to the collector in place of the site.

The collector runs headless Chromium from one address, which CDNs and shops often
block. The phone's WebView usually gets the real page, so a block page says nothing
about the site, and the labeling guide (rule 1) leaves such cases out of training
and evaluation.

Only a refused response (status 400 or above) with one of the wordings below counts.
A fake "verify you are human" step that a phishing page serves with status 200 is
part of that page and stays labeled, and so do Cloudflare's phishing and malware
warnings, which say something about the site.
"""

from __future__ import annotations

import re

from guadian_ml.llm.contract.prompt import PageObservation

WORDINGS = (
    ("akamai", re.compile(r"you don't have permission to access", re.I)),
    ("cloudflare_block", re.compile(r"sorry, you have been blocked", re.I)),
    ("cloudflare_challenge", re.compile(r"보안 확인 수행 중|checking if the site connection is secure"
                                        r"|checking your browser|verify you are human", re.I)),
    ("google_sorry", re.compile(r"비정상적인 트래픽|unusual traffic from your computer network", re.I)),
    ("robot_check", re.compile(r"로봇인가요, 아닌가요|are you a person or a robot", re.I)),
    ("automation_block", re.compile(r"자동화된 (?:환경|비정상 접근)")),
)


def bot_check(obs: PageObservation) -> str | None:
    """The name of the block page obs shows, or None when it shows the site."""
    if (obs.status or 0) < 400:
        return None
    head = f"{obs.title}\n{obs.text[:500]}"
    return next((name for name, wording in WORDINGS if wording.search(head)), None)
