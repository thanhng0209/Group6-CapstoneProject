# UWA 3D Printer Farm Management System

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-18-red.svg)](https://angular.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)

A comprehensive, full-stack 3D Printer Farm Management and Scheduling platform developed for the **University of Western Australia (UWA)**. The system streamlines self-service 3D print job submission for students, automates G-code slicing compatibility checks, manages digital wallet balances and cost estimations, coordinates physical 3D printer hardware telemetry, and provides farm managers with rich administrative and financial monitoring tools.

---

## Table of Contents
- [System Architecture](#system-architecture)
- [Key Features](#key-features)
  - [1. Authentication & Role-Based Access Control (RBAC)](#1-authentication--role-based-access-control-rbac)
  - [2. Student Print Dashboard & Real-Time Tracking](#2-student-print-dashboard--real-time-tracking)
  - [3. Digital Wallet & Top-Up System](#3-digital-wallet--top-up-system)
  - [4. G-Code Validation & Hardware Compatibility](#4-g-code-validation--hardware-compatibility)
  - [5. Print Job Submission & Cost Calculation](#5-print-job-submission--cost-calculation)
  - [6. Job Cancellation & Automated Policy Refunds](#6-job-cancellation--automated-policy-refunds)
  - [7. Physical Printer Inventory & Live Telemetry](#7-physical-printer-inventory--live-telemetry)
  - [8. Mock Printer Hardware Dispatcher](#8-mock-printer-hardware-dispatcher)
  - [9. Admin Console & Financial Analytics](#9-admin-console--financial-analytics)
- [Tech Stack](#tech-stack)
- [Step-by-Step Setup Guide (with Docker)](#step-by-step-setup-guide-with-docker)
  - [Prerequisites](#prerequisites)
  - [Step 1: Start PostgreSQL with Docker](#step-1-start-postgresql-with-docker)
  - [Step 2: Start the Spring Boot Backend](#step-2-start-the-spring-boot-backend)
  - [Step 3: Start the Angular Frontend](#step-3-start-the-angular-frontend)
  - [Step 4: Log In and Test](#step-4-log-in-and-test)
- [Automated Testing & Verification](#automated-testing--verification)
- [API Reference](#api-reference)

---

## System Architecture

```
                                  ┌────────────────────────┐
                                  │   Angular 18 Frontend  │
                                  │ (Port 4200 - Dark UI)  │
                                  └───────────┬────────────┘
                                              │ HTTP / JWT REST
                                              ▼
                                  ┌────────────────────────┐
                                  │  Spring Boot 3 Backend │
                                  │ (Port 8080 - Java 17)  │
                                  └─────┬────────────┬─────┘
                                        │            │
                         JPA / Flyway   │            │ Dispatcher Loop
                                        ▼            ▼
                          ┌────────────────┐   ┌────────────────────────┐
                          │ PostgreSQL 16  │   │ Mock Printer Simulator │
                          │ (Docker: 5432) │   │  (Physical Telemetry)  │
                          └────────────────┘   └────────────────────────┘
```

---

## Key Features

### 1. Authentication & Role-Based Access Control (RBAC)
- **Institutional Login**: Secure authentication using university ID credentials (e.g. UNI ID `00000000`).
- **Stateless Security**: Spring Security with JSON Web Tokens (JWT) attached automatically to frontend requests via an HTTP interceptor.
- **Role Hierarchy**: Strict authorization segregation between `STUDENT`, `STAFF`, and `ADMIN`.
- **Auto-Provisioning**: Unseen student IDs are automatically registered on first authenticated access with default starting credit.

### 2. Student Print Dashboard & Real-Time Tracking
- **Live Fleet Polling**: Automatic background status polling updating active job progress every 8 seconds.
- **Visual Job Management**: Color-coded status badges for `QUEUED`, `PRINTING`, `COMPLETED`, `FAILED`, and `CANCELLED`.
- **Comprehensive Job Details**: Displays file name, target printer, material type, estimated weight/duration, total cost, and formatted submission timestamps.

### 3. Digital Wallet & Top-Up System
- **Real-Time Balance Display**: Accurate available balance rendered in the student dashboard balance card.
- **Interactive Top-Up Modal**:
  - Quick-selection preset amount chips: **+$10**, **+$20**, **+$50**, **+$100**.
  - Custom dollar amount input with instant client-side validation (`> $0.00`).
  - Dynamic **Projected Balance Preview** displaying what the new balance will be before confirming.
- **Immutable Transaction Ledger**: Every credit, print deduction, and refund is permanently recorded in PostgreSQL with `TransactionType.TOPUP`, `DEBIT`, or `REFUND` for comprehensive financial auditing.

### 4. G-Code Validation & Hardware Compatibility
- **PrusaSlicer ASCII Parser**: Fast streaming extraction of metadata comments: printer profile, build dimensions (X, Y, Z), layer height, nozzle size, filament type, extruder/bed temperatures, and estimated duration.
- **5-Rule Compatibility Engine**:
  1. *Profile Verification*: Confirms G-code target profile matches the selected printer.
  2. *Volume Boundaries*: Prevents hardware collisions by verifying model dimensions do not exceed physical bed axes.
  3. *Nozzle Size Compatibility*: Validates sliced nozzle diameter against approved printer tooling.
  4. *Layer Height Bounds*: Ensures layer resolution falls within the machine's safe operational thresholds.
  5. *Filament Approval*: Rejects unauthorized or untested materials.
- **Physical Machine Harmonization**: Seamlessly accepts physical printer instance IDs (e.g. `PRUSA_XL_1`) by looking up their underlying hardware model profile (`PRUSA_XL`), while maintaining backward compatibility for pure model identifiers.

### 5. Print Job Submission & Cost Calculation
- **Dual-Factor Costing Algorithm**:
  $$\text{Cost} = (\text{Filament Rate} \times \text{Weight in Grams}) + (\text{Machine Hourly Rate} \times \text{Minutes})$$
- **Balance Gatekeeping**: Verifies wallet balance prior to dispatch, preventing insufficient funds submissions while preserving clear user error messaging.
- **Automated Queuing**: Deducts cost atomically and transitions accepted jobs into the `QUEUED` state.

### 6. Job Cancellation & Automated Policy Refunds
- **One-Click Cancellation**: Students can cancel any job while in the `QUEUED` state directly from the dashboard.
- **Policy-Based Instant Refund**: Funds are automatically credited back to the student's wallet balance, accompanied by an immutable `REFUND` ledger transaction.

### 7. Physical Printer Inventory & Live Telemetry
- **Physical Machine Registry**: Pre-configured inventory reflecting the actual UWA lab hardware:
  - `PRUSA_XL_1`: Prusa XL (Dual Tool) — PLA (Prusa Orange)
  - `PRUSA_MK4S_1`: Prusa MK4S #1 — PETG (Galaxy Black)
  - `PRUSA_CORE_ONE_1`: Prusa Core One #1 — PLA (White)
- **Live Telemetry Dropdown**: Slicing submission UI renders complete physical machine context including loaded material, filament color, and operational status (`IDLE`, `PRINTING`, `MAINTENANCE`).

### 8. Mock Printer Hardware Dispatcher
- **Hardware Simulation Loop**: An automated asynchronous dispatcher simulates physical printer behavior in development and testing environments.
- **Job Lifecycle Transitions**: Automatically matches `QUEUED` jobs to compatible `IDLE` printers, moves printer state to `PRINTING`, simulates print duration, and completes the print job.

### 9. Admin Console & Financial Analytics
- **Fleet Monitoring**: Real-time breakdown of printer availability and status distribution.
- **Financial Audit Metrics**: High-level financial reporting displaying total charged, total refunded, and net lab revenue.
- **Filament Consumption Analytics**: Summarizes total filament consumption (grams) grouped by polymer material.
- **Manual Refund Management**: Administrative queue for inspecting, approving, or rejecting disputed refunds.

---

## Tech Stack

| Layer | Technologies |
|---|---|
| **Backend** | Java 17, Spring Boot 3.3.3 (Web, Security, Data JPA), Flyway Migration, Lombok, Swagger/OpenAPI |
| **Frontend** | Angular 18 (Standalone Components), TypeScript, RxJS, Vanilla CSS |
| **Database** | PostgreSQL 16 (production/dev), H2 Database (in-memory unit test suite) |
| **Container** | Docker, Docker Compose |

---

## Step-by-Step Setup Guide (with Docker)

Follow these instructions to run the entire system locally.

### Prerequisites
Before getting started, ensure you have installed:
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (must be running)
- [Java 17 JDK](https://adoptium.net/) or higher
- [Apache Maven](https://maven.apache.org/) (or use `./mvnw`)
- [Node.js](https://nodejs.org/) (v18.x or v20.x LTS) & `npm`

---

### Step 1: Start PostgreSQL with Docker

You can spin up the PostgreSQL database container either using **Docker Compose** or via a direct **Docker Run** command.

#### Option A: Using Docker Compose (Recommended)
From the repository root directory:
```bash
docker compose up -d
```

#### Option B: Using Standalone Docker Run
```bash
docker run --name printerfarm-postgres \
  -e POSTGRES_DB=printerfarm \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -d postgres:16
```

Verify that the database is running:
```bash
docker ps
```
You should see `printerfarm-postgres` running and listening on port `5432`.

---

### Step 2: Start the Spring Boot Backend

1. Navigate to the `backend/` directory:
   ```bash
   cd backend
   ```
2. Start the Spring Boot application using Maven:
   ```bash
   mvn spring-boot:run
   ```
3. **Database Migration**: Flyway will automatically run the schema migrations:
   - `V1`: Creates users table and provisions the initial Administrator account.
   - `V2`: Sets verified password hash for the default admin.
   - `V3`: Creates `printers`, `jobs`, `refund_requests`, and `transactions` tables with seed printers.
4. Once started, the backend is live at:
   - **REST API Base**: `http://localhost:8080/api`
   - **Swagger UI Documentation**: `http://localhost:8080/swagger-ui.html`

---

### Step 3: Start the Angular Frontend

1. Open a new terminal window and navigate to `frontend/`:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Launch the development server:
   ```bash
   npm start
   ```
4. The web application will be accessible at:
   - **Web App URL**: `http://localhost:4200`

---

### Step 4: Log In and Test

1. Open your browser and navigate to `http://localhost:4200`.
2. **Default Credentials**:
   - **Admin Account**:
     - UNI ID: `00000000`
     - Password: `AdminPass123!`
   - **Student Account**:
     - Enter any standard 8-digit student number (e.g., `22345678`) or log in as Admin. Unseen students will be auto-provisioned with a default starting balance of **$50.00**.
3. **Recommended Test Walkthrough**:
   - **Top Up Wallet**: On the Dashboard balance card, click **`+ Top Up Balance`**, select **`+$50`**, and confirm. Your balance updates immediately.
   - **Submit Print Job**: Click **`Submit New Job`**, select a target printer (e.g. `Prusa XL (Dual Tool)`), upload a valid G-code file (`backend/src/test/resources/gcode/valid_xl.gcode`), click **`Validate G-code Compatibility`**, and confirm submission.
   - **Cancel & Refund**: Return to the dashboard and cancel the queued job to observe the automatic refund.
   - **Admin Console**: Click **`Admin Console`** (top right for admin users) to view real-time fleet activity, revenue metrics, and filament usage.

---

## Automated Testing & Verification

### Running Backend Unit & Integration Tests
The backend test suite runs against an isolated in-memory H2 database with mock security contexts:
```bash
cd backend
mvn test
```
*Current test suite: **103 tests passed, 0 failures, 0 errors**.*

### Compiling Frontend Production Bundle
Verify TypeScript type checking and Angular AOT template compilation:
```bash
cd frontend
npm run build
```

---

## API Reference

| Module | Method | Endpoint | Description | Auth Required |
|---|---|---|---|---|
| **Auth** | `POST` | `/api/auth/login` | Authenticate with UNI ID & password, returns JWT | None |
| **User** | `GET` | `/api/user/dashboard` | Returns current user profile and balance | Student / Staff / Admin |
| **Wallet** | `GET` | `/api/wallet/{uniId}/balance` | Returns dollar balance for specified user | Self or Admin |
| **Wallet** | `POST` | `/api/wallet/{uniId}/credit` | Top up / credit dollar balance to user account | Self or Admin |
| **Wallet** | `POST` | `/api/wallet/{uniId}/debit` | Deduct dollar balance from user account | Admin |
| **Printers** | `GET` | `/api/printers` | Get full physical printer inventory & telemetry | Public |
| **G-Code** | `POST` | `/api/gcode/upload` | Upload & validate .gcode file compatibility | Public |
| **G-Code** | `GET` | `/api/gcode/printers` | List printer profile registry options | Public |
| **Jobs** | `POST` | `/api/jobs/submit` | Deducts balance and queues print job | Authenticated |
| **Jobs** | `GET` | `/api/jobs/my` | Retrieve print jobs owned by caller | Authenticated |
| **Jobs** | `POST` | `/api/jobs/{id}/cancel` | Cancel print job and process refund | Owner or Admin |
| **Admin** | `GET` | `/api/admin/jobs` | Retrieve all jobs filtered by status | Admin |
| **Admin** | `GET` | `/api/admin/printers` | Aggregate activity statistics by printer | Admin |
| **Admin** | `GET` | `/api/admin/costs` | Total charged, refunded, and net lab revenue | Admin |
| **Admin** | `GET` | `/api/admin/filament-usage` | Filament consumption in grams by material | Admin |
| **Admin** | `GET` | `/api/admin/refunds/pending` | List pending manual refund approval requests | Admin |
| **Admin** | `POST` | `/api/admin/refunds/{id}/approve` | Approve refund, credit wallet, record transaction | Admin |
| **Admin** | `POST` | `/api/admin/refunds/{id}/reject` | Reject refund without crediting | Admin |
