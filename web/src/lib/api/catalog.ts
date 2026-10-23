import { http } from '@/lib/api/client';
import type {
  AvailabilityQuery,
  AvailabilityResponse,
  Insurer,
  Location,
  Specialty,
} from '@/lib/api/types';

/**
 * Catálogos públicos y disponibilidad (secciones 2 y 3 del contrato).
 * No exigen token, pero si hay sesión se envía igual.
 */

/** GET /locations — sedes activas. */
export function listLocations(): Promise<Location[]> {
  return http.get<Location[]>('/locations');
}

/** GET /specialties?locationId — especialidades activas (opcionalmente de una sede). */
export function listSpecialties(locationId?: number | null): Promise<Specialty[]> {
  return http.get<Specialty[]>('/specialties', { locationId });
}

/** GET /insurers — aseguradoras con sus planes. */
export function listInsurers(): Promise<Insurer[]> {
  return http.get<Insurer[]>('/insurers');
}

/** GET /availability — cupos libres (rango máximo 14 días, desde la hora actual). */
export function getAvailability(query: AvailabilityQuery): Promise<AvailabilityResponse> {
  return http.get<AvailabilityResponse>('/availability', { ...query });
}

export const catalogApi = { listLocations, listSpecialties, listInsurers, getAvailability };
