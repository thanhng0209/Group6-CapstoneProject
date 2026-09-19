import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { UserService } from '../services/user.service';
import { AuthService } from '../auth/auth.service';
import { JobService } from '../services/job.service';
import { UserDashboard } from '../models/user.model';
import { Job } from '../models/job.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent implements OnInit, OnDestroy {
  dashboard: UserDashboard | null = null;
  jobs: Job[] = [];
  isLoading = true;
  isRefreshing = false;
  cancellingJobId: number | null = null;
  errorMessage: string | null = null;
  feedbackMessage: { text: string; type: 'success' | 'error' } | null = null;
  lastUpdated: Date = new Date();

  // Top-Up Modal state
  showTopUpModal = false;
  isToppingUp = false;
  topUpAmount: number | null = 20;
  topUpErrorMessage: string | null = null;
  readonly presetAmounts = [10, 20, 50, 100];

  private pollTimer: any = null;

  constructor(
    private userService: UserService,
    private authService: AuthService,
    private jobService: JobService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadInitialData();

    // Poll for real-time job status updates every 8 seconds
    this.pollTimer = setInterval(() => {
      this.refreshJobsSilently();
    }, 8000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  loadInitialData(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.userService.getDashboard().subscribe({
      next: (userData) => {
        this.dashboard = userData;
        this.fetchJobs(() => {
          this.isLoading = false;
        });
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Could not load dashboard profile. Please try again.';
      },
    });
  }

  fetchJobs(callback?: () => void): void {
    this.jobService.getMyJobs().subscribe({
      next: (jobList) => {
        this.jobs = jobList;
        this.lastUpdated = new Date();
        if (callback) callback();
      },
      error: () => {
        if (callback) callback();
      },
    });
  }

  manualRefresh(): void {
    this.isRefreshing = true;
    this.errorMessage = null;

    // Refresh user balance and jobs
    this.userService.getDashboard().subscribe({
      next: (userData) => {
        this.dashboard = userData;
      },
    });

    this.fetchJobs(() => {
      this.isRefreshing = false;
    });
  }

  private refreshJobsSilently(): void {
    this.jobService.getMyJobs().subscribe({
      next: (jobList) => {
        this.jobs = jobList;
        this.lastUpdated = new Date();
      },
      error: () => {
        // Silently ignore background polling errors
      },
    });
  }

  isEligibleForCancel(job: Job): boolean {
    return job.status === 'QUEUED';
  }

  cancelJob(job: Job): void {
    if (!this.isEligibleForCancel(job)) {
      return;
    }

    const confirmCancel = window.confirm(
      `Are you sure you want to cancel print job #${job.id} ("${job.fileName}")?\n\nThis will stop the job and process a refund to your balance.`
    );

    if (!confirmCancel) {
      return;
    }

    this.cancellingJobId = job.id;
    this.feedbackMessage = null;

    this.jobService.cancelJob(job.id).subscribe({
      next: (res) => {
        this.cancellingJobId = null;
        this.feedbackMessage = {
          text: res.message || `Job #${job.id} cancelled successfully.`,
          type: 'success',
        };

        // Reload user dashboard to update balance and fetch updated jobs
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
        this.feedbackMessage = { text: msg, type: 'error' };
      },
    });
  }

  formatMinutes(mins: number | null | undefined): string {
    if (mins == null || mins === 0) return '-';
    const m = Math.round(mins);
    const h = Math.floor(m / 60);
    const rem = m % 60;
    return h > 0 ? `${h}h ${rem}m` : `${rem}m`;
  }

  formatDate(isoString: string | null | undefined): string {
    if (!isoString) return '-';
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
    if (this.isToppingUp) return;
    this.showTopUpModal = false;
    this.topUpErrorMessage = null;
  }

  selectPresetAmount(amt: number): void {
    this.topUpAmount = amt;
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
    const add = this.isValidTopUpAmount() ? Number(this.topUpAmount) : 0;
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

    this.userService.topUpBalance(this.dashboard.uniId, amount).subscribe({
      next: (res) => {
        this.isToppingUp = false;
        const newBalance =
          res?.balanceAfter !== undefined
            ? Number(res.balanceAfter)
            : this.dashboard!.balance + amount;
        this.dashboard!.balance = newBalance;
        this.closeTopUpModal();

        this.feedbackMessage = {
          text: `Successfully topped up $${amount.toFixed(2)}! Available balance is now $${newBalance.toFixed(2)}.`,
          type: 'success',
        };

        // Reload user dashboard data to stay in sync
        this.userService.getDashboard().subscribe({
          next: (userData) => {
            this.dashboard = userData;
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
