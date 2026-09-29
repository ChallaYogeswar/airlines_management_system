export interface AdminFlightOfferSummary {
  id: string;
  flightNumber: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  price: number;
  totalSeats: number;
  seatsAvailable: number;
}

export interface CreateFlightOfferValue {
  flightNumber: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  price: number;
  totalSeats: number;
}

export interface UpdateFlightOfferValue {
  price: number;
  totalSeats: number;
  departureTime: string;
  arrivalTime: string;
}

export interface AdminUserSummary {
  id: string;
  email: string;
  name: string;
  roles: string[];
  active: boolean;
  mfaEnabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}
