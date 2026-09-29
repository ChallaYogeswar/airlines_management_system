import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { backendConfig } from './backend.config';

/**
 * Attaches the access token to every request bound for our own backend
 * (never to third-party requests - there are none from the browser today,
 * but this guards against accidentally leaking a token to one added
 * later). On a 401 from a non-auth backend endpoint, it triggers exactly
 * one shared refresh (see AuthService.refreshAccessToken) and retries the
 * original request once with the new token - it does not loop if the
 * retry also 401s, since that means the refresh token itself is no
 * longer valid and further retries won't help.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  const isBackendRequest = req.url.startsWith(backendConfig.httpBase);
  const isAuthEndpoint = req.url.includes('/api/auth/');

  const token = authService.accessToken();
  const authorizedReq =
    isBackendRequest && token && !isAuthEndpoint
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      const shouldAttemptRefresh =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        isBackendRequest &&
        !isAuthEndpoint;

      if (!shouldAttemptRefresh) {
        return throwError(() => error);
      }

      return authService.refreshAccessToken().pipe(
        switchMap((pair) => {
          const retried = req.clone({ setHeaders: { Authorization: `Bearer ${pair.accessToken}` } });
          return next(retried);
        }),
        catchError((refreshError) => throwError(() => refreshError)),
      );
    }),
  );
};
