import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserDashboard } from '../models/user.model';

const API_BASE = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private http: HttpClient) {}

  getDashboard(): Observable<UserDashboard> {
    // JWT is attached automatically by jwt.interceptor.ts
    return this.http.get<UserDashboard>(`${API_BASE}/user/dashboard`);
  }
}
