import type { DocumentType, PatientLookup } from '@/lib/api';
import { DOCUMENT_TYPE_LABELS } from '@/lib/formatters';

/** Tipos de documento aceptados por la API, en el orden en que se muestran. */
export const DOCUMENT_TYPES: DocumentType[] = ['DNI', 'CE', 'PAS'];

export const DOCUMENT_TYPE_OPTIONS = DOCUMENT_TYPES.map((value) => ({ value, label: DOCUMENT_TYPE_LABELS[value] }));

export function isDocumentType(value: string | null | undefined): value is DocumentType {
  return value === 'DNI' || value === 'CE' || value === 'PAS';
}

/** Longitud máxima del número según el tipo (DNI 8 dígitos; CE y pasaporte hasta 12 caracteres). */
export function documentMaxLength(type: DocumentType): number {
  return type === 'DNI' ? 8 : 12;
}

export function documentPlaceholder(type: DocumentType): string {
  if (type === 'DNI') return '8 dígitos, p. ej. 45871236';
  if (type === 'CE') return 'Número del carné, p. ej. 001234567';
  return 'Número del pasaporte, p. ej. AB123456';
}

/** Limpia lo que se escribe: el DNI solo admite dígitos; CE y pasaporte, letras y números en mayúscula. */
export function sanitizeDocumentNumber(type: DocumentType, raw: string): string {
  const cleaned = type === 'DNI' ? raw.replace(/\D/g, '') : raw.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  return cleaned.slice(0, documentMaxLength(type));
}

/** Validación local amable antes de consultar a la API. Devuelve el mensaje de error o null si está bien. */
export function validateDocumentNumber(type: DocumentType, value: string): string | null {
  const number = value.trim();
  if (number === '') return 'Ingresa el número de documento.';
  if (type === 'DNI' && !/^\d{8}$/.test(number)) return 'El DNI debe tener 8 dígitos.';
  if (type !== 'DNI' && number.length < 6) return 'El número debe tener al menos 6 caracteres.';
  return null;
}

/** Convierte los parámetros de la URL (?documentType&documentNumber) en una búsqueda válida, o null. */
export function lookupFromParams(params: URLSearchParams): PatientLookup | null {
  const documentType = params.get('documentType');
  const documentNumber = params.get('documentNumber')?.trim() ?? '';
  if (!isDocumentType(documentType) || documentNumber === '') return null;
  return { documentType, documentNumber };
}

/** Parámetros de URL que identifican al paciente por su documento (para que recargar conserve la búsqueda). */
export function lookupToParams(lookup: PatientLookup): Record<string, string> {
  return { documentType: lookup.documentType, documentNumber: lookup.documentNumber };
}
