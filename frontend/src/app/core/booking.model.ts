export interface FlightOfferSummary {
  id: string;
  flightNumber: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  price: number;
  seatsAvailable: number;
}

export interface PassengerBookingDetail {
  firstName: string;
  lastName: string;
  dateOfBirth: string; // yyyy-MM-dd
  seatId: string;
}

export interface CreateBookingRequest {
  flightOfferId: string;
  passengers: PassengerBookingDetail[];
}

export type BookingStatus = 'PENDING_PAYMENT' | 'PAYMENT_FAILED' | 'CONFIRMED' | 'CANCELLED';

export interface BookedPassenger {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  seatNumber: string;
}

export interface BookingSummary {
  id: string;
  bookingReference: string;
  flightNumber: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  status: BookingStatus;
  totalPrice: number;
  passengers: BookedPassenger[];
  createdAt: string;
}
