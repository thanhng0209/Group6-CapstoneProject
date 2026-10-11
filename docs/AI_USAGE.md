# Use of Generative AI

This project was built with the help of generative AI tools. This page records which tools were used, what for, and how their output was checked before it was merged.

## Tools used

| Tool | Where it shows up | Used for |
|---|---|---|
| Claude Code (Anthropic) | Commits and PRs in the Job, Wallet and Notification modules | Reading the codebase, auditing it for bugs, drafting fixes and tests, drafting issue and PR text |
| GitHub Copilot coding agent | Branches named `copilot/*` and commits by `copilot-swe-agent[bot]` | Addressing code-review comments on PRs |
| Cursor | Co-author trailer on one commit | In-editor code assistance |

## How AI output was checked

AI-generated changes were never merged on trust. The checks that were applied:

1. **Run the tests.** Every AI-drafted change had to pass `mvn test`. Bug fixes also had to come with a test that fails without the fix. For example, the wallet and pause/resume concurrency tests run 8 threads and fail against the unfixed code.
2. **Verify claims against the code.** AI audit findings were re-checked against the current `main` before being acted on, and findings that were already fixed by a teammate were dropped.
3. **Review the diff.** Each change went through a normal pull request and teammate review.
4. **Humans own the GitHub actions.** Issues and pull requests are created and merged by team members. AI tools only produce draft text.

## Examples where AI output was evaluated and changed

- **An empty "fix" was caught.** Copilot's PR #35, "Validate wallet amount sign at service boundary", was merged but changed 0 files: it only contained the agent's placeholder "Initial plan" commit. A manual audit found that the validation did not exist. It was then written properly (`InvalidWalletAmountException`) with tests for zero and negative amounts.
- **A fix was superseded, not forced in.** Claude Code drafted a double-ledger-entry fix for refunds (`creditWithoutLedger`). A teammate merged a different fix (`creditRefund`) to `main` first. Instead of keeping both, the AI version was dropped during the merge and only the extra value was kept: end-to-end regression tests (`RefundLedgerRegressionTest`) that run the real `WalletService` and assert exactly one `REFUND` entry and no `TOPUP` entry.
- **A security default was changed after review.** The AI suggestion for the hardcoded JWT secret was to fail on start-up when no secret is set. That would have broken every teammate's `mvn spring-boot:run`, which sets no environment variables, so the fallback became a random per-process key instead.
- **Placeholder values were not invented.** Prices, the cancellation window and the starting balance are marked as placeholders in the code and in [KNOWN_ISSUES.md](KNOWN_ISSUES.md) rather than being filled with AI-suggested numbers, because they are the client's decision.

## Limits of this record

This page lists the AI use that is visible in the repository history. Individual team members may have used AI tools in their own editors in ways that leave no trace in git.
