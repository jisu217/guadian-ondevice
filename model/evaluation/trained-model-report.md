# Trained Model Evaluation

Model: nearest-centroid classifier
Train samples: 536
Test samples: 132
Model accuracy: 72.73%
Model macro F1: 74.15%
Baseline accuracy on same test split: 68.94%

## Validation Protocol

- Final test metrics use a deterministic stratified holdout split.
- The exact holdout files are saved as `model/evaluation/train-split.csv` and `model/evaluation/test-split.csv`.
- Threshold calibration uses 5-fold stratified cross-validation on the training split.
- Each label gets its own certain-distance threshold instead of one shared scalar,
  so one close call for label A can no longer collapse label B's threshold too.
- Thresholds are intentionally conservative: uncertain cases should fall through to local RAG or remote fallback.

### Calibrated Certain-Distance Thresholds

| Label | Threshold |
| :-- | --: |
| LOW | 0.3450 |
| MEDIUM | 0.7499 |
| HIGH | 1.0370 |
| UNKNOWN | 0.3129 |

## Cross Validation

Average accuracy: 73.16%
Average macro F1: 74.95%

| Fold | Train | Validation | Accuracy | Macro F1 |
| :-- | --: | --: | --: | --: |
| 1 | 427 | 109 | 68.81% | 71.09% |
| 2 | 428 | 108 | 73.15% | 75.34% |
| 3 | 428 | 108 | 75.00% | 77.38% |
| 4 | 430 | 106 | 72.64% | 73.38% |
| 5 | 431 | 105 | 76.19% | 77.55% |

### Cross-Validation Label Metrics

| Label | Precision | Recall | F1 | Support |
| :-- | --: | --: | --: | --: |
| LOW | 84.43% | 74.60% | 79.21% | 189 |
| MEDIUM | 84.47% | 48.88% | 61.92% | 178 |
| HIGH | 98.80% | 95.35% | 97.04% | 86 |
| UNKNOWN | 44.81% | 98.80% | 61.65% | 83 |

## Label Distribution

| Label | Actual | Predicted |
| :-- | --: | --: |
| LOW | 47 | 43 |
| MEDIUM | 44 | 29 |
| HIGH | 21 | 22 |
| UNKNOWN | 20 | 38 |

## Precision And Recall

| Label | Precision | Recall | F1 | Support | Baseline Precision | Baseline Recall |
| :-- | --: | --: | --: | --: | --: | --: |
| LOW | 81.40% | 74.47% | 77.78% | 47 | 85.37% | 74.47% |
| MEDIUM | 75.86% | 50.00% | 60.27% | 44 | 70.97% | 50.00% |
| HIGH | 90.91% | 95.24% | 93.02% | 21 | 88.24% | 71.43% |
| UNKNOWN | 50.00% | 95.00% | 65.52% | 20 | 44.19% | 95.00% |

## Error Analysis

