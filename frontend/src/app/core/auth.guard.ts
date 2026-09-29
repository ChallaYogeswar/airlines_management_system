import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/** Protects authenticated-only routes. */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.waitUntilReady().pipe(
    map(() => (authService.isAuthenticated() ? true : router.createUrlTree(['/login']))),
  );
};

/** Keeps already-logged-in users off /login and /register. */
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.waitUntilReady().pipe(
    map(() => (authService.isAuthenticated() ? router.createUrlTree(['/']) : true)),
  );
};

/** Protects admin-only routes. Requires authGuard's job (being logged
 * in) to already be satisfied - this only adds the role check on top,
 * redirecting non-admins to the home page rather than /login (they ARE
 * logged in, just not authorized for this section). */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.waitUntilReady().pipe(
    map(() => {
      if (!authService.isAuthenticated()) return router.createUrlTree(['/login']);
      return authService.hasRole('ADMIN') ? true : router.createUrlTree(['/']);
    }),
  );
};
