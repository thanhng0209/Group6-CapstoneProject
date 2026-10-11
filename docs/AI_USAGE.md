# Use of Generative AI

This project was developed with assistance from generative AI tools. This page records which tools were used, what they were used for, and how their output was reviewed, tested, adapted or rejected before being incorporated into the project.

## Tools used

| Tool | Where it shows up | Used for |
|---|---|---|
| Claude Code (Anthropic) | Commits and PRs in the Job, Wallet and Notification modules | Analysing the codebase, auditing for potential bugs, proposing fixes and tests, and drafting issue and PR text |
| GitHub Copilot coding agent | Branches named `copilot/*` and commits by `copilot-swe-agent[bot]` | Addressing code-review comments and proposing changes for review |
| Cursor | Co-author trailer on one commit | In-editor code assistance |

## How AI output was checked

AI-generated suggestions were treated as proposals rather than automatically accepted solutions. The following checks were used for AI-assisted changes, where applicable:

1. **Run the tests.** Backend changes were checked using Maven tests (`mvn test`). Regression tests were added or updated where appropriate to verify expected behaviour and help prevent defects from recurring. For example, the wallet and pause/resume concurrency tests exercise concurrent operations using eight threads.

2. **Verify claims against the code.** Potential defects identified during AI-assisted audits were checked against the current implementation before changes were made. Findings that had already been resolved by a teammate were not treated as outstanding defects.

3. **Review the diff.** Proposed changes were reviewed through the team's normal GitHub pull-request workflow. The actual code changes were examined rather than relying only on AI-generated explanations or pull-request descriptions.

4. **Humans remain responsible for GitHub actions.** Team members remained responsible for deciding which suggestions to adopt, reviewing changes, and creating and merging issues and pull requests. AI-generated text was treated as draft material and checked before use.

5. **Consider security and configuration implications.** Suggestions affecting authentication, JWT configuration, wallet transactions and other sensitive behaviour were assessed for their security and operational implications, rather than only whether the code compiled.

These checks do not imply that every possible behaviour was exhaustively tested. Verification depended on the change, the affected functionality and the available tests.

## Examples where AI output was evaluated and changed

- **An empty fix was identified.** Copilot's PR #35, "Validate wallet amount sign at service boundary", was merged without changing any source files; it contained only the agent's placeholder "Initial plan" commit. A subsequent manual audit identified that the required validation was still absent. The validation was then implemented using `InvalidWalletAmountException`, with tests for zero and negative wallet amounts.

- **A fix was superseded rather than duplicated.** Claude Code proposed a refund-related fix involving `creditWithoutLedger` to address a potential duplicate-ledger-entry problem. A teammate merged an alternative implementation using `creditRefund` into `main` first. Rather than retaining overlapping implementations, the superseded AI-proposed implementation was dropped, while additional regression tests were retained. The `RefundLedgerRegressionTest`, where confirmed in the final repository, exercises the real `WalletService` and checks that the tested refund scenario produces exactly one `REFUND` entry and no `TOPUP` entry.

- **A security default was evaluated after review.** An AI suggestion for JWT secret configuration proposed failing at startup when no secret was configured. The team considered the impact on the local development workflow, where teammates ran `mvn spring-boot:run` without explicitly setting environment variables. The resulting approach used a randomly generated per-process key as a fallback, according to the implementation described in the project history. This supports local startup, but tokens signed with that key generally become invalid when the application restarts or the key changes. A stable, securely managed secret is still required for deployment, and the fallback must not inadvertently become an insecure production default.

- **Placeholder values were not invented.** Values such as print prices, the job-cancellation window and the starting wallet balance were treated as decisions requiring client confirmation rather than assumptions that could be resolved by AI-generated suggestions. Where these values remain provisional, they are identified as placeholders in the code and `KNOWN_ISSUES.md`, as confirmed in the relevant implementation.

## Limits of this record

This page documents AI use identifiable from the repository history, including commit attribution, co-author information, pull requests and related code changes. Git history may not capture all AI assistance used within individual editors or private conversations.

The examples demonstrate specific instances of review and evaluation. They should not be interpreted as proof that every AI-assisted change received identical testing or that all possible defects were eliminated.

The team remains responsible for the correctness, security, maintainability and suitability of the submitted code, regardless of whether AI tools contributed to its development.