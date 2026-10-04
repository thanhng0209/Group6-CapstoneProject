import { Component, OnDestroy, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Router, RouterLink, RouterLinkActive } from "@angular/router";
import { UserService } from "../services/user.service";
import { JobService } from "../services/job.service";
import { AuthService } from "../auth/auth.service";
import { UserDashboard } from "../models/user.model";

type CollectionJob = UserDashboard["pendingCollectionJobs"][number];

@Component({
  selector: "app-pending-collection",
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: "./pending-collection.component.html",
  styleUrl: "../dashboard/dashboard.component.css",
})
export class PendingCollectionComponent implements OnInit, OnDestroy {
  dashboard: UserDashboard | null = null;
  isLoading = true;
  errorMessage: string | null = null;
  feedbackMessage: { text: string; type: "success" | "error" } | null = null;
  collectingJobId: number | null = null;
  selectedCollectionJob: CollectionJob | null = null;
  collectionError: string | null = null;

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private userService: UserService,
    private jobService: JobService,
    private authService: AuthService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.loadDashboard();
    this.pollTimer = setInterval(() => this.refreshDashboard(), 8000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
  }

  get pendingCollectionJobs(): CollectionJob[] {
    return this.dashboard?.pendingCollectionJobs ?? [];
  }

  loadDashboard(): void {
    this.isLoading = true;
    this.userService.getDashboard().subscribe({
      next: (dashboard) => {
        this.dashboard = dashboard;
        this.isLoading = false;
        this.errorMessage = null;
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = "Failed to load pending collection items.";
      },
    });
  }

  private refreshDashboard(): void {
    this.userService.getDashboard().subscribe({
      next: (dashboard) => {
        this.dashboard = dashboard;
        this.errorMessage = null;
      },
    });
  }

  openCollectionConfirmation(job: CollectionJob): void {
    if (this.collectingJobId !== null) {
      return;
    }

    this.selectedCollectionJob = job;
    this.collectionError = null;
  }

  closeCollectionConfirmation(): void {
    if (this.collectingJobId !== null) {
      return;
    }

    this.selectedCollectionJob = null;
    this.collectionError = null;
  }

  confirmCollection(): void {
    const job = this.selectedCollectionJob;
    if (!job || this.collectingJobId !== null) {
      return;
    }

    this.collectingJobId = job.jobId;
    this.collectionError = null;
    this.feedbackMessage = null;
    this.jobService.confirmCollection(job.jobId).subscribe({
      next: (response) => {
        this.collectingJobId = null;
        this.selectedCollectionJob = null;
        this.feedbackMessage = {
          text:
            response.message || `Collection confirmed for job #${job.jobId}.`,
          type: "success",
        };
        this.refreshDashboard();
      },
      error: (error) => {
        this.collectingJobId = null;
        this.collectionError =
          error.error?.errors?.[0] ||
          "Could not confirm collection. Please try again.";
      },
    });
  }

  formatDate(isoString: string | null | undefined): string {
    if (!isoString) {
      return "-";
    }
    const date = new Date(isoString);
    return Number.isNaN(date.getTime())
      ? isoString
      : date.toLocaleDateString(undefined, {
          month: "short",
          day: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(["/login"]);
  }
}
