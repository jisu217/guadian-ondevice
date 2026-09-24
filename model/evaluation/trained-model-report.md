# Trained Model Evaluation

Model: nearest-centroid classifier
Train samples: 520
Test samples: 128
Model accuracy: 72.66%
Model macro F1: 74.24%
Baseline accuracy on same test split: 68.75%

## Validation Protocol

- Final test metrics use a deterministic stratified holdout split.
- Threshold calibration uses 5-fold stratified cross-validation on the training split.
- Each label gets its own certain-distance threshold instead of one shared scalar,
  so one close call for label A can no longer collapse label B's threshold too.
- Thresholds are intentionally conservative: uncertain cases should fall through to local RAG or remote fallback.

### Calibrated Certain-Distance Thresholds

| Label | Threshold |
| :-- | --: |
| LOW | 0.3522 |
| MEDIUM | 0.7852 |
| HIGH | 1.0401 |
| UNKNOWN | 0.3066 |

## Cross Validation

Average accuracy: 72.52%
Average macro F1: 74.51%

| Fold | Train | Validation | Accuracy | Macro F1 |
| :-- | --: | --: | --: | --: |
| 1 | 414 | 106 | 68.87% | 71.32% |
| 2 | 416 | 104 | 72.12% | 74.23% |
| 3 | 416 | 104 | 75.00% | 77.36% |
| 4 | 417 | 103 | 71.84% | 73.10% |
| 5 | 417 | 103 | 74.76% | 76.55% |

### Cross-Validation Label Metrics

| Label | Precision | Recall | F1 | Support |
| :-- | --: | --: | --: | --: |
| LOW | 85.00% | 74.32% | 79.30% | 183 |
| MEDIUM | 86.60% | 47.73% | 61.54% | 176 |
| HIGH | 98.72% | 95.06% | 96.86% | 81 |
| UNKNOWN | 43.24% | 100.00% | 60.38% | 80 |

## Label Distribution

| Label | Actual | Predicted |
| :-- | --: | --: |
| LOW | 45 | 40 |
| MEDIUM | 43 | 29 |
| HIGH | 20 | 21 |
| UNKNOWN | 20 | 38 |

## Precision And Recall

| Label | Precision | Recall | F1 | Support | Baseline Precision | Baseline Recall |
| :-- | --: | --: | --: | --: | --: | --: |
| LOW | 82.50% | 73.33% | 77.65% | 45 | 86.84% | 73.33% |
| MEDIUM | 75.86% | 51.16% | 61.11% | 43 | 70.97% | 51.16% |
| HIGH | 90.48% | 95.00% | 92.68% | 20 | 87.50% | 70.00% |
| UNKNOWN | 50.00% | 95.00% | 65.52% | 20 | 44.19% | 95.00% |

## Error Analysis

