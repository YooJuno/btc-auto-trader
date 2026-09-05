# Strategy Research Loop (워크포워드 검증)

> **`strategy_lab_daemon.py` 는 더 이상 사용하지 않습니다.**
>
> 그 데몬은 7일 창을 매시간 재최적화하면서 `--min-sell-trades 1`(거래 1건짜리 결과 채택)로 합의값을
> 만들었습니다. 연구가 아니라 **노이즈를 학습하는 기계**였고, 익절 기본값을 2.4% → 1.92% → 1.44%로
> 깎아온 원인입니다. 그게 감시 없이 실제 돈에 대해 돌고 있었습니다.
>
> 대체: `scripts/research/strategy_research_loop.py`

## 무엇이 달라졌나

| | 이전 | 현재 |
|---|---|---|
| 검증 | train/test 1회 분할 | **앵커드 워크포워드 18폴드** (검증 구간 비중첩) |
| 다중검정 | 없음 | **Deflated Sharpe** — 시도 조합이 많을수록 기준 상승 |
| 최소 표본 | 거래 1건 | 폴드 6개 + OOS 거래 30건 |
| 일관성 | 없음 | 양수 폴드 60% 이상 |
| 현행 대비 | 없음 | +0.5%p 이상 개선 필수 |
| 주기 | 매시간 | 하루 1회 |
| 기본 판정 | 채택 | **거부** |
| 자동 적용 | 사람이 반영 | `champion.json` 기록만, 설정 파일 미변경 |

## 실행

```bash
# 1회, 리포트만
python3 scripts/research/strategy_research_loop.py --once

# 무인 (systemd)
./scripts/systemd/install_services.sh --strategy-only
```

산출물은 `data/strategy-research/` 에 쌓입니다:
- `latest.json` — 최근 1회 상세
- `history.jsonl` — append-only 이력 (무엇을 시도했고 왜 거부됐는지)
- `champion.json` — `--auto-promote` 사용 시 통과 후보 (적용은 수동)

## 거부가 정상입니다

**대부분의 실행은 REJECT를 반환합니다.** 게이트가 고장난 게 아니라 통과할 전략이 없다는 뜻입니다.

실측 예 (KRW-BTC 60m, 730일, 18폴드):

```
baseline    중앙값 +0.00%  양수폴드 50%  거래 66
challenger  중앙값 +0.00%  양수폴드 50%  거래 70  foldSharpe 0.14

VERDICT: REJECT
  [FAIL] 양수 폴드 50% (60% 필요)
  [FAIL] fold Sharpe 0.138 < 노이즈 기준선 0.463 (조합 20개 시도 기준)
  [FAIL] 현행 대비 +0.00%p (+0.50%p 필요)
```

폴드별로 최적화한 challenger가 **최적화하지 않은 baseline을 전혀 이기지 못했습니다.** 파라미터
튜닝으로 이 전략에 엣지를 만들 수 없다는 증거입니다. 예전 데몬이었다면 이 데이터에서도 파라미터를
채택했을 겁니다.

**답이 대부분 "이걸 적용하세요"인 연구 루프는 노이즈를 학습하고 있는 것입니다.**

---

