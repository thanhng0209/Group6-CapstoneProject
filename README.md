# Authentication & User Dashboard — Workstream 1

Covers everything in your task list:
- Spring Security login using UNI ID-style credentials
- Role-based access control (STUDENT / STAFF vs ADMIN)
- Angular user dashboard (balance, current jobs, print history)
- Backend `/user` endpoints (profile, balance, job history)
- `users` table in PostgreSQL (Flyway migration)
- Unit tests for login/auth
- Acceptance check: a logged-in user sees their real balance and job history on first load

## Project layout

```
printer-farm/
├── backend/    Spring Boot API (Java 17, Maven)
└── frontend/   Angular app (standalone components)
```

## Running the backend

1. Install PostgreSQL locally and create a database:
   ```sql
   CREATE DATABASE printer_farm;
   CREATE USER printer_farm_app WITH PASSWORD 'changeme';
   GRANT ALL PRIVILEGES ON DATABASE printer_farm TO printer_farm_app;
   ```
2. From `backend/`, run:
   ```bash
   mvn spring-boot:run
   ```
   Flyway will automatically run `V1__create_users_table.sql` on startup and seed one admin
   account (UNI ID `00000000`, password `AdminPass123!` — **change this before any real use**).
3. API will be live at `http://localhost:8080`.

### Running the tests
```bash
mvn test
```
Tests run against an in-memory H2 database (see `src/test/resources/application-test.yml`),
so they don't require PostgreSQL to be running.

## Running the frontend

```bash
cd frontend
npm install
npm start
```
App will be live at `http://localhost:4200`, and will call the backend at
`http://localhost:8080/api`.

## API Endpoints

### Printer Management
- `GET /api/printers` — Retrieve inventory of physical 3D printers with their status (`IDLE`, `PRINTING`, etc.), loaded material/filament, and colors.

### Admin Monitoring & Refund Decisions
- `GET /api/admin/jobs` — View jobs by status.
- `GET /api/admin/printers` — View printer activity summary.
- `GET /api/admin/costs` — View financial breakdown (total charged, refunded, net revenue).
- `GET /api/admin/filament-usage` — View total filament consumption by material.
- `GET /api/admin/refunds/pending` — List pending refund requests.
- `POST /api/admin/refunds/{id}/approve` — Approve pending refund, credit student wallet, and record ledger transaction.
- `POST /api/admin/refunds/{id}/reject` — Reject pending refund request without crediting.

## How the pieces connect

```
LoginComponent
   → AuthService.login()
   → POST /api/auth/login   (AuthController → UserService → AuthenticationManager)
   → JWT returned, stored in localStorage
   → redirect to /dashboard

DashboardComponent (behind authGuard)
   → UserService.getDashboard()
   → jwt.interceptor.ts attaches "Authorization: Bearer <token>" automatically
   → GET /api/user/dashboard   (JwtAuthFilter validates token → UserController → UserService)
   → real balance + profile shown; job lists are empty until the
     PrintJob entity exists (owned by other workstreams)
```

## Next steps / things intentionally left as TODOs for this workstream

- Wire up an actual **registration/account-provisioning** flow (currently only login exists —
  confirm with the client whether accounts are pre-provisioned from a UWA student/staff list,
  or self-registered).
- Once the **PrintJob** entity exists (File & G-code Validation / Job & Financial Management
  workstreams), replace the empty lists in `UserService.getDashboard()` with real queries.
- Swap the placeholder JWT secret and seeded admin password before any real deployment —
  both are marked clearly in `application.yml` / the migration file.
- Add a `/api/auth/register` endpoint if the client confirms self-service signup is in scope.
