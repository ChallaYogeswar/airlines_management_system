import { BookingSummary } from './booking.model';

export interface PaymentRequest {
  cardholderName: string;
  cardNumber: string;
  expiryMonth: string;
  expiryYear: string;
  cvv: string;
}

export interface PaymentResult {
  success: boolean;
  declineReason: string | null;
  booking: BookingSummary;
}
