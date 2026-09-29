/**
 * South/central India - Chennai / Hyderabad / Bengaluru triangle.
 * Keep this in sync with ams.opensky.bounding-box in the backend's
 * application.yml - the backend uses it to query OpenSky, this uses it
 * to project lat/lon onto the radar display and to keep the fallback
 * simulation's aircraft inside the same visible region.
 */
export const RADAR_BOUNDING_BOX = {
  lamin: 8.0,
  lomin: 76.0,
  lamax: 19.5,
  lomax: 85.0,
};
