import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { UserService } from '../services/user.service';
import { AuthService } from '../auth/auth.service';
import { JobService } from '../services/job.service';
import { UserDashboard } from '../models/user.model';

type DashboardJob = UserDashboard['currentJobs'][number];

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent implements OnInit, OnDestroy {
  dashboard: UserDashboard | null = null;

  isLoading = true;
  isRefreshing = false;
  cancellingJobId: number | null = null;

  errorMessage: string | null = null;

  feedbackMessage: {
    text: string;
    type: 'success' | 'error';
  } | null = null;

  lastUpdated: Date = new Date();

  // Top-Up Modal state
  showTopUpModal = false;
  isToppingUp = false;
  topUpAmount: number | null = 20;
  topUpErrorMessage: string | null = null;

  readonly presetAmounts = [10, 20, 50, 100];

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private userService: UserService,
    private authService: AuthService,
    private jobService: JobService,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.loadInitialData();

    // Refresh the complete dashboard every 8 seconds.
    this.pollTimer = setInterval(() => {
      this.refreshDashboardSilently();
    }, 8000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  /**
   * Loads the complete dashboard from:
   * GET /api/user/dashboard
   *
   * This provides:
   * - User profile
   * - Balance
   * - Current jobs
   * - Print history
   */
  loadInitialData(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.userService.getDashboard().subscribe({
      next: (data) => {
        this.dashboard = data;
        this.lastUpdated = new Date();
        this.isLoading = false;
      },

      error: (err) => {
        console.error('Failed to load dashboard', err);
        this.errorMessage = 'Failed to load dashboard data.';
        this.isLoading = false;
      },
    });
  }

  /**
   * Manually refresh the complete dashboard.
   */
  manualRefresh(): void {
    this.isRefreshing = true;
    this.errorMessage = null;

    this.userService.getDashboard().subscribe({
      next: (data) => {
        this.dashboard = data;
        this.lastUpdated = new Date();
        this.isRefreshing = false;
      },

      error: (err) => {
        console.error('Failed to refresh dashboard', err);
        this.errorMessage = 'Failed to refresh dashboard data.';
        this.isRefreshing = false;
      },
    });
  }

  /**
   * Background refresh used for job status updates.
   */
  private refreshDashboardSilently(): void {
    this.userService.getDashboard().subscribe({
      next: (data) => {
        this.dashboard = data;
        this.lastUpdated = new Date();
      },

      error: () => {
        // Ignore background polling errors.
      },
    });
  }

  /**
   * Current jobs shown on the dashboard.
   *
   * Backend currently classifies QUEUED and PRINTING
   * as current jobs.
   */
  get currentJobs(): DashboardJob[] {
    return this.dashboard?.currentJobs ?? [];
  }

  /**
   * Completed, failed and cancelled jobs.
   */
  get printHistory(): DashboardJob[] {
    return this.dashboard?.printHistory ?? [];
  }

  get activeJobsCount(): number {
    return this.currentJobs.length;
  }

  /**
   * The current dashboard API does not yet return estimated
   * filament information, so this card cannot calculate it
   * from the new dashboard response.
   */
  get estimatedFilamentGrams(): number | null {
    return null;
  }

  isEligibleForCancel(job: DashboardJob): boolean {
    return job.status === 'QUEUED';
  }

  cancelJob(job: DashboardJob): void {
    if (!this.isEligibleForCancel(job)) {
      return;
    }

    const confirmCancel = window.confirm(
      `Are you sure you want to cancel print job #${job.jobId} ("${job.fileName}")?\n\nThis will stop the job and process a refund to your balance.`
    );

    if (!confirmCancel) {
      return;
    }

    this.cancellingJobId = job.jobId;
    this.feedbackMessage = null;

    this.jobService.cancelJob(job.jobId).subscribe({
      next: (res) => {
        this.cancellingJobId = null;

        this.feedbackMessage = {
          text: res.message || `Job #${job.jobId} cancelled successfully.`,
          type: 'success',
        };

        // Reload the complete dashboard.
        // The cancelled job should now appear in printHistory.
        this.loadInitialData();

        setTimeout(() => {
          if (this.feedbackMessage?.type === 'success') {
            this.feedbackMessage = null;
          }
        }, 6000);
      },

      error: (err) => {
        this.cancellingJobId = null;

        const msg =
          err.error?.errors?.[0] ||
          'Failed to cancel print job. It may have already started printing.';

        this.feedbackMessage = {
          text: msg,
          type: 'error',
        };
      },
    });
  }

  formatDate(isoString: string | null | undefined): string {
    if (!isoString) {
      return '-';
    }

    try {
      const d = new Date(isoString);

      return d.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  openTopUpModal(): void {
    this.topUpAmount = 20;
    this.topUpErrorMessage = null;
    this.showTopUpModal = true;
  }

  closeTopUpModal(): void {
    if (this.isToppingUp) {
      return;
    }

    this.showTopUpModal = false;
    this.topUpErrorMessage = null;
  }

  selectPresetAmount(amount: number): void {
    this.topUpAmount = amount;
    this.topUpErrorMessage = null;
  }

  onCustomAmountChange(): void {
    this.topUpErrorMessage = null;
  }

  isValidTopUpAmount(): boolean {
    return (
      this.topUpAmount !== null &&
      !isNaN(this.topUpAmount) &&
      this.topUpAmount > 0
    );
  }

  getProjectedBalance(): number {
    const current = this.dashboard?.balance ?? 0;

    const add = this.isValidTopUpAmount()
      ? Number(this.topUpAmount)
      : 0;

    return Math.round((current + add) * 100) / 100;
  }

  confirmTopUp(): void {
    if (!this.isValidTopUpAmount() || !this.dashboard) {
      this.topUpErrorMessage =
        'Please select or enter a valid dollar amount greater than 0.';
      return;
    }

    const amount = Number(this.topUpAmount);

    this.isToppingUp = true;
    this.topUpErrorMessage = null;

    this.userService
      .topUpBalance(this.dashboard.uniId, amount)
      .subscribe({
        next: (res) => {
          this.isToppingUp = false;

          const newBalance =
            res?.balanceAfter !== undefined
              ? Number(res.balanceAfter)
              : this.dashboard!.balance + amount;

          this.dashboard!.balance = newBalance;

          this.closeTopUpModal();

          this.feedbackMessage = {
            text: `Successfully topped up $${amount.toFixed(
              2
            )}! Available balance is now $${newBalance.toFixed(2)}.`,
            type: 'success',
          };

          // Reload complete dashboard data to stay in sync.
          this.userService.getDashboard().subscribe({
            next: (userData) => {
              this.dashboard = userData;
              this.lastUpdated = new Date();
            },
          });

          setTimeout(() => {
            if (this.feedbackMessage?.type === 'success') {
              this.feedbackMessage = null;
            }
          }, 6000);
        },

        error: (err) => {
          this.isToppingUp = false;

          const msg =
            err.error?.error ||
            err.error?.errors?.[0] ||
            'Top-up failed. Please verify your connection and try again.';

          this.topUpErrorMessage = msg;
        },
      });
  }
}