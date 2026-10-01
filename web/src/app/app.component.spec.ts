import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { ApiService } from './core/api.service';
import { AuthService } from './core/auth.service';
import { roleGuard } from './core/guards';
import { problemMessage } from './core/http';
import { MoneyPipe, StatusClassPipe } from './core/shared';

describe('LendHub web core', () => {
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    window.__env = { apiUrl: 'http://api.test' };
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  it('signs in, keeps the session in memory and exposes roles', () => {
    auth.login('manager@lendhub.demo', 'Demo123!').subscribe();
    const req = http.expectOne('http://api.test/api/v1/auth/login');
    expect(req.request.body).toEqual({ username: 'manager@lendhub.demo', password: 'Demo123!' });
    req.flush({ accessToken: 't', username: 'manager@lendhub.demo', displayName: 'Chipo', roles: ['MANAGER'], branch: 'MBARE', expiresIn: 3600 });

    expect(auth.signedIn()).toBeTrue();
    expect(auth.token).toBe('t');
    expect(auth.hasAnyRole('MANAGER', 'COMMITTEE')).toBeTrue();
    expect(auth.hasAnyRole('FINANCE')).toBeFalse();
    expect(localStorage.length === 0 || !Object.values(localStorage).includes('t')).toBeTrue();
  });

  it('treats an expired session as signed out', () => {
    auth.setSession({ accessToken: 'old', username: 'x', displayName: 'x', roles: ['OFFICER'], expiresAt: Date.now() - 1 });
    expect(auth.signedIn()).toBeFalse();
    expect(auth.token).toBeNull();
  });

  it('guards routes by role', () => {
    const router = TestBed.inject(Router);
    const route = (roles: string[]) => ({ data: { roles } }) as unknown as ActivatedRouteSnapshot;
    const run = (roles: string[]) => TestBed.runInInjectionContext(() => roleGuard(route(roles), {} as RouterStateSnapshot));

    expect(router.serializeUrl(run(['FINANCE']) as UrlTree)).toBe('/login');
    auth.setSession({ accessToken: 't', username: 'x', displayName: 'x', roles: ['OFFICER'], expiresAt: Date.now() + 60_000 });
    expect(run(['OFFICER'])).toBeTrue();
    expect(router.serializeUrl(run(['FINANCE']) as UrlTree)).toBe('/');
    expect(run([])).toBeTrue();
  });

  it('sends repayment requests with an Idempotency-Key', () => {
    TestBed.inject(ApiService).requestEcoCash('loan-1', 57.5).subscribe();
    const req = http.expectOne('http://api.test/api/v1/loans/loan-1/repayment-requests');
    expect(req.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(req.request.body).toEqual({ amount: 57.5 });
    req.flush({});
  });

  it('formats money with its currency and maps statuses to colours', () => {
    const money = new MoneyPipe();
    expect(money.transform(1234.5, 'USD')).toBe('USD 1,234.50');
    expect(money.transform(null)).toBe('—');
    const status = new StatusClassPipe();
    expect(status.transform('IN_ARREARS')).toBe('bad');
    expect(status.transform('ACTIVE')).toBe('ok');
  });

  it('turns ProblemDetails into readable messages', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 403, error: { title: 'approval-limit', detail: 'Above the limit' } })))
      .toBe('Above the limit');
    expect(problemMessage(new HttpErrorResponse({ status: 400, error: { errors: [{ field: 'amount', message: 'must be ≥ 1' }] } })))
      .toBe('amount: must be ≥ 1');
    expect(problemMessage(new HttpErrorResponse({ status: 0 }))).toContain('Cannot reach');
  });
});
