import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable, catchError, filter, finalize, map, shareReplay, take, tap, throwError } from 'rxjs';
import { LoginResult, TokenPair, UserSummary } from './auth.model';
import { backendConfig } from './backend.config';

const REFRESH_TOKEN_KEY = 'ams_refresh_token';
const USER_KEY = 'ams_user';

/**
 * Access tokens live in memory only (a signal, never localStorage) -
 * short-lived and never worth persisting. Refresh tokens go in
 * localStorage so a page reload doesn't force a re-login; on startup,
 * bootstrap() silently exchanges a stored refresh token for a fresh
 * access token before the app treats the user as logged in.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly currentUser = signal<UserSummary | null>(this.readStoredUser());
  readonly accessToken = signal<string | null>(null);
  readonly isAuthenticated = computed(() => this.currentUser() !== null && this.accessToken() !== null);

  /** True until the startup silent-refresh attempt resolves one way or
   * the other. Guards wait on this so a valid session isn't bounced to
   * /login just because the refresh call hasn't come back yet. */
  private readonly bootstrapping = signal(true);
  private readonly bootstrapping$ = toObservable(this.bootstrapping);

  private refreshInFlight: Observable<TokenPair> | null = null;

  constructor(private readonly http: HttpClient) {
    this.bootstrap();
  }

  waitUntilReady(): Observable<void> {
    return this.bootstrapping$.pipe(
      filter((b) => !b),
      take(1),
      map(() => void 0),
    );
  }

  login(email: string, password: string, deviceName: string): Observable<LoginResult> {
    return this.http
      .post<LoginResult>(`${backendConfig.httpBase}/api/auth/login`, { email, password, deviceName })
      .pipe(tap((result) => this.handleLoginResult(result)));
  }

  verifyMfa(mfaChallenge: string, code: string): Observable<LoginResult> {
    return this.http
      .post<LoginResult>(`${backendConfig.httpBase}/api/auth/mfa/verify`, { mfaChallenge, code })
      .pipe(tap((result) => this.handleLoginResult(result)));
  }

  register(email: string, name: string, password: string): Observable<UserSummary> {
    return this.http.post<UserSummary>(`${backendConfig.httpBase}/api/auth/register`, {
      email,
      name,
      password,
    });
  }

  logout(): void {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (refreshToken) {
      // best-effort - the session is cleared client-side regardless of
      // whether this reaches the server
      this.http
        .post(`${backendConfig.httpBase}/api/auth/logout`, { refreshToken })
        .subscribe({ error: () => {} });
    }
    this.clearSession();
  }

  hasRole(role: string): boolean {
    return this.currentUser()?.roles.includes(role) ?? false;
  }

  /** Used by the HTTP interceptor when a request 401s. Concurrent 401s
   * share this single in-flight call via shareReplay rather than each
   * firing their own refresh request. */
  refreshAccessToken(): Observable<TokenPair> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      this.clearSession();
      return throwError(() => new Error('No refresh token available'));
    }
    this.refreshInFlight = this.doRefresh(refreshToken).pipe(
      finalize(() => (this.refreshInFlight = null)),
      shareReplay(1),
    );
    return this.refreshInFlight;
  }

  private bootstrap(): void {
    const storedRefresh = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!storedRefresh) {
      this.bootstrapping.set(false);
      return;
    }
    this.doRefresh(storedRefresh).subscribe({
      next: () => this.bootstrapping.set(false),
      error: () => {
        this.clearSession();
        this.bootstrapping.set(false);
      },
    });
  }

  private doRefresh(refreshToken: string): Observable<TokenPair> {
    return this.http.post<TokenPair>(`${backendConfig.httpBase}/api/auth/refresh`, { refreshToken }).pipe(
      tap((pair) => {
        this.accessToken.set(pair.accessToken);
        localStorage.setItem(REFRESH_TOKEN_KEY, pair.refreshToken);
      }),
      catchError((err) => {
        this.clearSession();
        return throwError(() => err);
      }),
    );
  }

  private handleLoginResult(result: LoginResult): void {
    if (result.requiresMfa) return; // caller shows the MFA step; nothing to persist yet
    this.currentUser.set(result.user);
    this.accessToken.set(result.accessToken);
    if (result.refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, result.refreshToken);
    }
    localStorage.setItem(USER_KEY, JSON.stringify(result.user));
  }

  private clearSession(): void {
    this.currentUser.set(null);
    this.accessToken.set(null);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  private readStoredUser(): UserSummary | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as UserSummary) : null;
    } catch {
      return null;
    }
  }
}
