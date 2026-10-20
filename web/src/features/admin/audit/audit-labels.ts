import type { AuditAction, AuditEntity } from '@/lib/api';

/** Etiquetas en español de las entidades y acciones del registro de auditoría (contrato §7.5). */

export const AUDIT_ENTITY_LABELS: Record<AuditEntity, string> = {
  APPOINTMENT: 'Cita',
  APP_USER: 'Usuario',
  SPECIALTY: 'Especialidad',
  DOCTOR: 'Médico',
  SLOT: 'Cupo',
};

export const AUDIT_ENTITIES: AuditEntity[] = ['APPOINTMENT', 'APP_USER', 'SPECIALTY', 'DOCTOR', 'SLOT'];

/** Tipo de acción, que define el color del distintivo en la tabla. */
export type AuditActionKind = 'created' | 'changed' | 'removed' | 'login';

interface AuditActionMeta {
  label: string;
  entity: AuditEntity;
  kind: AuditActionKind;
}

export const AUDIT_ACTIONS: Record<AuditAction, AuditActionMeta> = {
  APPOINTMENT_BOOKED: { label: 'Cita reservada', entity: 'APPOINTMENT', kind: 'created' },
  APPOINTMENT_RESCHEDULED: { label: 'Cita reprogramada', entity: 'APPOINTMENT', kind: 'changed' },
  APPOINTMENT_CANCELLED: { label: 'Cita anulada', entity: 'APPOINTMENT', kind: 'removed' },
  ATTENDANCE_REGISTERED: { label: 'Asistencia registrada', entity: 'APPOINTMENT', kind: 'changed' },
  USER_LOGGED_IN: { label: 'Inicio de sesión', entity: 'APP_USER', kind: 'login' },
  USER_CREATED: { label: 'Usuario creado', entity: 'APP_USER', kind: 'created' },
  USER_UPDATED: { label: 'Usuario actualizado', entity: 'APP_USER', kind: 'changed' },
  USER_DEACTIVATED: { label: 'Usuario desactivado', entity: 'APP_USER', kind: 'removed' },
  USER_PASSWORD_CHANGED: { label: 'Contraseña cambiada', entity: 'APP_USER', kind: 'changed' },
  SPECIALTY_CREATED: { label: 'Especialidad creada', entity: 'SPECIALTY', kind: 'created' },
  SPECIALTY_UPDATED: { label: 'Especialidad actualizada', entity: 'SPECIALTY', kind: 'changed' },
  DOCTOR_CREATED: { label: 'Médico creado', entity: 'DOCTOR', kind: 'created' },
  DOCTOR_UPDATED: { label: 'Médico actualizado', entity: 'DOCTOR', kind: 'changed' },
  SLOTS_GENERATED: { label: 'Cupos generados', entity: 'SLOT', kind: 'created' },
  SLOT_BLOCKED: { label: 'Cupo bloqueado', entity: 'SLOT', kind: 'removed' },
  SLOT_UNBLOCKED: { label: 'Cupo desbloqueado', entity: 'SLOT', kind: 'changed' },
};

export const AUDIT_ACTION_CODES = Object.keys(AUDIT_ACTIONS) as AuditAction[];

export function isAuditEntity(value: string | null | undefined): value is AuditEntity {
  return value !== null && value !== undefined && value in AUDIT_ENTITY_LABELS;
}

export function isAuditAction(value: string | null | undefined): value is AuditAction {
  return value !== null && value !== undefined && value in AUDIT_ACTIONS;
}

/** Acciones que aplican a una entidad (o todas si no se indica). */
export function actionsForEntity(entity: AuditEntity | null): AuditAction[] {
  return AUDIT_ACTION_CODES.filter((action) => entity === null || AUDIT_ACTIONS[action].entity === entity);
}

/** Etiqueta legible de una acción; si llega un código desconocido se muestra tal cual. */
export function auditActionLabel(action: string): string {
  return isAuditAction(action) ? AUDIT_ACTIONS[action].label : action;
}

export function auditActionKind(action: string): AuditActionKind {
  return isAuditAction(action) ? AUDIT_ACTIONS[action].kind : 'changed';
}

export function auditEntityLabel(entity: string): string {
  return isAuditEntity(entity) ? AUDIT_ENTITY_LABELS[entity] : entity;
}
