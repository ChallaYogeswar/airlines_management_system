import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BookingSummary, CreateBookingRequest } from './booking.model';
import { backendConfig } from './backend.config';

@Injectable({ providedIn: 'root' })
export class BookingService {
  constructor(private readonly http: HttpClient) {}

  create(request: CreateBookingRequest): Observable<BookingSummary> {
    return this.http.post<BookingSummary>(`${backendConfig.httpBase}/api/bookings`, request);
  }

  listMine(): Observable<BookingSummary[]> {
    return this.http.get<BookingSummary[]>(`${backendConfig.httpBase}/api/bookings`);
  }

  cancel(id: string): Observable<void> {
    return this.http.delete<void>(`${backendConfig.httpBase}/api/bookings/${id}`);
  }
}