| Actual | Predicted | Distance | URL |
| :-- | :-- | --: | :-- |
| LOW | UNKNOWN | 0.4946 | https://amc.seoul.kr/asan/healthinfo/management/managementDetail.do?managementId=365 |
| LOW | UNKNOWN | 0.3574 | https://shinsegaegroupnewsroom.com/?utm_source=mt&utm_medium=banner&utm_campaign=main |
| LOW | UNKNOWN | 0.5137 | https://m.inews24.com |
| LOW | UNKNOWN | 1.1183 | https://carrotins.com/mobile/calculation/car/personal/intro/?utm_campaign=car&utm_source=criteo&utm_medium=display&utm_content=adaptive_181850&utm_term=cv_go&afccd=PA01230&afcDtcd=cv_go&afcDtFlgcd=adaptive_181850&main=y&utm_id=706693&cto_pld=j1iwW8r2AQCSPXa-On52LQ |
| LOW | MEDIUM | 1.1525 | https://estore.wacom.kr/ko-kr/pen-tablet/wacomintuospro2025/wacom-intuos-pro-medium-ptk670k0c.html?utm_source=google&utm_medium=banner&utm_campaign=2607_gdn&utm_content=Intuospro&gad_source=5&gad_campaignid=24026560685&gclid=CjwKCAjw48TUBhBREiwAK0GnQQFOAVrdrR_tJ2Jt1cbaOcMAyvF5Wrxd1gf966buUaa-DuBR5nXSRhoC7RUQAvD_BwE |
| LOW | MEDIUM | 1.1691 | https://www.sktair.com/plan?ch=AIR_WEB_6&utm_source=google_da&utm_medium=none&utm_campaign=plan&utm_term=underkg&utm_content=air_air-plan_conversion__google_da__20260824__underkg&gad_source=5&gad_campaignid=24161740742&gclid=CjwKCAjw48TUBhBREiwAK0GnQQaNOSmp2q2L2onYxpGlIjEZighRuBzDssyU8v9zHhKFVaw6844uYRoChE0QAvD_BwE |
| LOW | UNKNOWN | 0.8022 | https://m.knnews.co.kr/mView.php?idxno=1549627 |
| LOW | UNKNOWN | 0.4783 | https://m.joongboo.com |
| LOW | MEDIUM | 1.3232 | https://api.haimlabs.co.kr/cou/kw/land.php?code=ck600736mob1280s&url=https%3A%2F%2Flink.coupang.com%2Fre%2FAFFSRP%3Flptag%3DAF4821280%26subid%3Dck600736mob1280s%26subparam%3D600736%26pageKey%3D%25EB%258D%25B4%25EB%25A7%2588%25ED%2581%25AC%25EB%258F%2599%25EC%259B%2590%25ED%259B%2584%25EB%25A0%2588%25EC%2589%25AC%26traceid%3DV0-703-8d36ccb10b0a143b%26requestId%3D20260828150907519246263024%26impressionId%3D20260828150907520246263027%26ctime%3D1787897347%26sig%3D03cc4d127c&click_type=1&origin_bridge=1&click_id=600736 |
| LOW | MEDIUM | 0.8600 | https://www.4nb.co.kr/v2/products/jtm.php?dcode=101&gad_source=5&gad_campaignid=23202951114&gclid=CjwKCAjw48TUBhBREiwAK0GnQX5ckKF2032yWsbIrg09lg67N_dZ2LcAHC-Ql5BwYY4MZWtXOH4EcxoCzFoQAvD_BwE |
| LOW | MEDIUM | 1.2305 | https://api.haimlabs.co.kr/cou/kw/land.php?code=ck966235mob6013s&url=https%3A%2F%2Flink.coupang.com%2Fre%2FAFFSRP%3Flptag%3DAF1356013%26subid%3Dck966235mob6013s%26subparam%3D966235%26pageKey%3D%25EB%25B7%25B0%25ED%258B%25B0%26traceid%3DV0-703-3482b27ff25d8038%26requestId%3D20260828193907087296650833%26impressionId%3D20260828193907088296650852%26ctime%3D1787913547%26sig%3D034f3726cf&click_type=1&origin_bridge=1&click_id=966235 |
| LOW | UNKNOWN | 0.4964 | https://story.s-oil.com |
| MEDIUM | HIGH | 1.1398 | https://promo-login.test/member/event |
| MEDIUM | HIGH | 1.1458 | https://event-page.test/auth |
| MEDIUM | LOW | 1.2369 | https://pj.findmyfamily.co.kr/mkt05/google_21/17b3ddcd831a4992/0?gad_source=5&gad_campaignid=24167488278&gclid=CjwKCAjw48TUBhBREiwAK0GnQQOw75qYSmaCwu8F-_bjD3DS2APBgCPxpALNi6y7dm0vVAam4znXhhoCDQkQAvD_BwE |
| MEDIUM | UNKNOWN | 0.7232 | https://knownow.co.in/article/navigating-the-world-of-industrial-equipment-suppliers-key-considerations?lid=1147a788f2af27eafce95f661bdae260&utm_term=m.inews24.com&utm_campaign=24108629691&gad_source=5&gad_campaignid=24108629691&gclid=CjwKCAjw48TUBhBREiwAK0GnQXF87BQ9a4NRsHuVMCITbw5j7B7hg8MvddxsgkVveuZ6XtpWDC2AXBoCj74QAvD_BwE |
| MEDIUM | LOW | 0.3781 | https://www.chanel.com |
| MEDIUM | UNKNOWN | 0.4425 | https://kcar.com/bc/detail/CarInfoDtl?i_sCarCd=EC61381113&utm_source=criteo&utm_medium=display&utm_campaign=buy_web_microcoversion_interest_all_LAL&utm_content=display&utm_id |
| MEDIUM | LOW | 0.6348 | https://todayon.blue-dream-2722.workers.dev/a/0tovalxizio3c4/?click_id=%5BCLICK_ID%5D&utm_source=dable&utm_medium=cpc&utm_campaign=20260828_글로벌대박&utm_content=미국+품절대란+%27ㅇㅇ다이어 |
| MEDIUM | UNKNOWN | 0.4886 | https://gwangju.co.kr |
| MEDIUM | UNKNOWN | 0.6130 | https://adapi.feeldmc.com/ad/p/in/v1_0/clk?slotid=slt_kbfgbank_555&adgroupid=agr_jhok_20260730_2&adid=adc_jhok_20260730_2 |
| MEDIUM | UNKNOWN | 0.8018 | https://s.adscope.co.kr/adt_connect.php?u=aHR0cDovL3Nha3VuemEuY29tL25ld3NtL2luZGV4X2JuLmh0bWw/cGlkPTU3Jm1jaT0xMV8yNF8xXzkxNg==&v=&d= |
| MEDIUM | UNKNOWN | 0.3663 | https://suickon.com/trial/?utm=blogM |
| MEDIUM | LOW | 0.5298 | https://reople.ad4989.co.kr/sttop/2/?pid=roo |
| MEDIUM | LOW | 1.2183 | https://m.g-school.co.kr/customer/view.jsp?board_cate_code=GBC4&board_num=54764&keyField=&keyWord=&nowPage=1&utm_source=google&utm_medium=banner&utm_campaign=mock_exam&gad_source=5&gad_campaignid=24181996315&gclid=CjwKCAjw48TUBhBREiwAK0GnQTnXig4jwzNEGuqyPu09wJcwJlJYy3ijo0Nwk9mz48d_8kD_xaqtrxoCvAgQAvD_BwE |
| MEDIUM | LOW | 0.3490 | https://ppomppu.co.kr/error/error.php?e=404 |
| MEDIUM | UNKNOWN | 0.4506 | https://zoomoneyplus.com/shop/item.php?it_id=1783422179&smdi_grp=MTEwfDExMDZ8MjExfDQ3Mw%3D%3D |
| MEDIUM | UNKNOWN | 0.5472 | https://z00.kr |
| MEDIUM | UNKNOWN | 0.4849 | https://sameagedates.com/land/sp/ce929c24/?partner=adw_sameagedates_ua_localization_korean&campaignid=24094282011&gclid=EAIaIQobChMIj9X10qTDlgMVP1zCBR29uDO_EAEYASAAEgLZuvD_Bw |
| MEDIUM | UNKNOWN | 0.7833 | https://ads.mncmedia.co.kr/mad/LINKSUM/mncNpage.html?bpage=y&s2=bt&url=http%3A%2F%2Freople.ad4989.co.kr%2Fsttop%2F2%2F%3Fpid%3Droo&n1=45 |
| MEDIUM | UNKNOWN | 0.3830 | https://sakunza.com/m4/index.html?pid=57 |
| MEDIUM | LOW | 0.7346 | https://ap.srv.stackadapt.com/cookie?campid=3264273&is_ctv=0&nativeid=14797261&domain=zdnet.co.kr%3A%3A390&auctionid=1-2753-178791667842756778125138-4&impindex=0&m=MzkuMTE1LjE5MC4zOQ&isipgen=0&conv=1 |
| MEDIUM | UNKNOWN | 0.5664 | https://happydietblog.kr/XqcmeAnuYlkGyh3NK5/?utm_source=dable&utm_medium=cpc&utm_campaign=0622_베르린정&utm_content=%5B사이트%5D+"마운X로+필요없어요"+-28kg+감량+성공한+예신의+고백&ecid=n71SuGd4c_ZkLmSO5QDilH0c0_83KmP4U1o9R9kFUJw&request_id=4f880dfcc60209bdd84f267795bef548 |
| MEDIUM | LOW | 1.1014 | https://bit.ly/3AbCdEf |
| HIGH | MEDIUM | 1.2318 | https://reward-bank-card.test/claim |
| UNKNOWN | MEDIUM | 0.8416 | https://event-summary.test/report |

