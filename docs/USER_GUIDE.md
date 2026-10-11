# User Guide

How to use the UWA 3D Printer Farm Management System as a **student / staff member** or as a **farm manager (admin)**.
To install and start the system first, follow the [Setup Guide in the README](../README.md#step-by-step-setup-guide-with-docker).

- [Logging in](#logging-in)
- [Student / staff guide](#student--staff-guide)
- [Farm manager (admin) guide](#farm-manager-admin-guide)
- [Costs, cancellation and refund rules](#costs-cancellation-and-refund-rules)
- [Troubleshooting](#troubleshooting)

---

## Logging in

1. Open `http://localhost:4200`.
2. Enter your **UNI ID** and password.
3. Students and staff who have never signed in before are created automatically on first login and start with a **$50.00** wallet balance.

For local testing, the seeded admin account is UNI ID `00000000` / password `AdminPass123!`.
Admins see an extra **Farm Manager** link in the sidebar.

---

## Student / staff guide

After login you land on the **Summary** dashboard. The left sidebar has five pages:

| Sidebar item | What it is for |
|---|---|
| Summary | Wallet balance, your active jobs and their status |
| Submit Print Job | Upload a G-code file and send it to a printer |
| My Print Jobs | Your jobs, with pause / resume / cancel controls |
| Pending Collection | Finished prints waiting for you to pick up |
| Transaction History | Every top-up, print charge and refund on your wallet |

### 1. Top up your wallet

1. On the **Summary** page click **+ Top Up Balance**.
2. Pick a preset (**+$10, +$20, +$50, +$100**) or type a custom amount greater than $0.00.
3. The dialog shows your **projected balance**. Click confirm and the balance updates immediately.

### 2. Submit a print job

1. Click **Submit Print Job**.
2. Choose a printer. Each entry shows the loaded material, filament colour and status (`IDLE`, `PRINTING`, `MAINTENANCE`).
3. Upload a **sliced `.gcode` file** exported from PrusaSlicer. A sample is in `backend/src/test/resources/gcode/valid_xl.gcode`.
4. Click **Validate G-code Compatibility**. The system checks five rules: printer profile, bed volume, nozzle size, layer height and filament type. If a rule fails you get a message saying which one, so re-slice with the right profile and upload again.
5. When validation passes, you see the estimated weight, duration and **cost**. Click confirm to submit.
6. The cost is deducted from your wallet and the job enters the queue as `QUEUED`. If your balance is too low the submission is refused with a clear error and nothing is charged.

### 3. Follow and control your jobs

The dashboard refreshes automatically every 8 seconds. Job status badges:

| Status | Meaning |
|---|---|
| `QUEUED` | Waiting for a compatible idle printer |
| `PRINTING` | Currently being printed |
| `PAUSED` | Printing was paused by you |
| `COMPLETED` | Finished, ready for collection |
| `FAILED` | The print failed |
| `CANCELLED` | Cancelled (and refunded if eligible) |

- **Pause** a `PRINTING` job, then **Resume** it later.
- **Cancel** a job to stop it. See [refund rules](#costs-cancellation-and-refund-rules) below.
- While one of these requests is being processed the other buttons on that job are disabled, so you cannot send conflicting actions.

### 4. Collect a finished print

1. When a job is `COMPLETED` you receive an email at your UWA address.
2. Open **Pending Collection**. It lists every finished job with printer and ready-since time.
3. Pick the print up from the lab, then click **Confirm collection** and confirm the dialog. The job leaves the list.

You can only confirm collection for your own completed jobs.

### 5. Review your wallet history

**Transaction History** lists, newest first, every `TOPUP`, `DEBIT` (print charge) and `REFUND`, with the date and your balance after each one. Entries are permanent and cannot be edited.

---

## Farm manager (admin) guide

Click **Farm Manager** in the sidebar (admins only). The page has four tabs.

### Farm Status
- Printer fleet cards: status (`IDLE`, `PRINTING`, `MAINTENANCE`), loaded material and per-printer activity.
- Use **Refresh** to reload telemetry immediately.

### Jobs
- All jobs from all users, filterable by status and searchable.
- Use this to find a job a student is asking about.

### Financial
- Totals: **charged**, **refunded** and **net lab revenue**.
- Filament consumption in grams, grouped by material.
- **Pending Refund Requests:** requests from students who cancelled outside the automatic window. Review each one and click **Approve** (credits the wallet and records a `REFUND` entry) or **Reject** (no credit). A request can only be decided once.

### Usage Trends
- Shows how usage and money change over time, one bar per day.
- Pick **7, 30 or 90 days** with the buttons at the top right.
- Summary cards give the totals for the range: filament used, amount charged, amount refunded and number of jobs.
- Two charts: **filament used per day** (grams) and **charged vs refunded per day** (dollars). Hover a bar to see the exact figures for that day.
- Days with no jobs are shown as empty bars, so gaps are visible. If the whole range is empty a note says so.

---

## Costs, cancellation and refund rules

**Cost of a job**

```
cost = (material rate × grams) + (machine rate × minutes)
```

Current rates are placeholders pending confirmation from the client (see [Known Issues](KNOWN_ISSUES.md)):

| Material | Rate per gram |
|---|---|
| PLA | $0.05 |
| ABS | $0.06 |
| PETG | $0.07 |
| Machine time | $0.02 per minute |

**Cancellation and refunds**

| When you cancel | What happens |
|---|---|
| Within the cancellation window (currently **5 minutes** after submission) | The full cost is refunded to your wallet automatically, with a `REFUND` entry |
| After the window | A refund request is created and waits for a farm manager to approve or reject it. Nothing is credited until it is approved |

Each refund creates exactly one `REFUND` entry in your transaction history.

---

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| "Insufficient balance" on submit | Top up your wallet, then submit again |
| G-code validation fails | Re-slice in PrusaSlicer with the profile of the printer you selected, and an approved material and nozzle |
| Cannot log in | Check the UNI ID and password. The backend must be running on port 8080 |
| Pending Collection is empty | Only `COMPLETED` jobs you have not yet collected appear there |
| Completion emails never arrive | In development the backend points at a placeholder SMTP server (`localhost:1025`). Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` and `MAIL_PASSWORD` for real delivery |
