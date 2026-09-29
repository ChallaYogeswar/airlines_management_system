import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { backendConfig } from './backend.config';
import { PaymentRequest, PaymentResult } from './payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  constructor(private readonly http: HttpClient) {}

  pay(bookingId: string, request: PaymentRequest): Observable<PaymentResult> {
    return this.http.post<PaymentResult>(`${backendConfig.httpBase}/api/bookings/${bookingId}/pay`, request);
  }
}
