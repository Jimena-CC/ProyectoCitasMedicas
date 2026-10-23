/**
 * Tipos fieles al contrato REST de la API (docs/api-contract.md, secciones 1 a 10).
 *
 * Convenciones de la API:
 * - fechas "yyyy-MM-dd" (IsoDate), horas "HH:mm" (IsoTime), instantes "yyyy-MM-dd'T'HH:mm:ssXXX" (IsoDateTime).
 * - Los mensajes (`message`) ya vienen en español y se muestran tal cual.
 */

/** "2026-10-20" */
export type IsoDate = string;
/** "09:00" */
export type IsoTime = string;
/** "2026-10-20T09:25:10-05:00" */
export type IsoDateTime = string;

// ---------------------------------------------------------------------------
// Enumeraciones
// ---------------------------------------------------------------------------

export type UserRole = 'ADMIN' | 'FRONT_DESK' | 'DOCTOR';
export type DocumentType = 'DNI' | 'CE' | 'PAS';
export type Sex = 'M' | 'F';
export type AppointmentStatus = 'BOOKED' | 'ATTENDED' | 'NO_SHOW' | 'CANCELLED';
export type AttendanceStatus = 'ATTENDED' | 'NO_SHOW';
export type BookingChannel = 'KIOSK' | 'FRONT_DESK';
export type CoverageStatus = 'ACTIVE' | 'SUSPENDED' | 'EXPIRED';
export type SlotStatus = 'FREE' | 'BOOKED' | 'BLOCKED';
export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
export type AuditEntity = 'APPOINTMENT' | 'APP_USER' | 'SPECIALTY' | 'DOCTOR' | 'SLOT';
export type AuditAction =
  | 'APPOINTMENT_BOOKED'
  | 'APPOINTMENT_RESCHEDULED'
  | 'APPOINTMENT_CANCELLED'
  | 'ATTENDANCE_REGISTERED'
  | 'USER_LOGGED_IN'
  | 'USER_CREATED'
  | 'USER_UPDATED'
  | 'USER_DEACTIVATED'
  | 'USER_PASSWORD_CHANGED'
  | 'SPECIALTY_CREATED'
  | 'SPECIALTY_UPDATED'
  | 'DOCTOR_CREATED'
  | 'DOCTOR_UPDATED'
  | 'SLOTS_GENERATED'
  | 'SLOT_BLOCKED'
  | 'SLOT_UNBLOCKED';
export type ReportKind = 'appointments' | 'no-show-rate' | 'occupancy';
export type NotificationType = 'CONFIRMATION' | 'REMINDER';
export type NotificationChannel = 'EMAIL' | 'SMS';
export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED' | 'CANCELLED';

// ---------------------------------------------------------------------------
// Errores (sección 5)
// ---------------------------------------------------------------------------

export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
  fields: FieldError[] | null;
  timestamp: IsoDateTime;
}

// ---------------------------------------------------------------------------
// Autenticación (sección 6)
// ---------------------------------------------------------------------------

export interface LoginRequest {
  username: string;
  password: string;
}

export interface UserInfo {
  id: number;
  username: string;
  role: UserRole;
  /** Nombre del médico vinculado o el username. */
  fullName: string;
  doctorId: number | null;
}

export interface LoginResponse {
  token: string;
  expiresAt: IsoDateTime;
  user: UserInfo;
}

// ---------------------------------------------------------------------------
// Catálogos públicos y disponibilidad (secciones 2 y 3)
// ---------------------------------------------------------------------------

export interface InsurerPlan {
  id: number;
  name: string;
  consultationCopay: number;
}

export interface Insurer {
  id: number;
  name: string;
  plans: InsurerPlan[];
}

export interface Location {
  id: number;
  code: string;
  name: string;
  address: string;
  district: string;
}

export interface Specialty {
  id: number;
  name: string;
  description: string | null;
  appointmentMinutes: number;
}

export interface AvailabilityDoctor {
  id: number;
  fullName: string;
  /** Número de colegiatura (CMP). */
  license: string;
}

export interface AvailabilitySlot {
  slotId: number;
  startTime: IsoTime;
  endTime: IsoTime;
  room: string | null;
  doctor: AvailabilityDoctor;
}

export interface AvailabilityDay {
  date: IsoDate;
  freeSlots: number;
  slots: AvailabilitySlot[];
}

export interface AvailabilityResponse {
  days: AvailabilityDay[];
}

export interface AvailabilityQuery {
  specialtyId: number;
  locationId: number;
  from: IsoDate;
  /** Máximo 14 días después de `from`. */
  to: IsoDate;
}

// ---------------------------------------------------------------------------
// Pacientes y seguros (secciones 1 y 8)
// ---------------------------------------------------------------------------

