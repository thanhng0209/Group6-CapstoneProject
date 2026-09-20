import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse } from '../models/user.model';

const TOKEN_KEY = 'printer_farm_token';
const API_BASE = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class AuthService {

  // Lets components (e.g. a nav bar) reactively know if someone's logged in.
  private loggedInSubject = new BehaviorSubject<boolean>(this.hasToken());
  loggedIn$ = this.loggedInSubject.asObservable();

  constructor(private http: HttpClient) { }

  login(email: string, password: string): Observable<LoginResponse> {
    const body: LoginRequest = { email, password };

    return this.http.post<LoginResponse>(`${API_BASE}/auth/login`, body).pipe(
      tap((response) => {
        localStorage.setItem(TOKEN_KEY, response.token);
        this.loggedInSubject.next(true);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.loggedInSubject.next(false);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return this.hasToken();
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(TOKEN_KEY);
  }
}
