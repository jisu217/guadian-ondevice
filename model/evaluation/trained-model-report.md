# Trained Model Evaluation

Model: nearest-centroid classifier
Train samples: 96
Test samples: 24
Model accuracy: 91.67%
Baseline accuracy on same test split: 83.33%

## Label Distribution

| Label | Actual | Predicted |
| :-- | --: | --: |
| LOW | 6 | 6 |
| MEDIUM | 6 | 8 |
| HIGH | 6 | 5 |
| UNKNOWN | 6 | 5 |

## Precision And Recall

| Label | Precision | Recall | Support | Baseline Precision | Baseline Recall |
| :-- | --: | --: | --: | --: | --: |
| LOW | 100.00% | 100.00% | 6 | 100.00% | 100.00% |
| MEDIUM | 75.00% | 100.00% | 6 | 66.67% | 66.67% |
| HIGH | 100.00% | 83.33% | 6 | 71.43% | 83.33% |
| UNKNOWN | 100.00% | 83.33% | 6 | 100.00% | 83.33% |

## Confusion Matrix

| Actual \ Predicted | LOW | MEDIUM | HIGH | UNKNOWN |
| :-- | --: | --: | --: | --: |
| LOW | 6 | 0 | 0 | 0 |
| MEDIUM | 0 | 6 | 0 | 0 |
| HIGH | 0 | 1 | 5 | 0 |
| UNKNOWN | 0 | 1 | 0 | 5 |

## Predictions

| Actual | Predicted | Distance | URL |
| :-- | :-- | --: | :-- |
| LOW | LOW | 1.3524 | https://support.example.com/account/recovery |
| LOW | LOW | 1.0510 | https://www.samsung.com |
| LOW | LOW | 0.5955 | https://docs.github.com |
| LOW | LOW | 0.6598 | https://developer.android.com |
| LOW | LOW | 1.0503 | https://verified-support.test/faq |
| LOW | LOW | 1.0994 | https://status.safe-example.test/incidents |
| MEDIUM | MEDIUM | 1.0890 | https://promo-login.test/member/event |
| MEDIUM | MEDIUM | 0.4843 | https://event-coupon-box.test/receive |
| MEDIUM | MEDIUM | 1.0994 | https://event-page.test/auth |
| MEDIUM | MEDIUM | 0.8376 | https://mobile-coupon.test/apply?ref=redacted |
| MEDIUM | MEDIUM | 0.4567 | https://voucher-event.test/claim |
| MEDIUM | MEDIUM | 0.8184 | https://promo-center.test/win |
| HIGH | HIGH | 0.8288 | https://kakao-login-security.test/verify |
| HIGH | HIGH | 0.8189 | https://secure-kakao-login.test/account |
| HIGH | HIGH | 0.8254 | https://toss-card-security.test/verify |
| HIGH | HIGH | 1.1576 | https://viewer-plugin.test/download/plugin.apk |
| HIGH | HIGH | 0.7153 | https://identity-check-form.test/verify |
| HIGH | MEDIUM | 1.1081 | https://reward-bank-card.test/claim |
| UNKNOWN | UNKNOWN | 0.2627 | https://mobile-guide.test/setup |
| UNKNOWN | UNKNOWN | 0.2627 | https://landing-info.test/about |
| UNKNOWN | UNKNOWN | 0.2013 | https://campaign-page.test/detail |
| UNKNOWN | MEDIUM | 0.4611 | https://event-summary.test/report |
| UNKNOWN | UNKNOWN | 0.2187 | https://product-info.test/mobile |
| UNKNOWN | UNKNOWN | 0.3590 | https://open-info.test/mobile/page |