export interface PatientInsurance {
  patientInsuranceId: number;
  insurer: string;
  plan: string;
  policyNumber: string | null;
  coverageStatus: CoverageStatus;
}

export interface Patient {
  id: number;
  documentType: DocumentType;
  documentNumber: string;
  firstNames: string;
  lastNames: string;
  birthDate: IsoDate;
  phone: string;
  email: string;
  /** null = atención particular. Solo muestra la cobertura vigente. */
  insurance: PatientInsurance | null;
}

export interface PatientLookup {
  documentType: DocumentType;
  documentNumber: string;
}

export interface PatientRequest {
  documentType: DocumentType;
  documentNumber: string;
  firstNames: string;
  lastNames: string;
  birthDate: IsoDate;
  sex?: Sex | null;
  /** 9 dígitos que empiezan en 9. */
  phone: string;
  email: string;
  acceptsNotifications?: boolean;
}

export interface InsuranceRequest {
  /** null u omitido = el paciente queda como particular. */
  planId?: number | null;
  policyNumber?: string | null;
}

export interface CoverageValidation {
  patientInsuranceId: number;
  insurer: string;
  plan: string;
  policyNumber: string | null;
  coverageStatus: CoverageStatus;
  validUntil: IsoDate | null;
  valid: boolean;
}

export interface InsuranceValidationResponse {
  patientId: number;
  valid: boolean;
  message: string;
  coverages: CoverageValidation[];
}

// ---------------------------------------------------------------------------
// Citas (secciones 4 y 8)
// ---------------------------------------------------------------------------

export interface Appointment {
  id: number;
  code: string;
  status: AppointmentStatus;
  date: IsoDate;
  startTime: IsoTime;
  endTime: IsoTime;
  specialty: string;
  location: string;
  room: string | null;
  doctor: string;
  /** "Rímac Seguros · Red Médica" o "Particular". */
  coverage: string;
  visitReason: string;
  rescheduleCount: number;
  bookedAt: IsoDateTime;
}

export interface AppointmentRequest {
  patientId: number;
  slotId: number;
  /** null = particular. */
  patientInsuranceId: number | null;
  visitReason: string;
}

export interface RescheduleRequest {
  newSlotId: number;
}

export interface CancellationRequest {
  /** Obligatorio en ventanilla (≤ 200). */
  reason: string;
}

export interface AppointmentNotification {
  type: NotificationType;
  channel: NotificationChannel;
  status: NotificationStatus;
  scheduledFor: IsoDateTime;
  sentAt: IsoDateTime | null;
  attempts: number;
}

// ---------------------------------------------------------------------------
// Médico (sección 7)
// ---------------------------------------------------------------------------

export interface AgendaDoctor {
  id: number;
  fullName: string;
  specialty: string;
}

export interface AgendaPatient {
  id: number;
  fullName: string;
  documentType: DocumentType;
  documentNumber: string;
  phone: string;
}

export interface AgendaSummary {
  total: number;
  booked: number;
  attended: number;
  noShow: number;
  cancelled: number;
}

export interface AgendaItem {
  appointmentId: number;
  code: string;
  startTime: IsoTime;
  endTime: IsoTime;
  room: string | null;
  location: string;
  patient: AgendaPatient;
  visitReason: string;
  /** "Rímac Seguros · Red Médica" o "Particular". */
  insurance: string;
  status: AppointmentStatus;
  bookingChannel: BookingChannel;
  /** Solo con valor cuando status es ATTENDED o NO_SHOW. */
  attendanceRegisteredAt: IsoDateTime | null;
}

export interface AgendaResponse {
  date: IsoDate;
  doctor: AgendaDoctor;
  summary: AgendaSummary;
  items: AgendaItem[];
}

export interface AttendanceRequest {
  status: AttendanceStatus;
  /** Opcional, ≤ 250 caracteres. */
  note?: string | null;
}

// ---------------------------------------------------------------------------
// Administración (sección 9)
// ---------------------------------------------------------------------------

export interface UserSummary {
  id: number;
  username: string;
  role: UserRole;
  fullName: string;
  doctorId: number | null;
  active: boolean;
  lastAccess: IsoDateTime | null;
}

export interface CreateUserRequest {
  /** 3-50 caracteres: letras, números, punto, guion y guion bajo. */
  username: string;
  /** 8-72 caracteres. */
  password: string;
  role: UserRole;
  /** Obligatorio solo con role DOCTOR. */
  doctorId: number | null;
}

export interface UpdateUserRequest {
  role: UserRole;
  doctorId: number | null;
  active: boolean;
}

export interface PasswordChangeRequest {
  password: string;
}

export interface LocationDetail {
  id: number;
  code: string;
  name: string;
  address: string;
  district: string;
  phone: string | null;
  active: boolean;
}

