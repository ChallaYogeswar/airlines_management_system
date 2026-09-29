export type SeatClass = 'ECONOMY' | 'BUSINESS';
export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED';

export interface SeatSummary {
  id: string;
  seatNumber: string;
  seatClass: SeatClass;
  status: SeatStatus;
  heldByMe: boolean;
}

export interface SeatHoldResponse {
  heldSeats: SeatSummary[];
  holdExpiresAt: string;
}