## Confusion Matrix

| Actual \ Predicted | LOW | MEDIUM | HIGH | UNKNOWN |
| :-- | --: | --: | --: | --: |
| LOW | 35 | 5 | 0 | 7 |
| MEDIUM | 8 | 22 | 2 | 12 |
| HIGH | 0 | 1 | 20 | 0 |
| UNKNOWN | 0 | 1 | 0 | 19 |

## Predictions

| Actual | Predicted | Distance | URL |
| :-- | :-- | --: | :-- |
| LOW | LOW | 1.0489 | https://support.example.com/account/recovery |
| LOW | LOW | 0.9060 | https://www.samsung.com |
| LOW | LOW | 0.3719 | https://docs.github.com |
| LOW | LOW | 0.3845 | https://developer.android.com |
| LOW | LOW | 1.0841 | https://verified-support.test/faq |
| LOW | LOW | 1.0499 | https://status.safe-example.test/incidents |
| LOW | LOW | 0.4797 | https://example.com/news/release-notes |
| LOW | LOW | 0.4824 | https://example.com/blog/engineering |
| LOW | LOW | 1.0905 | https://example.com/account/security-tips |
| LOW | LOW | 0.4806 | https://example.com/support/status |
| LOW | LOW | 0.3725 | https://support.example.com/news/release-notes |
| LOW | LOW | 0.3776 | https://support.example.com/blog/engineering |
| LOW | LOW | 1.0482 | https://support.example.com/account/security-tips |
| LOW | LOW | 0.3748 | https://support.example.com/support/status |
| LOW | LOW | 0.3632 | https://docs.example.com/news/release-notes |
| LOW | LOW | 0.3656 | https://docs.example.com/blog/engineering |
| LOW | LOW | 1.0443 | https://docs.example.com/account/security-tips |
| LOW | LOW | 0.3640 | https://docs.example.com/support/status |
| LOW | LOW | 0.3632 | https://news.example.com/news/release-notes |
| LOW | LOW | 0.3656 | https://news.example.com/blog/engineering |
| LOW | UNKNOWN | 0.4946 | https://amc.seoul.kr/asan/healthinfo/management/managementDetail.do?managementId=365 |
| LOW | UNKNOWN | 0.3574 | https://shinsegaegroupnewsroom.com/?utm_source=mt&utm_medium=banner&utm_campaign=main |
| LOW | LOW | 0.4750 | https://stackadapt.com |
| LOW | UNKNOWN | 0.5137 | https://m.inews24.com |
| LOW | UNKNOWN | 1.1183 | https://carrotins.com/mobile/calculation/car/personal/intro/?utm_campaign=car&utm_source=criteo&utm_medium=display&utm_content=adaptive_181850&utm_term=cv_go&afccd=PA01230&afcDtcd=cv_go&afcDtFlgcd=adaptive_181850&main=y&utm_id=706693&cto_pld=j1iwW8r2AQCSPXa-On52LQ |
| LOW | LOW | 0.4792 | https://ouraring.com |
| LOW | LOW | 0.3463 | https://link.coupang.com/a/ch0NuG |
| LOW | MEDIUM | 1.1525 | https://estore.wacom.kr/ko-kr/pen-tablet/wacomintuospro2025/wacom-intuos-pro-medium-ptk670k0c.html?utm_source=google&utm_medium=banner&utm_campaign=2607_gdn&utm_content=Intuospro&gad_source=5&gad_campaignid=24026560685&gclid=CjwKCAjw48TUBhBREiwAK0GnQQFOAVrdrR_tJ2Jt1cbaOcMAyvF5Wrxd1gf966buUaa-DuBR5nXSRhoC7RUQAvD_BwE |
| LOW | MEDIUM | 1.1691 | https://www.sktair.com/plan?ch=AIR_WEB_6&utm_source=google_da&utm_medium=none&utm_campaign=plan&utm_term=underkg&utm_content=air_air-plan_conversion__google_da__20260824__underkg&gad_source=5&gad_campaignid=24161740742&gclid=CjwKCAjw48TUBhBREiwAK0GnQQaNOSmp2q2L2onYxpGlIjEZighRuBzDssyU8v9zHhKFVaw6844uYRoChE0QAvD_BwE |
| LOW | LOW | 0.7368 | https://krrtb.c.appier.net/xclk?bidobjid=4ArG_sN1BfOqZaSgskaRag&cid=Y2vYjZTHSb61-qv9EzD7Wg&crid=XWyAVHabRL-HoMqr4d-pVw&crpid=u4OPu7-TRsig1EGse5SrmA&soid=1R64&partner_id=03mSf3HNecb-&bx=Cylxwnu_oqdDKqosoqwP20uR70wy20f-uY79uqa1cbS9cnzqUyzFZ1u-uYS9uqa1cbS9cnzqUyzFZ1u-uYgWJ4u_onx1wHTRuqayK4lBZyu_uYS93LlBc8M1K4lWUyu_uqGmu1x1UD7muqa1orw9o4QxKqd1eM&ui=CylYc8Myuqa1oPf9orGsKqGzo4Qx2qaV71lO&consent=1&ddhh=oq6Wo6&flanding_rdr= |
| LOW | UNKNOWN | 0.8022 | https://m.knnews.co.kr/mView.php?idxno=1549627 |
| LOW | LOW | 0.4756 | https://doubleclick.net |
| LOW | LOW | 0.3874 | https://m.slrclub.com |
| LOW | LOW | 0.3684 | https://search.daum.net/search?q=실비보험 |
| LOW | LOW | 0.5969 | https://m.news.nate.com/?sq=1188651&keyB=1 |
| LOW | UNKNOWN | 0.4783 | https://m.joongboo.com |
| LOW | LOW | 0.5178 | https://nate.com |
| LOW | LOW | 0.5863 | https://m.hani.co.kr |
| LOW | MEDIUM | 1.3232 | https://api.haimlabs.co.kr/cou/kw/land.php?code=ck600736mob1280s&url=https%3A%2F%2Flink.coupang.com%2Fre%2FAFFSRP%3Flptag%3DAF4821280%26subid%3Dck600736mob1280s%26subparam%3D600736%26pageKey%3D%25EB%258D%25B4%25EB%25A7%2588%25ED%2581%25AC%25EB%258F%2599%25EC%259B%2590%25ED%259B%2584%25EB%25A0%2588%25EC%2589%25AC%26traceid%3DV0-703-8d36ccb10b0a143b%26requestId%3D20260828150907519246263024%26impressionId%3D20260828150907520246263027%26ctime%3D1787897347%26sig%3D03cc4d127c&click_type=1&origin_bridge=1&click_id=600736 |
| LOW | MEDIUM | 0.8600 | https://www.4nb.co.kr/v2/products/jtm.php?dcode=101&gad_source=5&gad_campaignid=23202951114&gclid=CjwKCAjw48TUBhBREiwAK0GnQX5ckKF2032yWsbIrg09lg67N_dZ2LcAHC-Ql5BwYY4MZWtXOH4EcxoCzFoQAvD_BwE |
| LOW | LOW | 1.9875 | https://siape.veta.naver.com/fxclick?eu=EU10042238&calp=-&oj=IpQuyVrNR7R%2Fs0x2Ji1I7DfqcFCNKZX4nQrwKeV%2FW9QBPFlEZiMYeTCgJd%2BT6nvIOKHqtIHMEb1ejcMNTCgTvkynnV6e4poTr%2Fpgr8ytPCevbz%2Fbocq2ywvru6D5uUpuiCOF6Ehhrrh91fbQyY%2FdzowT%2FWBHVGxO0Ucr%2FKVw29Wdt1gGgB%2Fu1lRb38EDOBx%2BsEqu878hRNZwA3n7ivVQCkE6003qVP21Il558hVaKU3LEfdT0%2FMrhu6efKvemoJr53tgCcGYolthrVB%2Bhnm%2BZDdBJPj329psfdX20MmP3c7ogo0mEP6UYS2P%2FCfFVBz4fEVgw4QMPpj77pa4xD%2FBxZwMFbzZR%2BXEYEdBZdXOWHRCLYSHKqTi7dfE0LlTUUx85E%2FjrPYU5FXUem3D7x9rWbGJJRscmyiLuuOoisLfQO6B%2FUcACNGlIe1sTItBPjss1TwgRTjwZFroV0L%2BxLvqCv%2BJq8IRYql49RHASs454uBJxfbG2XUExB1hj3TuhCx2Lx1q2Zpx05A&ac=9315834&src=8218571&br=4978606&evtcd=P901&x_ti=1324&tb=&oid=&sid1=&sid2=&rk=R-lBNjlYsUMU7PcP1XD7bA&eltts=hKSV9g%2BjO%2Bt4XeXVkkL15g%3D%3D&brs=Y&&tr=ndptop&na=s_b07b096b3701e5&a=259F2550D3C6ABDD08B5048CD8715FEF6394A3F8664060D193DE222F674246331EAC25EEB461C9B075E88BC8C0FEA6A341E02CD847C75CCCA5258FE8CAAD51A990DA64CAE73F6B4E2580AEC2C4892914B8CA6ECA6B0E70 |
| LOW | MEDIUM | 1.2305 | https://api.haimlabs.co.kr/cou/kw/land.php?code=ck966235mob6013s&url=https%3A%2F%2Flink.coupang.com%2Fre%2FAFFSRP%3Flptag%3DAF1356013%26subid%3Dck966235mob6013s%26subparam%3D966235%26pageKey%3D%25EB%25B7%25B0%25ED%258B%25B0%26traceid%3DV0-703-3482b27ff25d8038%26requestId%3D20260828193907087296650833%26impressionId%3D20260828193907088296650852%26ctime%3D1787913547%26sig%3D034f3726cf&click_type=1&origin_bridge=1&click_id=966235 |
| LOW | LOW | 0.4755 | https://open.spotify.com/track/5SCyHMxB5SWZP4EAKI5YRu?gad_source=5&gad_campaignid=24167869635&gclid=CjwKCAjw48TUBhBREiwAK0GnQZ-7Cpk3mbt2euMFWdsRXrVYxcEQ1i5pH6uD9K3f2M2IXERMS0wkSxoCD1MQAvD_BwE |
| LOW | LOW | 0.5687 | https://m.seoul.co.kr |
| LOW | UNKNOWN | 0.4964 | https://story.s-oil.com |
| LOW | LOW | 0.3710 | https://obank.kbstar.com/quics?page=C016511 |
| LOW | LOW | 1.4496 | https://coupon.example.com/event/free?gift=1 |
| MEDIUM | HIGH | 1.1398 | https://promo-login.test/member/event |
| MEDIUM | MEDIUM | 0.8471 | https://event-coupon-box.test/receive |
| MEDIUM | HIGH | 1.1458 | https://event-page.test/auth |
| MEDIUM | MEDIUM | 0.8398 | https://mobile-coupon.test/apply?ref=redacted |
| MEDIUM | MEDIUM | 0.8419 | https://voucher-event.test/claim |
| MEDIUM | MEDIUM | 1.2734 | https://promo-center.test/win |
| MEDIUM | MEDIUM | 0.8410 | https://reward-center.xyz/promo/claim |
| MEDIUM | MEDIUM | 0.8415 | https://reward-center.test/coupon/redeem |
| MEDIUM | MEDIUM | 0.8409 | https://reward-center.top/promotion/enter |
| MEDIUM | MEDIUM | 0.8406 | https://reward-center.xyz/reward/status |
| MEDIUM | MEDIUM | 0.8447 | https://promo-hub.test/event/join |
| MEDIUM | MEDIUM | 0.8457 | https://promo-hub.top/promo/claim |
| MEDIUM | MEDIUM | 0.8434 | https://promo-hub.xyz/coupon/redeem |
| MEDIUM | MEDIUM | 0.8394 | https://promo-hub.test/event/details?id=redacted |
| MEDIUM | MEDIUM | 0.8434 | https://promo-hub.top/reward/status |
| MEDIUM | MEDIUM | 0.8414 | https://event-gateway.xyz/event/join |
| MEDIUM | MEDIUM | 0.8398 | https://event-gateway.test/reward/apply?ref=redacted |
| MEDIUM | MEDIUM | 0.8406 | https://event-gateway.top/coupon/redeem |
| MEDIUM | MEDIUM | 0.8390 | https://event-gateway.xyz/event/details?id=redacted |
| MEDIUM | MEDIUM | 0.8430 | https://event-gateway.test/promo/eligibility |
| MEDIUM | LOW | 1.2369 | https://pj.findmyfamily.co.kr/mkt05/google_21/17b3ddcd831a4992/0?gad_source=5&gad_campaignid=24167488278&gclid=CjwKCAjw48TUBhBREiwAK0GnQQOw75qYSmaCwu8F-_bjD3DS2APBgCPxpALNi6y7dm0vVAam4znXhhoCDQkQAvD_BwE |
| MEDIUM | UNKNOWN | 0.7232 | https://knownow.co.in/article/navigating-the-world-of-industrial-equipment-suppliers-key-considerations?lid=1147a788f2af27eafce95f661bdae260&utm_term=m.inews24.com&utm_campaign=24108629691&gad_source=5&gad_campaignid=24108629691&gclid=CjwKCAjw48TUBhBREiwAK0GnQXF87BQ9a4NRsHuVMCITbw5j7B7hg8MvddxsgkVveuZ6XtpWDC2AXBoCj74QAvD_BwE |
| MEDIUM | LOW | 0.3781 | https://www.chanel.com |
| MEDIUM | MEDIUM | 1.1227 | https://lawwin.co.kr/seoul?utm_source=naver&utm_medium=gfa&utm_campaign=retargeting&utm_content=smartchannel&NaPm=ci%3D8sIBYoLYY1Q4SxQbVSl6tqLA%7Ctr%3Dgfa%7Cct%3Dmtcwsk12%7Ch |
| MEDIUM | UNKNOWN | 0.4425 | https://kcar.com/bc/detail/CarInfoDtl?i_sCarCd=EC61381113&utm_source=criteo&utm_medium=display&utm_campaign=buy_web_microcoversion_interest_all_LAL&utm_content=display&utm_id |
| MEDIUM | LOW | 0.6348 | https://todayon.blue-dream-2722.workers.dev/a/0tovalxizio3c4/?click_id=%5BCLICK_ID%5D&utm_source=dable&utm_medium=cpc&utm_campaign=20260828_글로벌대박&utm_content=미국+품절대란+%27ㅇㅇ다이어 |
| MEDIUM | UNKNOWN | 0.4886 | https://gwangju.co.kr |
| MEDIUM | MEDIUM | 1.2264 | https://kor.wwiqtest.com/start-test/?pt=gender&utm_source=google&utm_medium=cpc&utm_campaign=23941374220&utm_adset=197353523213&utm_term=&utm_ad=812700176797&cq_src=google_ads&cq_cmp=23941374220&cq_con=197353523213&cq_term=&cq_med=&cq_plac=www.mk.co.kr&cq_net=d&cq_plt=gp&gad_source=5&gad_campaignid=23941374220&gclid=EAIaIQobChMI34nL5pDDlgMVtoXpBR0digKqEAEYASAAEgLF4fD_BwE |
| MEDIUM | UNKNOWN | 0.6130 | https://adapi.feeldmc.com/ad/p/in/v1_0/clk?slotid=slt_kbfgbank_555&adgroupid=agr_jhok_20260730_2&adid=adc_jhok_20260730_2 |
| MEDIUM | UNKNOWN | 0.8018 | https://s.adscope.co.kr/adt_connect.php?u=aHR0cDovL3Nha3VuemEuY29tL25ld3NtL2luZGV4X2JuLmh0bWw/cGlkPTU3Jm1jaT0xMV8yNF8xXzkxNg==&v=&d= |
| MEDIUM | UNKNOWN | 0.3663 | https://suickon.com/trial/?utm=blogM |
| MEDIUM | LOW | 0.5298 | https://reople.ad4989.co.kr/sttop/2/?pid=roo |
| MEDIUM | LOW | 1.2183 | https://m.g-school.co.kr/customer/view.jsp?board_cate_code=GBC4&board_num=54764&keyField=&keyWord=&nowPage=1&utm_source=google&utm_medium=banner&utm_campaign=mock_exam&gad_source=5&gad_campaignid=24181996315&gclid=CjwKCAjw48TUBhBREiwAK0GnQTnXig4jwzNEGuqyPu09wJcwJlJYy3ijo0Nwk9mz48d_8kD_xaqtrxoCvAgQAvD_BwE |
| MEDIUM | MEDIUM | 0.9540 | https://adc.imadrep.co.kr/adclick.php?b=34597316290772778709&url=https%3A%2F%2Fmy.newscover.co.kr%2Fdaehanlaw%2F%3Fref%3D459%26cc%3D3162907 |
| MEDIUM | LOW | 0.3490 | https://ppomppu.co.kr/error/error.php?e=404 |
| MEDIUM | UNKNOWN | 0.4506 | https://zoomoneyplus.com/shop/item.php?it_id=1783422179&smdi_grp=MTEwfDExMDZ8MjExfDQ3Mw%3D%3D |
| MEDIUM | UNKNOWN | 0.5472 | https://z00.kr |
| MEDIUM | MEDIUM | 1.0845 | https://news.wec.co.kr/goodrich/mo?board_id=100&site_id=15&page_cd=popin_mo_sil2_90&utm_source=popin&utm_medium=referral&utm_campaign=popin_mo_sil2_90&utm_content=새로+나온+‘실비보험’+난리난+이유%21+"보험료+확+줄이는+핵심+공개"&utm_term=&tracking_id=28123f4a03f64436caac444bac04ddf2&cc4d76fdaf5=454&a0v5la7bquf89=08d248cdbf7e60652j11b900mgsuuduc&uy3ubftvh0u6o8=28123f4a03f64436caac444bac04ddf2&cusduxj27i=5440603&xnfrr0ncac=1152&zsmoi87pih9=trace.popin.cc&lzzgnpz8d=c92aad1999f497c1a6c2ff886123e07c&Q09ORklH=2 |
| MEDIUM | UNKNOWN | 0.4849 | https://sameagedates.com/land/sp/ce929c24/?partner=adw_sameagedates_ua_localization_korean&campaignid=24094282011&gclid=EAIaIQobChMIj9X10qTDlgMVP1zCBR29uDO_EAEYASAAEgLZuvD_Bw |
| MEDIUM | UNKNOWN | 0.7833 | https://ads.mncmedia.co.kr/mad/LINKSUM/mncNpage.html?bpage=y&s2=bt&url=http%3A%2F%2Freople.ad4989.co.kr%2Fsttop%2F2%2F%3Fpid%3Droo&n1=45 |
| MEDIUM | UNKNOWN | 0.3830 | https://sakunza.com/m4/index.html?pid=57 |
| MEDIUM | LOW | 0.7346 | https://ap.srv.stackadapt.com/cookie?campid=3264273&is_ctv=0&nativeid=14797261&domain=zdnet.co.kr%3A%3A390&auctionid=1-2753-178791667842756778125138-4&impindex=0&m=MzkuMTE1LjE5MC4zOQ&isipgen=0&conv=1 |
| MEDIUM | UNKNOWN | 0.5664 | https://happydietblog.kr/XqcmeAnuYlkGyh3NK5/?utm_source=dable&utm_medium=cpc&utm_campaign=0622_베르린정&utm_content=%5B사이트%5D+"마운X로+필요없어요"+-28kg+감량+성공한+예신의+고백&ecid=n71SuGd4c_ZkLmSO5QDilH0c0_83KmP4U1o9R9kFUJw&request_id=4f880dfcc60209bdd84f267795bef548 |
| MEDIUM | LOW | 1.1014 | https://bit.ly/3AbCdEf |
| HIGH | HIGH | 0.8050 | https://kakao-login-security.test/verify |
| HIGH | HIGH | 0.8047 | https://secure-kakao-login.test/account |
| HIGH | HIGH | 0.8049 | https://toss-card-security.test/verify |
| HIGH | HIGH | 1.0159 | https://viewer-plugin.test/download/plugin.apk |
| HIGH | HIGH | 0.5875 | https://identity-check-form.test/verify |
| HIGH | MEDIUM | 1.2318 | https://reward-bank-card.test/claim |
| HIGH | HIGH | 0.8094 | https://bank-auth-center.test/auth |
| HIGH | HIGH | 0.8063 | https://google-account-verify.test/account |
| HIGH | HIGH | 0.8046 | https://google-signin-alert.test/signin |
| HIGH | HIGH | 0.8074 | https://kakao-identity-check.test/verification |
| HIGH | HIGH | 1.0098 | https://app-release-mirror.test/download/app-release.apk |
| HIGH | HIGH | 1.0100 | https://app-release-mirror.test/files/latest-build.apk |
| HIGH | HIGH | 1.0100 | https://app-release-mirror.test/get/update-package.apk |
| HIGH | HIGH | 1.0100 | https://app-release-mirror.test/install/mobile-app.apk |
| HIGH | HIGH | 1.0105 | https://app-release-mirror.test/cdn/release/app-final.apk |
| HIGH | HIGH | 0.5880 | https://card-security-check.click/verify?step=card |
| HIGH | HIGH | 0.5888 | https://card-security-check.top/signin?redirect=payment |
| HIGH | HIGH | 0.5937 | https://payment-identity-verify.test/verify?step=card |
| HIGH | HIGH | 0.5993 | https://payment-identity-verify.click/account/verify |
| HIGH | HIGH | 0.5915 | https://payment-identity-verify.top/auth/confirm |
| HIGH | HIGH | 0.8118 | https://woori-bank-auth.top/account |
| UNKNOWN | UNKNOWN | 0.6907 | https://mobile-guide.test/setup |
| UNKNOWN | UNKNOWN | 0.6907 | https://landing-info.test/about |
| UNKNOWN | UNKNOWN | 0.6900 | https://campaign-page.test/detail |
| UNKNOWN | MEDIUM | 0.8416 | https://event-summary.test/report |
| UNKNOWN | UNKNOWN | 0.6907 | https://product-info.test/mobile |
| UNKNOWN | UNKNOWN | 0.6980 | https://open-info.test/mobile/page |
| UNKNOWN | UNKNOWN | 0.3186 | https://daily-notes-page.app/posts/latest |
| UNKNOWN | UNKNOWN | 0.3894 | https://daily-notes-page.app/thread/12938 |
| UNKNOWN | UNKNOWN | 0.3185 | https://daily-notes-page.app/info/overview |
| UNKNOWN | UNKNOWN | 0.3185 | https://daily-notes-page.app/article/today |
| UNKNOWN | UNKNOWN | 0.3186 | https://daily-notes-page.app/guide/basics |
| UNKNOWN | UNKNOWN | 0.3191 | https://daily-notes-page.app/page/detail |
| UNKNOWN | UNKNOWN | 0.3213 | https://daily-notes-page.app/notes/entry-42 |
| UNKNOWN | UNKNOWN | 0.3186 | https://daily-notes-page.app/gallery/view |
| UNKNOWN | UNKNOWN | 0.3586 | https://daily-notes-page.app/archive/2026 |
| UNKNOWN | UNKNOWN | 0.3185 | https://daily-notes-page.app/journal/entry |
| UNKNOWN | UNKNOWN | 0.3227 | https://mobile-setup-guide.app/posts/latest |
| UNKNOWN | UNKNOWN | 0.3851 | https://mobile-setup-guide.app/thread/12938 |
| UNKNOWN | UNKNOWN | 0.3233 | https://mobile-setup-guide.app/info/overview |
| UNKNOWN | UNKNOWN | 0.3233 | https://mobile-setup-guide.app/article/today |
