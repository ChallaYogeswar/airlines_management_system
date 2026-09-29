export interface AircraftPosition {
  icao24: string;
  callsign: string | null;
  originCountry: string | null;
  longitude: number | null;
  latitude: number | null;
  baroAltitudeM: number | null;
  onGround: boolean | null;
  velocityMs: number | null;
  trueTrackDeg: number | null;
  verticalRateMs: number | null;
}
