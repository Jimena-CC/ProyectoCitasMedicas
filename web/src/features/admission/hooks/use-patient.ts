import * as React from 'react';
import { useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { ApiError, frontDeskApi, type Patient, type PatientLookup } from '@/lib/api';

import { lookupFromParams, lookupToParams } from '../lib/documents';
import { admissionKeys } from '../lib/patients';

/**
 * Búsqueda de paciente guardada en la URL (?documentType&documentNumber), para que recargar conserve el resultado.
 * `setLookup(null)` limpia los parámetros de búsqueda y conserva el resto.
 */
export function usePatientLookupParams(): { lookup: PatientLookup | null; setLookup: (lookup: PatientLookup | null) => void } {
  const [params, setParams] = useSearchParams();
  const lookup = React.useMemo(() => lookupFromParams(params), [params]);

  const setLookup = React.useCallback(
    (next: PatientLookup | null) => {
      setParams(
        (current) => {
          const updated = new URLSearchParams(current);
          updated.delete('documentType');
          updated.delete('documentNumber');
          if (next) Object.entries(lookupToParams(next)).forEach(([key, value]) => updated.set(key, value));
          return updated;
        },
        { replace: true },
      );
    },
    [setParams],
  );

  return { lookup, setLookup };
}

/** true cuando el error es el 404 PATIENT_NOT_FOUND de la búsqueda por documento. */
export function isPatientNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

/**
 * Busca al paciente por documento (GET /front-desk/patients) y deja el resultado en caché por id,
 * para que la ficha y el asistente de agendar lo reutilicen sin volver a pedirlo.
 */
export function usePatientSearch(lookup: PatientLookup | null): UseQueryResult<Patient, Error> {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: lookup ? admissionKeys.patientLookup(lookup) : ['admission', 'patient-lookup', 'none'],
    queryFn: () => frontDeskApi.searchPatient(lookup as PatientLookup),
    enabled: lookup !== null,
    retry: false,
    staleTime: 5 * 60_000,
  });

  const patient = query.data;
  React.useEffect(() => {
    if (patient) queryClient.setQueryData(admissionKeys.patient(patient.id), patient);
  }, [patient, queryClient]);

  return query;
}

/**
 * Paciente por id para la ficha y el asistente. La API no expone GET /front-desk/patients/{id},
 * así que se usa la copia en caché dejada por la búsqueda y, si no existe (recarga), se vuelve a buscar
 * por el documento que viaja en la URL (?documentType&documentNumber).
 */
export function usePatient(patientId: number | null, lookup: PatientLookup | null): UseQueryResult<Patient, Error> {
  return useQuery({
    queryKey: admissionKeys.patient(patientId ?? 0),
    queryFn: async () => {
      if (!lookup) {
        // Enlace copiado o recortado sin el documento: el paciente puede existir, solo falta con qué buscarlo.
        throw new ApiError(404, 'PATIENT_NOT_FOUND', 'Este enlace no incluye el documento del paciente. Abre la ficha desde la búsqueda por documento.');
      }
      const patient = await frontDeskApi.searchPatient(lookup);
      if (patient.id !== patientId) {
        throw new ApiError(404, 'PATIENT_NOT_FOUND', 'El documento no corresponde a este paciente. Búscalo nuevamente.');
      }
      return patient;
    },
    enabled: patientId !== null,
    retry: false,
    staleTime: 5 * 60_000,
  });
}

/** Actualiza la copia en caché del paciente (p. ej. tras cambiar su seguro). */
export function useUpdatePatientCache() {
  const queryClient = useQueryClient();
  return React.useCallback(
    (patient: Patient) => {
      queryClient.setQueryData(admissionKeys.patient(patient.id), patient);
      queryClient.setQueryData(admissionKeys.patientLookup(patient), patient);
    },
    [queryClient],
  );
}
