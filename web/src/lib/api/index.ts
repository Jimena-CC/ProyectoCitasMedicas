/**
 * Punto de entrada del cliente de la API.
 *
 *   import { frontDeskApi, ApiError } from '@/lib/api';
 *   import type { Patient } from '@/lib/api';
 */
export * from '@/lib/api/client';
export * from '@/lib/api/types';
export { authApi } from '@/lib/api/auth';
export { catalogApi } from '@/lib/api/catalog';
export { doctorApi } from '@/lib/api/doctor';
export { frontDeskApi } from '@/lib/api/front-desk';
export { adminApi } from '@/lib/api/admin';
export { reportsApi } from '@/lib/api/reports';
