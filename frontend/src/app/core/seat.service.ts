import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { backendConfig } from './backend.config';
import { SeatHoldResponse, SeatSummary } from './seat.model';

@Injectable({ providedIn: 'root' })
export class SeatService {
  constructor(private readonly http: HttpClient) {}

  list(flightOfferId: string): Observable<SeatSummary[]> {
    return this.http.get<SeatSummary[]>(`${backendConfig.httpBase}/api/flights/offers/${flightOfferId}/seats`);
  }

  hold(flightOfferId: string, seatIds: string[]): Observable<SeatHoldResponse> {
    return this.http.post<SeatHoldResponse>(
      `${backendConfig.httpBase}/api/flights/offers/${flightOfferId}/seats/hold`,
      { seatIds },
    );
  }

  release(flightOfferId: string, seatId: string): Observable<void> {
    return this.http.delete<void>(
      `${backendConfig.httpBase}/api/flights/offers/${flightOfferId}/seats/${seatId}/hold`,
    );
  }
}
