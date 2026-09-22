# Baseline Evaluation

Samples: 120
Accuracy: 86.67%

## Label Distribution

| Label | Actual | Predicted |
| :-- | --: | --: |
| LOW | 30 | 30 |
| MEDIUM | 30 | 36 |
| HIGH | 30 | 22 |
| UNKNOWN | 30 | 32 |

## Predictions

| Actual | Predicted | Score | URL |
| :-- | :-- | --: | :-- |
| LOW | LOW | 0 | https://example.com |
| LOW | LOW | 0 | https://support.example.com/help |
| HIGH | HIGH | 5 | https://unknown-site.test/download/app.apk |
| HIGH | HIGH | 5 | https://bank-login-verification.test/account |
| MEDIUM | MEDIUM | 3 | https://promo-reward-event.test/free-gift |
| MEDIUM | MEDIUM | 3 | https://short.test/a9x2?p=promo |
| UNKNOWN | UNKNOWN | 1 | https://normal-blog.test/posts/mobile-security |
| HIGH | MEDIUM | 3 | https://account-update.test/pay?session=redacted |
| LOW | LOW | 0 | https://example.com/privacy |
| LOW | LOW | 0 | https://example.com/downloads |
| LOW | LOW | 0 | https://support.example.com/account/recovery |
| LOW | LOW | 0 | https://docs.example.com/mobile/security |
| LOW | LOW | 0 | https://news.example.com/events |
| HIGH | HIGH | 5 | https://install-update.test/mobile/app-release.apk |
| HIGH | HIGH | 5 | https://kakao-login-security.test/verify |
| HIGH | HIGH | 5 | https://naver-account-check.test/login |
| HIGH | HIGH | 6 | https://google-prize-auth.test/signin |
| HIGH | HIGH | 8 | https://samsung-event-install.test/download |
| HIGH | MEDIUM | 3 | https://toss-pay-update.test/card?session=redacted |
| MEDIUM | MEDIUM | 2 | https://reward-center.test/event/coupon |
| MEDIUM | MEDIUM | 2 | https://free-gift-box.test/winner |
| MEDIUM | HIGH | 4 | https://promo-login.test/member/event |
| MEDIUM | MEDIUM | 2 | https://go.short.test/r/7xz |
| MEDIUM | MEDIUM | 2 | https://coupon-event.test/apply?ref=redacted |
| MEDIUM | MEDIUM | 3 | https://limited-reward.test/free-entry |
| UNKNOWN | UNKNOWN | 1 | https://community-post.test/thread/12345 |
| UNKNOWN | UNKNOWN | 1 | https://landing-page.test/info |
| UNKNOWN | UNKNOWN | 1 | https://redirect-holder.test/out?target=redacted |
| UNKNOWN | UNKNOWN | 1 | https://mobile-guide.test/setup |
| UNKNOWN | UNKNOWN | 1 | https://survey-form.test/start |
| UNKNOWN | UNKNOWN | 1 | https://unknown-service.test/notice |
| LOW | LOW | 0 | https://www.google.com |
| LOW | LOW | 0 | https://www.apple.com |
| LOW | LOW | 0 | https://www.samsung.com |
| LOW | LOW | 0 | https://www.microsoft.com |
| LOW | LOW | 0 | https://www.cloudflare.com |
| LOW | LOW | 0 | https://www.wikipedia.org |
| LOW | LOW | 0 | https://github.com |
| LOW | LOW | 0 | https://docs.github.com |
| LOW | LOW | 0 | https://www.kakaocorp.com |
| LOW | LOW | 0 | https://www.naver.com |
| LOW | LOW | 0 | https://www.toss.im |
| LOW | LOW | 0 | https://www.iana.org/domains/reserved |
| LOW | LOW | 0 | https://developer.android.com |
| LOW | LOW | 0 | https://kotlinlang.org/docs/home.html |
| LOW | LOW | 0 | https://www.python.org/doc |
| LOW | LOW | 0 | https://safe-reference.test/help |
| LOW | LOW | 0 | https://trusted-service.test/notice |
| LOW | LOW | 0 | https://verified-support.test/faq |
| LOW | LOW | 0 | https://official-store.test/app |
| LOW | LOW | 0 | https://customer-center.test/security-guide |
| LOW | LOW | 0 | https://docs.safe-example.test/install-guide |
| LOW | LOW | 0 | https://news.safe-example.test/mobile-policy |
| LOW | LOW | 0 | https://status.safe-example.test/incidents |
| MEDIUM | MEDIUM | 2 | https://reward-claim-center.test/apply |
| MEDIUM | MEDIUM | 2 | https://event-coupon-box.test/receive |
| MEDIUM | MEDIUM | 2 | https://free-point-gift.test/member |
| MEDIUM | MEDIUM | 2 | https://limited-promo.test/today-only |
| MEDIUM | MEDIUM | 2 | https://winner-notice.test/prize |
| MEDIUM | HIGH | 4 | https://coupon-apply.test/login |
| MEDIUM | HIGH | 4 | https://event-page.test/auth |
| MEDIUM | MEDIUM | 2 | https://giftbox-promo.test/open |
| MEDIUM | MEDIUM | 2 | https://member-benefit.test/free |
| MEDIUM | MEDIUM | 3 | https://short-link.test/go/promo |
| MEDIUM | MEDIUM | 3 | https://go-event.test/r/42 |
| MEDIUM | MEDIUM | 2 | https://mobile-coupon.test/apply?ref=redacted |
| MEDIUM | MEDIUM | 2 | https://today-reward.test/check?campaign=redacted |
| MEDIUM | MEDIUM | 2 | https://lucky-draw.test/event |
| MEDIUM | UNKNOWN | 1 | https://bonus-credit.test/register |
| MEDIUM | MEDIUM | 2 | https://membership-upgrade.test/promo |
| MEDIUM | MEDIUM | 2 | https://voucher-event.test/claim |
| MEDIUM | MEDIUM | 2 | https://survey-reward.test/start |
| MEDIUM | MEDIUM | 3 | https://app-benefit.test/event/free |
| MEDIUM | MEDIUM | 2 | https://notice-coupon.test/confirm |
| MEDIUM | MEDIUM | 2 | https://one-time-benefit.test/reward |
| MEDIUM | MEDIUM | 3 | https://promo-center.test/win |
| HIGH | HIGH | 5 | https://secure-kakao-login.test/account |
| HIGH | HIGH | 5 | https://naver-security-check.test/verify |
| HIGH | HIGH | 6 | https://google-account-prize.test/auth |
| HIGH | MEDIUM | 3 | https://apple-id-confirm.test/signin |
| HIGH | HIGH | 6 | https://samsung-members-reward.test/login |
| HIGH | HIGH | 5 | https://toss-card-security.test/verify |
| HIGH | HIGH | 5 | https://bank-auth-center.test/login |
| HIGH | UNKNOWN | 1 | https://secure-pay-update.test/card |
| HIGH | HIGH | 5 | https://apk-fast-install.test/download/app.apk |
| HIGH | HIGH | 5 | https://mobile-vaccine-update.test/install.apk |
| HIGH | HIGH | 5 | https://viewer-plugin.test/download/plugin.apk |
| HIGH | HIGH | 6 | https://coupon-app-install.test/event/app.apk |
| HIGH | HIGH | 5 | https://delivery-track-app.test/download |
| HIGH | MEDIUM | 3 | https://loan-approval-login.test/account |
| HIGH | MEDIUM | 3 | https://refund-center-auth.test/payment |
| HIGH | MEDIUM | 3 | https://identity-check-form.test/verify |
| HIGH | UNKNOWN | 1 | https://parcel-fee-payment.test/card |
| HIGH | MEDIUM | 3 | https://cloud-storage-login.test/password |
| HIGH | MEDIUM | 3 | https://urgent-account-lock.test/signin |
| HIGH | MEDIUM | 3 | https://official-looking-bank.test/secure |
| HIGH | HIGH | 4 | https://reward-bank-card.test/claim |
| UNKNOWN | UNKNOWN | 1 | https://plain-content.test/article/alpha |
| UNKNOWN | UNKNOWN | 1 | https://user-forum.test/topic/mobile |
| UNKNOWN | UNKNOWN | 1 | https://landing-info.test/about |
| UNKNOWN | UNKNOWN | 1 | https://files-index.test/list |
| UNKNOWN | UNKNOWN | 1 | https://web-notice.test/update |
| UNKNOWN | UNKNOWN | 1 | https://redirect-info.test/out?target=redacted |
| UNKNOWN | UNKNOWN | 1 | https://mobile-content.test/view/9081 |
| UNKNOWN | UNKNOWN | 1 | https://campaign-page.test/detail |
| UNKNOWN | UNKNOWN | 1 | https://unknown-host.test/home |
| UNKNOWN | UNKNOWN | 1 | https://service-notice.test/read |
| UNKNOWN | UNKNOWN | 1 | https://article-share.test/post/security |
| UNKNOWN | UNKNOWN | 1 | https://document-viewer.test/readme |
| UNKNOWN | MEDIUM | 2 | https://event-summary.test/report |
| UNKNOWN | UNKNOWN | 1 | https://app-guide.test/tutorial |
| UNKNOWN | UNKNOWN | 1 | https://content-hub.test/mobile/story |
| UNKNOWN | UNKNOWN | 1 | https://safe-or-risky.test/check |
| UNKNOWN | UNKNOWN | 1 | https://neutral-landing.test/welcome |
| UNKNOWN | UNKNOWN | 1 | https://product-info.test/mobile |
| UNKNOWN | UNKNOWN | 1 | https://external-page.test/ref?source=redacted |
| UNKNOWN | UNKNOWN | 1 | https://general-update.test/news |
| UNKNOWN | UNKNOWN | 1 | https://public-board.test/notice/2026 |
| UNKNOWN | UNKNOWN | 1 | https://generic-service.test/path |
| UNKNOWN | UNKNOWN | 1 | https://open-info.test/mobile/page |

