import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { FlightOfferSummary } from './booking.model';
import { backendConfig } from './backend.config';

@Injectable({ providedIn: 'root' })
export class FlightSearchService {
  constructor(private readonly http: HttpClient) {}

  search(origin: string, destination: string, date: string): Observable<FlightOfferSummary[]> {
    const params = new HttpParams().set('origin', origin).set('destination', destination).set('date', date);
    return this.http.get<FlightOfferSummary[]>(`${backendConfig.httpBase}/api/flights/search`, { params });
  }

  getById(id: string): Observable<FlightOfferSummary> {
    return this.http.get<FlightOfferSummary>(`${backendConfig.httpBase}/api/flights/offers/${id}`);
  }
}
