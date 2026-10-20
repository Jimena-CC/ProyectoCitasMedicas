import * as React from 'react';
import { Link } from 'react-router';
import { RiErrorWarningLine, RiUserAddLine, RiUserSearchLine } from '@remixicon/react';

import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { LoadingState } from '@/components/loading-state';
import * as Button from '@/components/ui/button';
import { getErrorMessage, type Patient, type PatientLookup } from '@/lib/api';
import { formatDocument } from '@/lib/formatters';

import { isPatientNotFound, usePatientSearch } from '../hooks/use-patient';
import { registerPatientPath } from '../lib/patients';
import { PatientSearchForm } from './patient-search-form';

interface PatientLookupSectionProps {
  lookup: PatientLookup | null;
  onLookupChange: (lookup: PatientLookup) => void;
  /** Qué mostrar cuando se encontró al paciente. */
  renderFound: (patient: Patient) => React.ReactNode;
  /** Texto de ayuda del formulario. */
  hint?: React.ReactNode;
  /** Mensaje del estado inicial (sin búsqueda). */
  idleTitle?: string;
  idleDescription?: React.ReactNode;
  autoFocus?: boolean;
}

/**
 * Formulario de búsqueda por documento + estados del resultado (cargando, no encontrado, error, encontrado).
 * Lo comparten Buscar paciente, Citas y el paso 1 del asistente de agendar.
 */
export function PatientLookupSection({
  lookup,
  onLookupChange,
  renderFound,
  hint,
  idleTitle = 'Busca al paciente por su documento',
  idleDescription = 'Ingresa el número de documento para ver sus datos, su seguro y sus citas.',
  autoFocus = true,
}: PatientLookupSectionProps) {
  const query = usePatientSearch(lookup);

  let result: React.ReactNode;
  if (!lookup) {
    result = <EmptyState icon={RiUserSearchLine} title={idleTitle} description={idleDescription} />;
  } else if (query.isPending) {
    result = <LoadingState rows={3} label='Buscando al paciente…' />;
  } else if (query.isError && isPatientNotFound(query.error)) {
    result = (
      <EmptyState
        icon={RiUserAddLine}
        title='No encontramos un paciente con ese documento'
        description={
          <>
            Revisa que el número <span className='text-text-strong-950'>{formatDocument(lookup.documentType, lookup.documentNumber)}</span>{' '}
            esté bien escrito o regístralo si es la primera vez que se atiende.
          </>
        }
        action={
          <Button.Root variant='primary' mode='filled' size='small' asChild>
            <Link to={registerPatientPath(lookup)}>
              <Button.Icon as={RiUserAddLine} />
              Registrar paciente
            </Link>
          </Button.Root>
        }
      />
    );
  } else if (query.isError) {
    result = (
      <EmptyState
        icon={RiErrorWarningLine}
        title='No pudimos buscar al paciente'
        description={getErrorMessage(query.error)}
        action={
          <Button.Root variant='neutral' mode='stroke' size='small' onClick={() => void query.refetch()}>
            Reintentar
          </Button.Root>
        }
      />
    );
  } else {
    result = renderFound(query.data);
  }

  return (
    <>
      <Card>
        <PatientSearchForm initial={lookup} onSearch={onLookupChange} isPending={lookup !== null && query.isFetching} autoFocus={autoFocus} hint={hint} />
      </Card>
      {query.isSuccess && lookup ? result : <Card>{result}</Card>}
    </>
  );
}
