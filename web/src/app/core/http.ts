import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { apiUrl } from './env';
import { Problem } from './models';

/** Adds the bearer token to API calls only (never to third-party URLs). */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token;
  if (token && req.url.startsWith(apiUrl())) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};

/** Turns RFC 7807 ProblemDetails into a readable message; 401 sends the user back to sign in. */
export const problemInterceptor: HttpInterceptorFn = (req, next) => {
  const snack = inject(MatSnackBar);
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !req.url.endsWith('/auth/login')) {
        auth.logout();
      } else {
        snack.open(problemMessage(err), 'Close', { duration: 6000 });
      }
      return throwError(() => err);
    }),
  );
};

export function problemMessage(err: HttpErrorResponse): string {
  const p = err.error as Problem | null;
  if (p?.errors?.length) return p.errors.map(e => `${e.field}: ${e.message}`).join('; ');
  if (p?.detail) return p.detail;
  if (err.status === 0) return 'Cannot reach the LendHub API.';
  return `Request failed (${err.status}).`;
}
