# Labeling Guide

Status: adopted 2026-10-05. The teacher labeled the 2026-10 dataset with this
guide, reading the text from "Principles" on, and `dataset/final_labels.py`
applies the rules here that need no judgment; `ml/docs/llm/dataset.md` ("Labels")
describes both.

This guide replaces the original criteria plus the patch rules added since. It
is given to the teacher as its instructions and to people reviewing labels, so
both read the same text.

## Principles

A label is how strongly the app steps in, not how bad a page is. `HIGH` blocks
the visit, `MEDIUM` warns and leaves the choice to the user, `LOW` lets it
through. Following the original service's grading
(`reference/original-guadian/docs/risk-grade-rationale.md`), a page is `HIGH`
only when three things hold: the risk is seen on the page, not guessed
(certainty); it can lead to account theft, money loss, device takeover, or the
leak of information that cannot be changed (severity); and the site is not the
brand's own (identity). When one of them is missing the label is at most
`MEDIUM`, because blocking an ordinary page is also a harm.

## Scope

A label describes the model's input, the prompt built from what the phone sees
on the first screen: address, landing host, status, title, visible text, the
fields of visible forms, APK links, the official-domain and brand-mention lines,
and host-imitation findings. Nothing else counts: not later steps of a flow,
not fields in a closed popup, not domain age or blocklist membership, not what
the live page shows today.

## Decision order

Go down the list and stop at the first rule that applies. The type is the one
named by that rule.

0. **The brand's own site.** When the official-domain line names a brand for
   the landing host, or the host is plainly the brand's own domain in the brand
   table, the label is `LOW none`, whatever the page asks for (see Official
   sites), except that an `.apk` link is `MEDIUM apk`: an install from outside
   the store skips the store's checks even on the brand's own site. This comes
   first: a real bank's or Facebook's own login page is not a credentials page.
1. **The page did not load or shows almost nothing** (no page, an error
   status, a blank or redirect-only page). `LOW none` if the landing host is
   the brand's own site (see Official sites); otherwise `MEDIUM unverifiable`.
   A bot check or "Access Denied" page is not labeled: it is often an artifact
   of collection, and the phone's WebView may load the page, so such cases are
   left out of training and evaluation.
2. **Off-store app install.** A link to an `.apk` file. `HIGH apk`: off the
   brand's own site nothing confirms who made the app, and an installed app can
   take over the phone.
3. **Impersonation.** The page presents itself as a brand, bank, courier, or
   agency, by title, text, or form, and the landing host is not that
   organization's domain. `HIGH impersonation`. A host that embeds a brand's
   official domain under another domain (`roblox.com.mu`) or takes a brand's
   name under another suffix (`roblox.ly`) is `HIGH impersonation` by itself.
   A host that only pairs a brand name with an account word
   (`xfinityuserverification.weebly.com`) while the page claims nothing is
   `MEDIUM impersonation`. Naming a brand, a payment method, a social login,
   or a partner is not impersonation.
4. **Credentials.** A form asks for a resident registration number or a card
   number, or for the password of an account that is not the site's own
   (webmail, a bank, a cloud drive) on a host that is not that service's.
   `HIGH credentials`. When the page itself is a sign-in screen for the site's
   own service, with a password or email field and little else, it is `MEDIUM
   credentials`: an ad that leads straight to a sign-in screen is a common
   phishing step, and nothing confirms who runs the site. A sign-in box in the
   header or side of an ordinary page, or a password box for posting a comment,
   is not a credentials page; neither is text that only says to log in.
5. **Personal information.** A visible field asks for a name or phone number,
   or a form with fields other than search or email is shown and the page text names a contact
   field (연락처, 휴대폰, 전화번호) or asks consent to collect personal information
   (개인정보 수집·이용 동의). `MEDIUM personal_info`. Wording without such a form,
   such as a "상담신청" or "무료견적" menu link, is not personal information, and
   neither is an email field alone. The app reads the model's answer by the same
   rule (`contract/answer.py`, `personal_info_unsupported`).
6. **Payment.** The page pushes an immediate payment or subscription (pay now,
   a deposit, an auto-renewing trial) and names no seller in the shown text.
   `MEDIUM payment`. An ordinary product or service page is not payment: the
   shown text is cut short, so a business number missing from it is not
   evidence.
7. **Urgency.** A reward (free, prize, gift, winning) together with time
   pressure (today only, first come, deadline, a countdown). `MEDIUM urgency`.
   A reward word alone, such as free shipping, is not urgency.
8. **Investment.** The page offers returns, side income, trading signals, or
   paid "leading" rooms, including in article or course form. `MEDIUM
   investment`. Pages that inform without pushing an offer are `LOW`: news
   articles, calculators, university courses, and law firms or agencies
   warning about investment scams.
9. **Content farm.** A page whose main purpose is ad clicks, shown by at least
   two of: no author or source, ads crowding the text, bait links or a fake
   search box, product lists written for search engines. `MEDIUM contentfarm`.
10. **Otherwise** `LOW none`: the content was read and none of the above holds.

## Official sites

A host the brand table owns (`ml/runtime/llm_brands.json`) is the brand's own
site, and its pages are `LOW` whatever they collect (an `.apk` link aside): the service guards against
harmful ads, not a brand's own consultation form. The exception is a platform
where anyone publishes on the brand's domain (blogs, cafes, channels, shared
files and forms such as `blog.naver.com`, `pf.kakao.com`, `docs.google.com`,
`dropbox.com`); there the rules above apply to the content.

## Evidence

A `HIGH` answer names one whole line of the page description, copied exactly
with its label (for example `주소 흉내: host roblox.ly takes the name of
roblox.com on roblox.ly`), that supports it: a brand-mention, host-imitation,
form, or APK line, or the title. A `HIGH`
without such a line is `MEDIUM` of the same type. Other answers carry no
evidence.
