import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { backendConfig } from './backend.config';
import {
  AdminFlightOfferSummary,
  AdminUserSummary,
  CreateFlightOfferValue,
  UpdateFlightOfferValue,
} from './admin.model';
import { BookingSummary } from './booking.model';
import { PagedResult } from './paged-result.model';

@Injectable({ providedIn: 'root' })
export class AdminService {
  constructor(private readonly http: HttpClient) {}

  listFlights(page = 0, size = 50): Observable<PagedResult<AdminFlightOfferSummary>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResult<AdminFlightOfferSummary>>(
      `${backendConfig.httpBase}/api/admin/flights`,
      { params },
    );
  }

  createFlight(value: CreateFlightOfferValue): Observable<AdminFlightOfferSummary> {
    return this.http.post<AdminFlightOfferSummary>(`${backendConfig.httpBase}/api/admin/flights`, value);
  }

  updateFlight(id: string, value: UpdateFlightOfferValue): Observable<AdminFlightOfferSummary> {
    return this.http.put<AdminFlightOfferSummary>(`${backendConfig.httpBase}/api/admin/flights/${id}`, value);
  }

  deleteFlight(id: string): Observable<void> {
    return this.http.delete<void>(`${backendConfig.httpBase}/api/admin/flights/${id}`);
  }

  listUsers(page = 0, size = 50): Observable<PagedResult<AdminUserSummary>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResult<AdminUserSummary>>(`${backendConfig.httpBase}/api/admin/users`, { params });
  }

  updateUserRoles(id: string, roles: string[]): Observable<AdminUserSummary> {
    return this.http.put<AdminUserSummary>(`${backendConfig.httpBase}/api/admin/users/${id}/roles`, { roles });
  }

  listAllBookings(page = 0, size = 50): Observable<PagedResult<BookingSummary>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResult<BookingSummary>>(`${backendConfig.httpBase}/api/admin/bookings`, { params });
  }
}
