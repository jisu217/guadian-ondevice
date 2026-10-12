# Question Guide

Status: drafted 2026-10-05 for step 4 of the [Verdict Design](../../../docs/design.md).
The teacher reads this guide, from "Scope" on, as its instructions for answering
the six questions, and people reviewing answers read the same text. The risk is
not asked: the verdict table (`ml/runtime/verdict_table.json`) sets it from these
answers and from facts code computes. The [Labeling Guide](labeling-guide.md),
which asked for a risk, stays as the record of the guide labels kept as a fallback.

## Scope

An answer describes the page description in the prompt: address, landing host,
status, title, visible text on the first screen, the fields of visible forms,
APK links, the official-domain and brand-mention lines, and host-imitation
findings. Nothing else counts: not later steps of a flow, not fields in a closed
popup, not what the live page shows today. When the description does not show
something, the answer is no.

Do not judge what code already knows. Whether the site is the brand's own,
whether the page opened, whether it shows name, phone, registration, or card
number fields, and whether it links an `.apk` file are computed separately.
Answer the questions as if those facts were unknown.

## The questions

### claims

Does the page present itself as a brand, bank, courier, card company, telecom,
email provider, or government agency other than whoever the address belongs to?
Answer with the name it claims, as the page writes it, or `null`.

It claims a brand when the title, the visible text, or a form presents the page
as that organization: "KB국민은행 본인인증", "Netflix Sign In", "Indiquez votre
compte Orange", a parcel notice in the name of 우체국, an "official site" of a
wallet the domain does not belong to.

A sign-in screen that asks for the password of the visitor's own email, bank, or
cloud account ("Mail - Self Service Portal", "Email Login", "Verify your mailbox")
claims that service even when it names no brand: answer with the service as the
page names it, or "webmail", "bank", or "cloud storage".

It does not claim a brand when it only names one: a social-login button ("네이버로
로그인"), a payment method, a news article or review about the brand, a comparison
of several brands, or a shop selling the brand's goods. Partners, agents, and
resellers that sell a brand's products under their own name (an insurance agency
selling 라이나생명 policies, a rental agency for 롯데렌터카) do not claim it. A
site presenting its own name is not a claim, even when the address spells that
name differently (a Korean shop name on a romanized domain). A brand name in the
title of a page with no other content (an empty blog) is not a claim either.

Give `evidence`: one whole line of the description, copied exactly with its
Korean label (for example `제목: ...`, `브랜드 언급: ...`, `본문: ...`), that shows
the claim. Without such a line the answer is `null`.

### sign_in

Is the page itself a sign-in screen: its main content asks the visitor to log in
or verify an account, with a password or email field and little else?

Not a sign-in screen: a sign-in box in the header or side of an ordinary page
(a web-hard file list, a community board, a news site, a shop), a comment or
post password box, a member menu ("로그인 회원가입"), a "로그인이 필요합니다"
notice above other content, or text that only says to log in.

### payment

Does the page push an immediate payment or subscription (pay now, a deposit, an
auto-renewing trial, a fee to release a parcel or a refund) without naming a
seller in the shown text?

An ordinary product or service page is not a payment push. The shown text is cut
short, so a business number missing from it is not evidence.

### investment

Does the page offer returns, side income, trading signals, or paid "leading"
rooms, including in article or course form?

Pages that inform without pushing an offer are not: news articles, calculators,
university courses, and law firms or agencies warning about investment scams.

### reward_pressure

Is a reward (free, prize, gift, winning, cashback) offered together with time
pressure (today only, first come, a deadline, a countdown)?

Not reward pressure: a reward word alone, such as free shipping or a free
consultation; a news article reporting someone else's event; a dated event
period on a campaign page that does not press the visitor to act before it ends.

### content_farm

Is the page's main purpose ad clicks, shown by at least two of: no author or
source, ads crowding the text, bait links or a fake search box, product lists
written for search engines?

Not a content farm: a community board or forum, a news site, a shop, a web-hard
or streaming catalog, a company site, or a blog post with an author, even when
it carries ads.

## Answer format

Answer every key, with `claims` a string or `null`, `evidence` a string (empty
when `claims` is `null`), and the others `true` or `false`.