| Actual | Predicted | Distance | URL |
| :-- | :-- | --: | :-- |
| LOW | UNKNOWN | 0.4890 | https://amc.seoul.kr/asan/healthinfo/management/managementDetail.do?managementId=365 |
| LOW | UNKNOWN | 0.3460 | https://shinsegaegroupnewsroom.com/?utm_source=mt&utm_medium=banner&utm_campaign=main |
| LOW | UNKNOWN | 0.5078 | https://m.inews24.com |
| LOW | UNKNOWN | 1.1145 | https://carrotins.com/mobile/calculation/car/personal/intro/?utm_campaign=car&utm_source=criteo&utm_medium=display&utm_content=adaptive_181850&utm_term=cv_go&afccd=PA01230&afcDtcd=cv_go&afcDtFlgcd=adaptive_181850&main=y&utm_id=706693&cto_pld=j1iwW8r2AQCSPXa-On52LQ |
| LOW | MEDIUM | 1.1520 | https://estore.wacom.kr/ko-kr/pen-tablet/wacomintuospro2025/wacom-intuos-pro-medium-ptk670k0c.html?utm_source=google&utm_medium=banner&utm_campaign=2607_gdn&utm_content=Intuospro&gad_source=5&gad_campaignid=24026560685&gclid=CjwKCAjw48TUBhBREiwAK0GnQQFOAVrdrR_tJ2Jt1cbaOcMAyvF5Wrxd1gf966buUaa-DuBR5nXSRhoC7RUQAvD_BwE |
| LOW | MEDIUM | 1.1685 | https://www.sktair.com/plan?ch=AIR_WEB_6&utm_source=google_da&utm_medium=none&utm_campaign=plan&utm_term=underkg&utm_content=air_air-plan_conversion__google_da__20260824__underkg&gad_source=5&gad_campaignid=24161740742&gclid=CjwKCAjw48TUBhBREiwAK0GnQQaNOSmp2q2L2onYxpGlIjEZighRuBzDssyU8v9zHhKFVaw6844uYRoChE0QAvD_BwE |
| LOW | UNKNOWN | 0.8000 | https://m.knnews.co.kr/mView.php?idxno=1549627 |
| LOW | UNKNOWN | 0.4722 | https://m.joongboo.com |
| LOW | MEDIUM | 1.3227 | https://api.haimlabs.co.kr/cou/kw/land.php?code=ck600736mob1280s&url=https%3A%2F%2Flink.coupang.com%2Fre%2FAFFSRP%3Flptag%3DAF4821280%26subid%3Dck600736mob1280s%26subparam%3D600736%26pageKey%3D%25EB%258D%25B4%25EB%25A7%2588%25ED%2581%25AC%25EB%258F%2599%25EC%259B%2590%25ED%259B%2584%25EB%25A0%2588%25EC%2589%25AC%26traceid%3DV0-703-8d36ccb10b0a143b%26requestId%3D20260828150907519246263024%26impressionId%3D20260828150907520246263027%26ctime%3D1787897347%26sig%3D03cc4d127c&click_type=1&origin_bridge=1&click_id=600736 |
| LOW | MEDIUM | 0.8599 | https://www.4nb.co.kr/v2/products/jtm.php?dcode=101&gad_source=5&gad_campaignid=23202951114&gclid=CjwKCAjw48TUBhBREiwAK0GnQX5ckKF2032yWsbIrg09lg67N_dZ2LcAHC-Ql5BwYY4MZWtXOH4EcxoCzFoQAvD_BwE |
| LOW | MEDIUM | 1.2300 | https://api.haimlabs.co.kr/cou/kw/land.php?code=ck966235mob6013s&url=https%3A%2F%2Flink.coupang.com%2Fre%2FAFFSRP%3Flptag%3DAF1356013%26subid%3Dck966235mob6013s%26subparam%3D966235%26pageKey%3D%25EB%25B7%25B0%25ED%258B%25B0%26traceid%3DV0-703-3482b27ff25d8038%26requestId%3D20260828193907087296650833%26impressionId%3D20260828193907088296650852%26ctime%3D1787913547%26sig%3D034f3726cf&click_type=1&origin_bridge=1&click_id=966235 |
| LOW | UNKNOWN | 0.4904 | https://story.s-oil.com |
| MEDIUM | HIGH | 1.1506 | https://promo-login.test/member/event |
| MEDIUM | HIGH | 1.1568 | https://event-page.test/auth |
| MEDIUM | LOW | 1.2349 | https://pj.findmyfamily.co.kr/mkt05/google_21/17b3ddcd831a4992/0?gad_source=5&gad_campaignid=24167488278&gclid=CjwKCAjw48TUBhBREiwAK0GnQQOw75qYSmaCwu8F-_bjD3DS2APBgCPxpALNi6y7dm0vVAam4znXhhoCDQkQAvD_BwE |
| MEDIUM | UNKNOWN | 0.7192 | https://knownow.co.in/article/navigating-the-world-of-industrial-equipment-suppliers-key-considerations?lid=1147a788f2af27eafce95f661bdae260&utm_term=m.inews24.com&utm_campaign=24108629691&gad_source=5&gad_campaignid=24108629691&gclid=CjwKCAjw48TUBhBREiwAK0GnQXF87BQ9a4NRsHuVMCITbw5j7B7hg8MvddxsgkVveuZ6XtpWDC2AXBoCj74QAvD_BwE |
| MEDIUM | LOW | 0.3838 | https://www.chanel.com |
| MEDIUM | UNKNOWN | 0.4328 | https://kcar.com/bc/detail/CarInfoDtl?i_sCarCd=EC61381113&utm_source=criteo&utm_medium=display&utm_campaign=buy_web_microcoversion_interest_all_LAL&utm_content=display&utm_id |
| MEDIUM | LOW | 0.6371 | https://todayon.blue-dream-2722.workers.dev/a/0tovalxizio3c4/?click_id=%5BCLICK_ID%5D&utm_source=dable&utm_medium=cpc&utm_campaign=20260828_글로벌대박&utm_content=미국+품절대란+%27ㅇㅇ다이어 |
| MEDIUM | UNKNOWN | 0.4825 | https://gwangju.co.kr |
| MEDIUM | UNKNOWN | 0.6080 | https://adapi.feeldmc.com/ad/p/in/v1_0/clk?slotid=slt_kbfgbank_555&adgroupid=agr_jhok_20260730_2&adid=adc_jhok_20260730_2 |
| MEDIUM | UNKNOWN | 0.7999 | https://s.adscope.co.kr/adt_connect.php?u=aHR0cDovL3Nha3VuemEuY29tL25ld3NtL2luZGV4X2JuLmh0bWw/cGlkPTU3Jm1jaT0xMV8yNF8xXzkxNg==&v=&d= |
| MEDIUM | UNKNOWN | 0.3545 | https://suickon.com/trial/?utm=blogM |
| MEDIUM | LOW | 0.5334 | https://reople.ad4989.co.kr/sttop/2/?pid=roo |
| MEDIUM | LOW | 1.2162 | https://m.g-school.co.kr/customer/view.jsp?board_cate_code=GBC4&board_num=54764&keyField=&keyWord=&nowPage=1&utm_source=google&utm_medium=banner&utm_campaign=mock_exam&gad_source=5&gad_campaignid=24181996315&gclid=CjwKCAjw48TUBhBREiwAK0GnQTnXig4jwzNEGuqyPu09wJcwJlJYy3ijo0Nwk9mz48d_8kD_xaqtrxoCvAgQAvD_BwE |
| MEDIUM | LOW | 0.3539 | https://ppomppu.co.kr/error/error.php?e=404 |
| MEDIUM | UNKNOWN | 0.4411 | https://zoomoneyplus.com/shop/item.php?it_id=1783422179&smdi_grp=MTEwfDExMDZ8MjExfDQ3Mw%3D%3D |
| MEDIUM | UNKNOWN | 0.5387 | https://z00.kr |
| MEDIUM | UNKNOWN | 0.4763 | https://sameagedates.com/land/sp/ce929c24/?partner=adw_sameagedates_ua_localization_korean&campaignid=24094282011&gclid=EAIaIQobChMIj9X10qTDlgMVP1zCBR29uDO_EAEYASAAEgLZuvD_Bw |
| MEDIUM | UNKNOWN | 0.7812 | https://ads.mncmedia.co.kr/mad/LINKSUM/mncNpage.html?bpage=y&s2=bt&url=http%3A%2F%2Freople.ad4989.co.kr%2Fsttop%2F2%2F%3Fpid%3Droo&n1=45 |
| MEDIUM | UNKNOWN | 0.3715 | https://sakunza.com/m4/index.html?pid=57 |
| MEDIUM | LOW | 0.7348 | https://ap.srv.stackadapt.com/cookie?campid=3264273&is_ctv=0&nativeid=14797261&domain=zdnet.co.kr%3A%3A390&auctionid=1-2753-178791667842756778125138-4&impindex=0&m=MzkuMTE1LjE5MC4zOQ&isipgen=0&conv=1 |
| MEDIUM | UNKNOWN | 0.5588 | https://happydietblog.kr/XqcmeAnuYlkGyh3NK5/?utm_source=dable&utm_medium=cpc&utm_campaign=0622_베르린정&utm_content=%5B사이트%5D+"마운X로+필요없어요"+-28kg+감량+성공한+예신의+고백&ecid=n71SuGd4c_ZkLmSO5QDilH0c0_83KmP4U1o9R9kFUJw&request_id=4f880dfcc60209bdd84f267795bef548 |
| HIGH | MEDIUM | 1.2288 | https://reward-bank-card.test/claim |
| UNKNOWN | MEDIUM | 0.8386 | https://event-summary.test/report |

