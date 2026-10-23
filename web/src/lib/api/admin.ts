import { http } from '@/lib/api/client';
import type {
  AuditEntry,
  AuditQuery,
  CreateUserRequest,
  DoctorDetail,
  DoctorRequest,
  LocationDetail,
  PageResponse,
  PasswordChangeRequest,
  SlotDetail,
  SlotGenerationRequest,
  SlotGenerationResult,
  SlotQuery,
  SpecialtyDetail,
  SpecialtyRequest,
  UpdateUserRequest,
  UserSummary,
} from '@/lib/api/types';

/** Administración (sección 9). Solo rol ADMIN. */

// 9.1 Usuarios -------------------------------------------------------------

/** GET /admin/users — ordenados por username. */
export function listUsers(): Promise<UserSummary[]> {
  return http.get<UserSummary[]>('/admin/users');
}

/** GET /admin/users/{id} — 404 "El usuario no existe." */
export function getUser(userId: number): Promise<UserSummary> {
  return http.get<UserSummary>(`/admin/users/${userId}`);
}

/** POST /admin/users — 409 USERNAME_TAKEN · 409 DOCTOR_ALREADY_LINKED · 404 médico. */
export function createUser(body: CreateUserRequest): Promise<UserSummary> {
  return http.post<UserSummary>('/admin/users', body);
}

/** PUT /admin/users/{id} — rol, médico y estado (los tres obligatorios); 422 SELF_DEACTIVATION. */
export function updateUser(userId: number, body: UpdateUserRequest): Promise<UserSummary> {
  return http.put<UserSummary>(`/admin/users/${userId}`, body);
}

/** PUT /admin/users/{id}/password — 204 sin cuerpo. */
export function changeUserPassword(userId: number, body: PasswordChangeRequest): Promise<void> {
  return http.put<void>(`/admin/users/${userId}/password`, body);
}

// 9.2 Sedes y especialidades ----------------------------------------------

/** GET /admin/locations — incluye inactivas. */
export function listLocations(): Promise<LocationDetail[]> {
  return http.get<LocationDetail[]>('/admin/locations');
}

/** GET /admin/specialties — activas e inactivas con sus sedes. */
export function listSpecialties(): Promise<SpecialtyDetail[]> {
  return http.get<SpecialtyDetail[]>('/admin/specialties');
}

/** POST /admin/specialties — 409 SPECIALTY_NAME_TAKEN. */
export function createSpecialty(body: SpecialtyRequest): Promise<SpecialtyDetail> {
  return http.post<SpecialtyDetail>('/admin/specialties', body);
}

/** PUT /admin/specialties/{id} — las sedes fuera de locationIds dejan de ofrecer la especialidad. */
export function updateSpecialty(specialtyId: number, body: SpecialtyRequest): Promise<SpecialtyDetail> {
  return http.put<SpecialtyDetail>(`/admin/specialties/${specialtyId}`, body);
}

// 9.3 Médicos ----------------------------------------------------------------

/** GET /admin/doctors — activos e inactivos, ordenados por apellidos. */
export function listDoctors(): Promise<DoctorDetail[]> {
  return http.get<DoctorDetail[]>('/admin/doctors');
}

/** POST /admin/doctors — 409 DOCTOR_LICENSE_TAKEN. */
export function createDoctor(body: DoctorRequest): Promise<DoctorDetail> {
  return http.post<DoctorDetail>('/admin/doctors', body);
}

/** PUT /admin/doctors/{id} — active omitido = se mantiene. */
export function updateDoctor(doctorId: number, body: DoctorRequest): Promise<DoctorDetail> {
  return http.put<DoctorDetail>(`/admin/doctors/${doctorId}`, body);
}

// 9.4 Agendas ----------------------------------------------------------------

/** GET /admin/slots?doctorId&from&to — rango máximo 92 días. */
export function listSlots(query: SlotQuery): Promise<SlotDetail[]> {
  return http.get<SlotDetail[]>('/admin/slots', { ...query });
}

/** POST /admin/slots/generate — idempotente: los horarios ya existentes cuentan en skipped. */
export function generateSlots(body: SlotGenerationRequest): Promise<SlotGenerationResult> {
  return http.post<SlotGenerationResult>('/admin/slots/generate', body);
}

/** PATCH /admin/slots/{id}/block — 409 SLOT_NOT_FREE. */
export function blockSlot(slotId: number): Promise<SlotDetail> {
  return http.patch<SlotDetail>(`/admin/slots/${slotId}/block`);
}

/** PATCH /admin/slots/{id}/unblock — 409 SLOT_NOT_BLOCKED. */
export function unblockSlot(slotId: number): Promise<SlotDetail> {
  return http.patch<SlotDetail>(`/admin/slots/${slotId}/unblock`);
}

// 9.5 Auditoría --------------------------------------------------------------

/** GET /admin/audit — paginado, más reciente primero; size entre 1 y 100. */
export function searchAudit(query: AuditQuery = {}): Promise<PageResponse<AuditEntry>> {
  return http.get<PageResponse<AuditEntry>>('/admin/audit', { ...query });
}

export const adminApi = {
  listUsers,
  getUser,
  createUser,
  updateUser,
  changeUserPassword,
  listLocations,
  listSpecialties,
  createSpecialty,
  updateSpecialty,
  listDoctors,
  createDoctor,
  updateDoctor,
  listSlots,
  generateSlots,
  blockSlot,
  unblockSlot,
  searchAudit,
};
