export type FlightStatus = 'on-time' | 'delayed' | 'boarding' | 'on-runway' | 'departed';

export interface Flight {
  id: string;
  time: string;
  flightNumber: string;
  destination: string;
  gate: string;
  status: FlightStatus;
  delayMinutes?: number;
  /** bumped on every status change so the template can key a flap-in animation off it */
  updatedAt: number;
}

export const STATUS_META: Record<FlightStatus, { label: string; colorVar: string; bgVar: string }> = {
  'on-time': { label: 'On time', colorVar: '--green', bgVar: '--green-bg' },
  delayed: { label: 'Delayed', colorVar: '--red', bgVar: '--red-bg' },
  boarding: { label: 'Boarding', colorVar: '--amber', bgVar: '--amber-bg' },
  'on-runway': { label: 'On runway', colorVar: '--cyan', bgVar: '--cyan-bg' },
  departed: { label: 'Departed', colorVar: '--red', bgVar: '--red-bg' },
};

/** Legal next states for each status - the engine will only ever move a
 * flight forward along a path that matches how a real flight actually
 * progresses, plus the chance of being pulled into "delayed" from any
 * pre-departure state. */
export const NEXT_STATES: Record<FlightStatus, FlightStatus[]> = {
  'on-time': ['boarding', 'delayed'],
  delayed: ['boarding'],
  boarding: ['on-runway'],
  'on-runway': ['departed'],
  departed: [],
};