export interface LocationRef {
  id: number;
  code: string;
  name: string;
}

export interface SpecialtyDetail {
  id: number;
  name: string;
  description: string | null;
  appointmentMinutes: number;
  active: boolean;
  locations: LocationRef[];
}

export interface SpecialtyRequest {
  name: string;
  description?: string | null;
  /** 5 a 240 minutos. */
  appointmentMinutes: number;
  /** Obligatorio; puede ser []. */
  locationIds: number[];
  /** Se ignora en el alta; en la edición, omitido = se mantiene. */
  active?: boolean | null;
}

export interface SpecialtyRef {
  id: number;
  name: string;
}

export interface DoctorDetail {
  id: number;
  license: string;
  specialistRegistry: string | null;
  firstNames: string;
  lastNames: string;
  fullName: string;
  specialty: SpecialtyRef;
  active: boolean;
}

export interface DoctorRequest {
  license: string;
  specialistRegistry?: string | null;
  firstNames: string;
  lastNames: string;
  specialtyId: number;
  active?: boolean | null;
}

export interface DoctorRef {
  id: number;
  fullName: string;
}

export interface SlotLocationRef {
  id: number;
  name: string;
}

export interface SlotDetail {
  id: number;
  date: IsoDate;
  startTime: IsoTime;
  endTime: IsoTime;
  room: string | null;
  status: SlotStatus;
  doctor: DoctorRef;
  location: SlotLocationRef;
}

export interface SlotQuery {
  doctorId: number;
  from: IsoDate;
  /** Máximo 92 días después de `from`. */
  to: IsoDate;
}

export interface SlotGenerationRequest {
  doctorId: number;
  locationId: number;
  room?: string | null;
  from: IsoDate;
  to: IsoDate;
  startTime: IsoTime;
  endTime: IsoTime;
  /** Por defecto lunes a sábado. */
  weekdays?: Weekday[] | null;
  /** Por defecto la duración de la especialidad del médico. */
  durationMinutes?: number | null;
}

export interface SlotGenerationResult {
  created: number;
  skipped: number;
  /** Solo los creados, todos FREE. */
  slots: SlotDetail[];
}

export interface AuditUser {
  id: number;
  username: string;
  fullName: string;
}

export interface AuditEntry {
  id: number;
  registeredAt: IsoDateTime;
  /** null cuando actuó el paciente desde el kiosko. */
  user: AuditUser | null;
  action: AuditAction | string;
  entity: AuditEntity | string;
  entityId: number | null;
  detail: string;
  sourceIp: string | null;
}

export interface AuditQuery {
  entity?: AuditEntity | null;
  action?: AuditAction | null;
  userId?: number | null;
  from?: IsoDate | null;
  to?: IsoDate | null;
  /** 0 o mayor. */
  page?: number;
  /** 1 a 100. */
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------------------------------------------------------------------------
// Reportes (sección 10)
// ---------------------------------------------------------------------------

export interface ReportRange {
  from: IsoDate;
  /** Máximo 366 días después de `from`. */
  to: IsoDate;
}

export interface AppointmentsReportQuery extends ReportRange {
  specialtyId?: number | null;
  locationId?: number | null;
}

export interface StatusTotals {
  booked: number;
  attended: number;
  cancelled: number;
  noShow: number;
  total: number;
}

export interface SpecialtyStatusRow {
  specialtyId: number;
  specialtyName: string;
  totals: StatusTotals;
}

export interface LocationStatusRow {
  locationId: number;
  locationName: string;
  totals: StatusTotals;
}

export interface AppointmentsReport {
  from: IsoDate;
  to: IsoDate;
  totals: StatusTotals;
  bySpecialty: SpecialtyStatusRow[];
  byLocation: LocationStatusRow[];
}

export interface NoShowRow {
  specialtyId: number;
  specialtyName: string;
  attended: number;
  noShow: number;
  /** 0 a 1 con 4 decimales. */
  rate: number;
}

export interface NoShowTotals {
  attended: number;
  noShow: number;
  rate: number;
}

export interface NoShowRateReport {
  from: IsoDate;
  to: IsoDate;
  bySpecialty: NoShowRow[];
  total: NoShowTotals;
}

export interface OccupancyRow {
  specialtyId: number;
  specialtyName: string;
  locationId: number;
  locationName: string;
  slotsTotal: number;
  slotsBlocked: number;
  slotsBooked: number;
  rate: number;
}

export interface OccupancyTotals {
  slotsTotal: number;
  slotsBlocked: number;
  slotsBooked: number;
  rate: number;
}

export interface OccupancyReport {
  from: IsoDate;
  to: IsoDate;
  rows: OccupancyRow[];
  total: OccupancyTotals;
}
