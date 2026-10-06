import { Routes } from "@angular/router";
import { LoginComponent } from "./auth/login/login.component";
import { DashboardComponent } from "./dashboard/dashboard.component";
import { JobSubmissionComponent } from "./job-submission/job-submission.component";
import { AdminDashboardComponent } from "./admin/admin-dashboard.component";
import { PendingCollectionComponent } from "./pending-collection/pending-collection.component";
import { TransactionHistoryComponent } from "./transaction-history/transaction-history.component";
import { authGuard } from "./auth/auth.guard";
import { adminGuard } from "./auth/auth.guard";

export const routes: Routes = [
  { path: "login", component: LoginComponent },
  {
    path: "dashboard",
    component: DashboardComponent,
    canActivate: [authGuard],
  },
  {
    path: "pending-collection",
    component: PendingCollectionComponent,
    canActivate: [authGuard],
  },
  {
    path: "transactions",
    component: TransactionHistoryComponent,
    canActivate: [authGuard],
  },
  {
    path: "admin",
    component: AdminDashboardComponent,
    canActivate: [adminGuard],
  },
  {
    path: "admin/dashboard",
    component: AdminDashboardComponent,
    canActivate: [adminGuard],
  },
  {
    path: "submit",
    component: JobSubmissionComponent,
    canActivate: [authGuard],
  },
  {
    path: "submit-job",
    component: JobSubmissionComponent,
    canActivate: [authGuard],
  },
  { path: "", redirectTo: "/login", pathMatch: "full" },
  { path: "**", redirectTo: "/login" },
];