## Confusion Matrix

| Actual \ Predicted | LOW | MEDIUM | HIGH | UNKNOWN |
| :-- | --: | --: | --: | --: |
| LOW | 33 | 5 | 0 | 7 |
| MEDIUM | 7 | 22 | 2 | 12 |
| HIGH | 0 | 1 | 19 | 0 |
| UNKNOWN | 0 | 1 | 0 | 19 |

## Predictions

| Actual | Predicted | Distance | URL |
| :-- | :-- | --: | :-- |
| LOW | LOW | 1.0606 | https://support.example.com/account/recovery |
| LOW | LOW | 0.9147 | https://www.samsung.com |
| LOW | LOW | 0.3776 | https://docs.github.com |
| LOW | LOW | 0.3899 | https://developer.android.com |
| LOW | LOW | 1.0848 | https://verified-support.test/faq |
| LOW | LOW | 1.0509 | https://status.safe-example.test/incidents |
| LOW | LOW | 0.4832 | https://example.com/news/release-notes |
| LOW | LOW | 0.4859 | https://example.com/blog/engineering |
| LOW | LOW | 1.1014 | https://example.com/account/security-tips |
| LOW | LOW | 0.4841 | https://example.com/support/status |
| LOW | LOW | 0.3780 | https://support.example.com/news/release-notes |
| LOW | LOW | 0.3830 | https://support.example.com/blog/engineering |
| LOW | LOW | 1.0598 | https://support.example.com/account/security-tips |
| LOW | LOW | 0.3802 | https://support.example.com/support/status |
| LOW | LOW | 0.3688 | https://docs.example.com/news/release-notes |
| LOW | LOW | 0.3711 | https://docs.example.com/blog/engineering |
| LOW | LOW | 1.0560 | https://docs.example.com/account/security-tips |
| LOW | LOW | 0.3696 | https://docs.example.com/support/status |
| LOW | LOW | 0.3688 | https://news.example.com/news/release-notes |
| LOW | LOW | 0.3711 | https://news.example.com/blog/engineering |
| LOW | UNKNOWN | 0.4890 | https://amc.seoul.kr/asan/healthinfo/management/managementDetail.do?managementId=365 |
| LOW | UNKNOWN | 0.3460 | https://shinsegaegroupnewsroom.com/?utm_source=mt&utm_medium=banner&utm_campaign=main |
| LOW | LOW | 0.4787 | https://stackadapt.com |
| LOW | UNKNOWN | 0.5078 | https://m.inews24.com |
| LOW | UNKNOWN | 1.1145 | https://carrotins.com/mobile/calculation/car/personal/intro/?utm_campaign=car&utm_source=criteo&utm_medium=display&utm_content=adaptive_181850&utm_term=cv_go&afccd=PA01230&afcDtcd=cv_go&afcDtFlgcd=adaptive_181850&main=y&utm_id=706693&cto_pld=j1iwW8r2AQCSPXa-On52LQ |
| LOW | LOW | 0.4829 | https://ouraring.com |
| LOW | LOW | 0.3519 | https://link.coupang.com/a/ch0NuG |
| LOW | MEDIUM | 1.1520 | https://estore.wacom.kr/ko-kr/pen-tablet/wacomintuospro2025/wacom-intuos-pro-medium-ptk670k0c.html?utm_source=google&utm_medium=banner&utm_campaign=2607_gdn&utm_content=Intuospro&gad_source=5&gad_campaignid=24026560685&gclid=CjwKCAjw48TUBhBREiwAK0GnQQFOAVrdrR_tJ2Jt1cbaOcMAyvF5Wrxd1gf966buUaa-DuBR5nXSRhoC7RUQAvD_BwE |
| LOW | MEDIUM | 1.1685 | https://www.sktair.com/plan?ch=AIR_WEB_6&utm_source=google_da&utm_medium=none&utm_campaign=plan&utm_term=underkg&utm_content=air_air-plan_conversion__google_da__20260824__underkg&gad_source=5&gad_campaignid=24161740742&gclid=CjwKCAjw48TUBhBREiwAK0GnQQaNOSmp2q2L2onYxpGlIjEZighRuBzDssyU8v9zHhKFVaw6844uYRoChE0QAvD_BwE |
| LOW | LOW | 0.7365 | https://krrtb.c.appier.net/xclk?bidobjid=4ArG_sN1BfOqZaSgskaRag&cid=Y2vYjZTHSb61-qv9EzD7Wg&crid=XWyAVHabRL-HoMqr4d-pVw&crpid=u4OPu7-TRsig1EGse5SrmA&soid=1R64&partner_id=03mSf3HNecb-&bx=Cylxwnu_oqdDKqosoqwP20uR70wy20f-uY79uqa1cbS9cnzqUyzFZ1u-uYS9uqa1cbS9cnzqUyzFZ1u-uYgWJ4u_onx1wHTRuqayK4lBZyu_uYS93LlBc8M1K4lWUyu_uqGmu1x1UD7muqa1orw9o4QxKqd1eM&ui=CylYc8Myuqa1oPf9orGsKqGzo4Qx2qaV71lO&consent=1&ddhh=oq6Wo6&flanding_rdr= |
| LOW | UNKNOWN | 0.8000 | https://m.knnews.co.kr/mView.php?idxno=1549627 |
| LOW | LOW | 0.4792 | https://doubleclick.net |
| LOW | LOW | 0.3930 | https://m.slrclub.com |
| LOW | LOW | 0.3741 | https://search.daum.net/search?q=실비보험 |
| LOW | LOW | 0.5993 | https://m.news.nate.com/?sq=1188651&keyB=1 |
| LOW | UNKNOWN | 0.4722 | https://m.joongboo.com |
| LOW | LOW | 0.5213 | https://nate.com |
| LOW | LOW | 0.5907 | https://m.hani.co.kr |
| LOW | MEDIUM | 1.3227 | https://api.haimlabs.co.kr/cou/kw/land.php?code=ck600736mob1280s&url=https%3A%2F%2Flink.coupang.com%2Fre%2FAFFSRP%3Flptag%3DAF4821280%26subid%3Dck600736mob1280s%26subparam%3D600736%26pageKey%3D%25EB%258D%25B4%25EB%25A7%2588%25ED%2581%25AC%25EB%258F%2599%25EC%259B%2590%25ED%259B%2584%25EB%25A0%2588%25EC%2589%25AC%26traceid%3DV0-703-8d36ccb10b0a143b%26requestId%3D20260828150907519246263024%26impressionId%3D20260828150907520246263027%26ctime%3D1787897347%26sig%3D03cc4d127c&click_type=1&origin_bridge=1&click_id=600736 |
| LOW | MEDIUM | 0.8599 | https://www.4nb.co.kr/v2/products/jtm.php?dcode=101&gad_source=5&gad_campaignid=23202951114&gclid=CjwKCAjw48TUBhBREiwAK0GnQX5ckKF2032yWsbIrg09lg67N_dZ2LcAHC-Ql5BwYY4MZWtXOH4EcxoCzFoQAvD_BwE |
| LOW | LOW | 1.9884 | https://siape.veta.naver.com/fxclick?eu=EU10042238&calp=-&oj=IpQuyVrNR7R%2Fs0x2Ji1I7DfqcFCNKZX4nQrwKeV%2FW9QBPFlEZiMYeTCgJd%2BT6nvIOKHqtIHMEb1ejcMNTCgTvkynnV6e4poTr%2Fpgr8ytPCevbz%2Fbocq2ywvru6D5uUpuiCOF6Ehhrrh91fbQyY%2FdzowT%2FWBHVGxO0Ucr%2FKVw29Wdt1gGgB%2Fu1lRb38EDOBx%2BsEqu878hRNZwA3n7ivVQCkE6003qVP21Il558hVaKU3LEfdT0%2FMrhu6efKvemoJr53tgCcGYolthrVB%2Bhnm%2BZDdBJPj329psfdX20MmP3c7ogo0mEP6UYS2P%2FCfFVBz4fEVgw4QMPpj77pa4xD%2FBxZwMFbzZR%2BXEYEdBZdXOWHRCLYSHKqTi7dfE0LlTUUx85E%2FjrPYU5FXUem3D7x9rWbGJJRscmyiLuuOoisLfQO6B%2FUcACNGlIe1sTItBPjss1TwgRTjwZFroV0L%2BxLvqCv%2BJq8IRYql49RHASs454uBJxfbG2XUExB1hj3TuhCx2Lx1q2Zpx05A&ac=9315834&src=8218571&br=4978606&evtcd=P901&x_ti=1324&tb=&oid=&sid1=&sid2=&rk=R-lBNjlYsUMU7PcP1XD7bA&eltts=hKSV9g%2BjO%2Bt4XeXVkkL15g%3D%3D&brs=Y&&tr=ndptop&na=s_b07b096b3701e5&a=259F2550D3C6ABDD08B5048CD8715FEF6394A3F8664060D193DE222F674246331EAC25EEB461C9B075E88BC8C0FEA6A341E02CD847C75CCCA5258FE8CAAD51A990DA64CAE73F6B4E2580AEC2C4892914B8CA6ECA6B0E70 |
| LOW | MEDIUM | 1.2300 | https://api.haimlabs.co.kr/cou/kw/land.php?code=ck966235mob6013s&url=https%3A%2F%2Flink.coupang.com%2Fre%2FAFFSRP%3Flptag%3DAF1356013%26subid%3Dck966235mob6013s%26subparam%3D966235%26pageKey%3D%25EB%25B7%25B0%25ED%258B%25B0%26traceid%3DV0-703-3482b27ff25d8038%26requestId%3D20260828193907087296650833%26impressionId%3D20260828193907088296650852%26ctime%3D1787913547%26sig%3D034f3726cf&click_type=1&origin_bridge=1&click_id=966235 |
| LOW | LOW | 0.4763 | https://open.spotify.com/track/5SCyHMxB5SWZP4EAKI5YRu?gad_source=5&gad_campaignid=24167869635&gclid=CjwKCAjw48TUBhBREiwAK0GnQZ-7Cpk3mbt2euMFWdsRXrVYxcEQ1i5pH6uD9K3f2M2IXERMS0wkSxoCD1MQAvD_BwE |
| LOW | LOW | 0.5732 | https://m.seoul.co.kr |
| LOW | UNKNOWN | 0.4904 | https://story.s-oil.com |
| MEDIUM | HIGH | 1.1506 | https://promo-login.test/member/event |
| MEDIUM | MEDIUM | 0.8440 | https://event-coupon-box.test/receive |
| MEDIUM | HIGH | 1.1568 | https://event-page.test/auth |
| MEDIUM | MEDIUM | 0.8367 | https://mobile-coupon.test/apply?ref=redacted |
| MEDIUM | MEDIUM | 0.8388 | https://voucher-event.test/claim |
| MEDIUM | MEDIUM | 1.2756 | https://promo-center.test/win |
| MEDIUM | MEDIUM | 0.8380 | https://reward-center.xyz/promo/claim |
| MEDIUM | MEDIUM | 0.8384 | https://reward-center.test/coupon/redeem |
| MEDIUM | MEDIUM | 0.8378 | https://reward-center.top/promotion/enter |
| MEDIUM | MEDIUM | 0.8376 | https://reward-center.xyz/reward/status |
| MEDIUM | MEDIUM | 0.8419 | https://promo-hub.test/event/join |
| MEDIUM | MEDIUM | 0.8429 | https://promo-hub.top/promo/claim |
| MEDIUM | MEDIUM | 0.8405 | https://promo-hub.xyz/coupon/redeem |
| MEDIUM | MEDIUM | 0.8365 | https://promo-hub.test/event/details?id=redacted |
| MEDIUM | MEDIUM | 0.8405 | https://promo-hub.top/reward/status |
| MEDIUM | MEDIUM | 0.8385 | https://event-gateway.xyz/event/join |
| MEDIUM | MEDIUM | 0.8367 | https://event-gateway.test/reward/apply?ref=redacted |
| MEDIUM | MEDIUM | 0.8376 | https://event-gateway.top/coupon/redeem |
| MEDIUM | MEDIUM | 0.8359 | https://event-gateway.xyz/event/details?id=redacted |
| MEDIUM | MEDIUM | 0.8397 | https://event-gateway.test/promo/eligibility |
| MEDIUM | LOW | 1.2349 | https://pj.findmyfamily.co.kr/mkt05/google_21/17b3ddcd831a4992/0?gad_source=5&gad_campaignid=24167488278&gclid=CjwKCAjw48TUBhBREiwAK0GnQQOw75qYSmaCwu8F-_bjD3DS2APBgCPxpALNi6y7dm0vVAam4znXhhoCDQkQAvD_BwE |
| MEDIUM | UNKNOWN | 0.7192 | https://knownow.co.in/article/navigating-the-world-of-industrial-equipment-suppliers-key-considerations?lid=1147a788f2af27eafce95f661bdae260&utm_term=m.inews24.com&utm_campaign=24108629691&gad_source=5&gad_campaignid=24108629691&gclid=CjwKCAjw48TUBhBREiwAK0GnQXF87BQ9a4NRsHuVMCITbw5j7B7hg8MvddxsgkVveuZ6XtpWDC2AXBoCj74QAvD_BwE |
| MEDIUM | LOW | 0.3838 | https://www.chanel.com |
| MEDIUM | MEDIUM | 1.1224 | https://lawwin.co.kr/seoul?utm_source=naver&utm_medium=gfa&utm_campaign=retargeting&utm_content=smartchannel&NaPm=ci%3D8sIBYoLYY1Q4SxQbVSl6tqLA%7Ctr%3Dgfa%7Cct%3Dmtcwsk12%7Ch |
| MEDIUM | UNKNOWN | 0.4328 | https://kcar.com/bc/detail/CarInfoDtl?i_sCarCd=EC61381113&utm_source=criteo&utm_medium=display&utm_campaign=buy_web_microcoversion_interest_all_LAL&utm_content=display&utm_id |
| MEDIUM | LOW | 0.6371 | https://todayon.blue-dream-2722.workers.dev/a/0tovalxizio3c4/?click_id=%5BCLICK_ID%5D&utm_source=dable&utm_medium=cpc&utm_campaign=20260828_글로벌대박&utm_content=미국+품절대란+%27ㅇㅇ다이어 |
| MEDIUM | UNKNOWN | 0.4825 | https://gwangju.co.kr |
| MEDIUM | MEDIUM | 1.2260 | https://kor.wwiqtest.com/start-test/?pt=gender&utm_source=google&utm_medium=cpc&utm_campaign=23941374220&utm_adset=197353523213&utm_term=&utm_ad=812700176797&cq_src=google_ads&cq_cmp=23941374220&cq_con=197353523213&cq_term=&cq_med=&cq_plac=www.mk.co.kr&cq_net=d&cq_plt=gp&gad_source=5&gad_campaignid=23941374220&gclid=EAIaIQobChMI34nL5pDDlgMVtoXpBR0digKqEAEYASAAEgLF4fD_BwE |
| MEDIUM | UNKNOWN | 0.6080 | https://adapi.feeldmc.com/ad/p/in/v1_0/clk?slotid=slt_kbfgbank_555&adgroupid=agr_jhok_20260730_2&adid=adc_jhok_20260730_2 |
| MEDIUM | UNKNOWN | 0.7999 | https://s.adscope.co.kr/adt_connect.php?u=aHR0cDovL3Nha3VuemEuY29tL25ld3NtL2luZGV4X2JuLmh0bWw/cGlkPTU3Jm1jaT0xMV8yNF8xXzkxNg==&v=&d= |
| MEDIUM | UNKNOWN | 0.3545 | https://suickon.com/trial/?utm=blogM |
| MEDIUM | LOW | 0.5334 | https://reople.ad4989.co.kr/sttop/2/?pid=roo |
| MEDIUM | LOW | 1.2162 | https://m.g-school.co.kr/customer/view.jsp?board_cate_code=GBC4&board_num=54764&keyField=&keyWord=&nowPage=1&utm_source=google&utm_medium=banner&utm_campaign=mock_exam&gad_source=5&gad_campaignid=24181996315&gclid=CjwKCAjw48TUBhBREiwAK0GnQTnXig4jwzNEGuqyPu09wJcwJlJYy3ijo0Nwk9mz48d_8kD_xaqtrxoCvAgQAvD_BwE |
| MEDIUM | MEDIUM | 0.9542 | https://adc.imadrep.co.kr/adclick.php?b=34597316290772778709&url=https%3A%2F%2Fmy.newscover.co.kr%2Fdaehanlaw%2F%3Fref%3D459%26cc%3D3162907 |
| MEDIUM | LOW | 0.3539 | https://ppomppu.co.kr/error/error.php?e=404 |
| MEDIUM | UNKNOWN | 0.4411 | https://zoomoneyplus.com/shop/item.php?it_id=1783422179&smdi_grp=MTEwfDExMDZ8MjExfDQ3Mw%3D%3D |
| MEDIUM | UNKNOWN | 0.5387 | https://z00.kr |
| MEDIUM | MEDIUM | 1.0841 | https://news.wec.co.kr/goodrich/mo?board_id=100&site_id=15&page_cd=popin_mo_sil2_90&utm_source=popin&utm_medium=referral&utm_campaign=popin_mo_sil2_90&utm_content=새로+나온+‘실비보험’+난리난+이유%21+"보험료+확+줄이는+핵심+공개"&utm_term=&tracking_id=28123f4a03f64436caac444bac04ddf2&cc4d76fdaf5=454&a0v5la7bquf89=08d248cdbf7e60652j11b900mgsuuduc&uy3ubftvh0u6o8=28123f4a03f64436caac444bac04ddf2&cusduxj27i=5440603&xnfrr0ncac=1152&zsmoi87pih9=trace.popin.cc&lzzgnpz8d=c92aad1999f497c1a6c2ff886123e07c&Q09ORklH=2 |
| MEDIUM | UNKNOWN | 0.4763 | https://sameagedates.com/land/sp/ce929c24/?partner=adw_sameagedates_ua_localization_korean&campaignid=24094282011&gclid=EAIaIQobChMIj9X10qTDlgMVP1zCBR29uDO_EAEYASAAEgLZuvD_Bw |
| MEDIUM | UNKNOWN | 0.7812 | https://ads.mncmedia.co.kr/mad/LINKSUM/mncNpage.html?bpage=y&s2=bt&url=http%3A%2F%2Freople.ad4989.co.kr%2Fsttop%2F2%2F%3Fpid%3Droo&n1=45 |
| MEDIUM | UNKNOWN | 0.3715 | https://sakunza.com/m4/index.html?pid=57 |
| MEDIUM | LOW | 0.7348 | https://ap.srv.stackadapt.com/cookie?campid=3264273&is_ctv=0&nativeid=14797261&domain=zdnet.co.kr%3A%3A390&auctionid=1-2753-178791667842756778125138-4&impindex=0&m=MzkuMTE1LjE5MC4zOQ&isipgen=0&conv=1 |
| MEDIUM | UNKNOWN | 0.5588 | https://happydietblog.kr/XqcmeAnuYlkGyh3NK5/?utm_source=dable&utm_medium=cpc&utm_campaign=0622_베르린정&utm_content=%5B사이트%5D+"마운X로+필요없어요"+-28kg+감량+성공한+예신의+고백&ecid=n71SuGd4c_ZkLmSO5QDilH0c0_83KmP4U1o9R9kFUJw&request_id=4f880dfcc60209bdd84f267795bef548 |
| HIGH | HIGH | 0.7946 | https://kakao-login-security.test/verify |
| HIGH | HIGH | 0.7948 | https://secure-kakao-login.test/account |
| HIGH | HIGH | 0.7949 | https://toss-card-security.test/verify |
| HIGH | HIGH | 1.0140 | https://viewer-plugin.test/download/plugin.apk |
| HIGH | HIGH | 0.6098 | https://identity-check-form.test/verify |
| HIGH | MEDIUM | 1.2288 | https://reward-bank-card.test/claim |
| HIGH | HIGH | 0.7999 | https://bank-auth-center.test/auth |
| HIGH | HIGH | 0.7957 | https://google-account-verify.test/account |
| HIGH | HIGH | 0.7945 | https://google-signin-alert.test/signin |
| HIGH | HIGH | 0.7970 | https://kakao-identity-check.test/verification |
| HIGH | HIGH | 1.0071 | https://app-release-mirror.test/download/app-release.apk |
| HIGH | HIGH | 1.0073 | https://app-release-mirror.test/files/latest-build.apk |
| HIGH | HIGH | 1.0073 | https://app-release-mirror.test/get/update-package.apk |
| HIGH | HIGH | 1.0073 | https://app-release-mirror.test/install/mobile-app.apk |
| HIGH | HIGH | 1.0079 | https://app-release-mirror.test/cdn/release/app-final.apk |
| HIGH | HIGH | 0.6101 | https://card-security-check.click/verify?step=card |
| HIGH | HIGH | 0.6113 | https://card-security-check.top/signin?redirect=payment |
| HIGH | HIGH | 0.6148 | https://payment-identity-verify.test/verify?step=card |
| HIGH | HIGH | 0.6200 | https://payment-identity-verify.click/account/verify |
| HIGH | HIGH | 0.6130 | https://payment-identity-verify.top/auth/confirm |
| UNKNOWN | UNKNOWN | 0.7037 | https://mobile-guide.test/setup |
| UNKNOWN | UNKNOWN | 0.7037 | https://landing-info.test/about |
| UNKNOWN | UNKNOWN | 0.7032 | https://campaign-page.test/detail |
| UNKNOWN | MEDIUM | 0.8386 | https://event-summary.test/report |
| UNKNOWN | UNKNOWN | 0.7038 | https://product-info.test/mobile |
| UNKNOWN | UNKNOWN | 0.7108 | https://open-info.test/mobile/page |
| UNKNOWN | UNKNOWN | 0.3054 | https://daily-notes-page.app/posts/latest |
| UNKNOWN | UNKNOWN | 0.3783 | https://daily-notes-page.app/thread/12938 |
| UNKNOWN | UNKNOWN | 0.3053 | https://daily-notes-page.app/info/overview |
| UNKNOWN | UNKNOWN | 0.3053 | https://daily-notes-page.app/article/today |
| UNKNOWN | UNKNOWN | 0.3054 | https://daily-notes-page.app/guide/basics |
| UNKNOWN | UNKNOWN | 0.3059 | https://daily-notes-page.app/page/detail |
| UNKNOWN | UNKNOWN | 0.3080 | https://daily-notes-page.app/notes/entry-42 |
| UNKNOWN | UNKNOWN | 0.3054 | https://daily-notes-page.app/gallery/view |
| UNKNOWN | UNKNOWN | 0.3466 | https://daily-notes-page.app/archive/2026 |
| UNKNOWN | UNKNOWN | 0.3053 | https://daily-notes-page.app/journal/entry |
| UNKNOWN | UNKNOWN | 0.3097 | https://mobile-setup-guide.app/posts/latest |
| UNKNOWN | UNKNOWN | 0.3739 | https://mobile-setup-guide.app/thread/12938 |
| UNKNOWN | UNKNOWN | 0.3104 | https://mobile-setup-guide.app/info/overview |
| UNKNOWN | UNKNOWN | 0.3104 | https://mobile-setup-guide.app/article/today |
