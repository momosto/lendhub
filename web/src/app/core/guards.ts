import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { Role } from './models';

/** Route guard: signed in, and (if the route lists roles) holding one of them. The API enforces the same rules. */
export const roleGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.signedIn()) return router.parseUrl('/login');
  const roles = (route.data?.['roles'] ?? []) as Role[];
  return roles.length === 0 || auth.hasAnyRole(...roles) ? true : router.parseUrl('/');
};
