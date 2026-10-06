import { Component, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Router, RouterLink, RouterLinkActive } from "@angular/router";
import { AuthService } from "../auth/auth.service";
import { UserService } from "../services/user.service";
import {
  UserDashboard,
  WalletTransaction,
} from "../models/user.model";

@Component({
  selector: "app-transaction-history",
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: "./transaction-history.component.html",
  styleUrls: [
    "../dashboard/dashboard.component.css",
    "./transaction-history.component.css",
  ],
})
export class TransactionHistoryComponent implements OnInit {
  dashboard: UserDashboard | null = null;
  transactions: WalletTransaction[] = [];
  page = 0;
  readonly pageSize = 20;
  totalElements = 0;
  totalPages = 0;
  isLoading = true;
  isLoadingTransactions = false;
  errorMessage: string | null = null;

  constructor(
    private userService: UserService,
    private authService: AuthService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.loadDashboard();
  }

  loadDashboard(): void {
    this.isLoading = true;
    this.errorMessage = null;
    this.userService.getDashboard().subscribe({
      next: (dashboard) => {
        this.dashboard = dashboard;
        this.isLoading = false;
        this.loadTransactions(0);
      },
      error: (error) => {
        console.error("Failed to load transaction history dashboard", error);
        this.isLoading = false;
        this.errorMessage = "Failed to load transaction history.";
      },
    });
  }

  loadTransactions(page: number): void {
    if (!this.dashboard || this.isLoadingTransactions) {
      return;
    }

    this.isLoadingTransactions = true;
    this.errorMessage = null;
    this.userService
      .getTransactionHistory(this.dashboard.uniId, page, this.pageSize)
      .subscribe({
        next: (result) => {
          this.transactions = result.transactions;
          this.page = result.page;
          this.totalElements = result.totalElements;
          this.totalPages = result.totalPages;
          this.isLoadingTransactions = false;
        },
        error: (error) => {
          console.error("Failed to load wallet transactions", error);
          this.isLoadingTransactions = false;
          this.errorMessage = "Failed to load transactions. Please try again.";
        },
      });
  }

  transactionLabel(type: WalletTransaction["type"]): string {
    switch (type) {
      case "TOPUP":
        return "Wallet top-up";
      case "DEBIT":
        return "Print job charge";
      case "REFUND":
        return "Refund";
    }
  }

  signedAmount(transaction: WalletTransaction): string {
    const sign = transaction.type === "DEBIT" ? "−" : "+";
    return `${sign}$${transaction.amount.toFixed(2)}`;
  }

  formatDate(isoString: string): string {
    const date = new Date(isoString);
    return Number.isNaN(date.getTime())
      ? isoString
      : date.toLocaleString(undefined, {
          year: "numeric",
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
