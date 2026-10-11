# Known Issues and Future Work

Open items at hand-over. Each is also a candidate GitHub issue. Last reviewed against `main` on 2026-10-11.

## Open: waiting on a client decision

These are placeholder values that work today but have not been confirmed by the client.

| # | Item | Where | Why it matters | Action needed |
|---|---|---|---|---|
| 1 | Material and machine cost rates are placeholders (PLA $0.05, ABS $0.06, PETG $0.07 per gram; $0.02 per minute) | `CostCalculationService.java` | Every charge is based on unconfirmed numbers | Client confirms real pricing, then a one-line change |
| 2 | Cancellation window is 5 minutes (placeholder) | `printerfarm.job.cancellation-window-minutes` | Decides automatic refund vs. manager approval | Client confirms the policy, then change the config value |
| 3 | Starting wallet balance is $50.00 (placeholder) | `printerfarm.wallet.default-starting-balance` | Every new account is seeded with it | Client confirms the quota or top-up policy |
| 4 | Students can top up their own wallet with any amount, with no payment step | `WalletController.credit()` | The MVP risk assessment says users must not modify their own balance. This may be an intentional interim top-up | Client decides the real top-up mechanism (admin-only credit, payment or voucher), then restrict `/credit` accordingly |

## Open: integration gaps

| # | Item | Detail |
|---|---|---|
| 5 | No real printer integration | Printing is simulated by `MockPrinterDispatcher`. There is no connection to Prusa Connect / PrusaLink, so printer status, progress and camera views are not live hardware data |
| 6 | Email delivery is not configured | `JobNotificationService` sends completion and failure emails, but SMTP defaults to a placeholder (`localhost:1025`). Student addresses are derived as `<UNI ID>@student.uwa.edu.au` until the user module stores real addresses |
| 7 | No online slicing | Only pre-sliced `.gcode` files from PrusaSlicer are accepted |
| 8 | G-code parser targets PrusaSlicer metadata comments | Files from other slicers may lack the expected metadata and fail validation |

## Fixed on a branch, awaiting merge

| # | Item | Branch |
|---|---|---|
| 9 | Frontend allowed pause and cancel to be fired together on the same job | `fix/mock-dispatcher-cancel-refund` |

## Resolved (for reference)

JWT secret fallback, wallet amount validation, double ledger entry on refunds, wallet concurrency control, cancel fallback status and pause/resume concurrency and the admin Usage Trends tab have all been fixed or delivered and merged.

## Future work

- Connect the dispatcher to Prusa Connect / PrusaLink for real printer status, job dispatch and camera views.
- Add a real payment or voucher flow for wallet top-ups.
- Extend the admin Usage Trends tab with per-printer and per-material breakdowns and CSV export.
- Support more slicers and online slicing.
- Move user email addresses into the user profile and configure production SMTP.
