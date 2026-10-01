import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';

import { apiUrl } from './env';
import { DemoUser, Role, Session } from './models';

/**
 * Holds the session in memory only (never localStorage), per docs/04-security-and-compliance.md.
 * A page refresh means signing in again; that is the trade-off for not exposing tokens to XSS via storage.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(null);

  readonly user = this.session.asReadonly();
  readonly signedIn = computed(() => {
    const s = this.session();
    return !!s && s.expiresAt > Date.now();
  });

  get token(): string | null {
    return this.signedIn() ? this.session()!.accessToken : null;
  }

  hasAnyRole(...roles: Role[]): boolean {
    const s = this.session();
    return !!s && s.roles.some(r => roles.includes(r));
  }

  demoUsers(): Observable<DemoUser[]> {
    return this.http.get<DemoUser[]>(`${apiUrl()}/api/v1/auth/demo-users`);
  }

  login(username: string, password: string): Observable<Session> {
    return this.http
      .post<Omit<Session, 'expiresAt'> & { expiresIn: number }>(`${apiUrl()}/api/v1/auth/login`, { username, password })
      .pipe(
        map(r => ({ accessToken: r.accessToken, username: r.username, displayName: r.displayName, roles: r.roles,
          branch: r.branch, expiresAt: Date.now() + r.expiresIn * 1000 })),
        tap(s => this.session.set(s)),
      );
  }

  logout(): void {
    this.session.set(null);
    this.router.navigate(['/login']);
  }

  /** Test helper and token refresh hook. */
  setSession(s: Session | null): void {
    this.session.set(s);
  }
}
