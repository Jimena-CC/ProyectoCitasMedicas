import { ApiError, getErrorMessage } from '@/lib/api/client';

export interface FormFailure {
  /** Errores por campo (400 INVALID_DATA o un 409 asociado a un campo). */
  fields: Record<string, string>;
  /** Mensaje general para mostrar en un Alert dentro del formulario. */
  message: string | null;
}

/**
 * Traduce un error de la API a errores de formulario.
 * `codeToField` asocia códigos de negocio (p. ej. USERNAME_TAKEN) con el campo donde mostrar el mensaje.
 */
export function toFormFailure(error: unknown, codeToField: Record<string, string> = {}): FormFailure {
  if (error instanceof ApiError) {
    if (error.isValidation) return { fields: error.fieldErrors(), message: null };
    const field = codeToField[error.code];
    if (field) return { fields: { [field]: error.message }, message: null };
    return { fields: {}, message: error.message };
  }
  return { fields: {}, message: getErrorMessage(error) };
}

/** Quita el error de un campo cuando el usuario vuelve a escribir en él. */
export function clearField(errors: Record<string, string>, field: string): Record<string, string> {
  if (!(field in errors)) return errors;
  const next = { ...errors };
  delete next[field];
  return next;
}
