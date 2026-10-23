import { Link } from 'react-router';
import { RiCalendarCheckLine, RiUserAddLine, RiUserLine } from '@remixicon/react';

import { PageHeader } from '@/components/page-header';
import * as Button from '@/components/ui/button';

import { PatientLookupSection } from './components/patient-lookup-section';
import { PatientSummaryCard } from './components/patient-summary-card';
import { usePatientLookupParams } from './hooks/use-patient';
import { bookingPath, patientDetailPath } from './lib/patients';

/** Buscar paciente por documento (CU-016). La búsqueda vive en la URL para que recargar la conserve. */
export function PatientSearchPage() {
  const { lookup, setLookup } = usePatientLookupParams();

  return (
    <>
      <PageHeader
        title='Buscar paciente'
        description='Busca al paciente por su documento o regístralo si es la primera vez que se atiende.'
        actions={
          <Button.Root variant='neutral' mode='stroke' size='small' asChild>
            <Link viewTransition to='/ventanilla/pacientes/nuevo'>
              <Button.Icon as={RiUserAddLine} />
              Registrar paciente
            </Link>
          </Button.Root>
        }
      />
      <PatientLookupSection
        lookup={lookup}
        onLookupChange={setLookup}
        renderFound={(patient) => (
          <PatientSummaryCard
            patient={patient}
            actions={
              <>
                <Button.Root variant='neutral' mode='stroke' size='small' asChild>
                  <Link viewTransition to={patientDetailPath(patient)}>
                    <Button.Icon as={RiUserLine} />
                    Ver ficha
                  </Link>
                </Button.Root>
                <Button.Root variant='primary' mode='filled' size='small' asChild>
                  <Link viewTransition to={bookingPath(patient)}>
                    <Button.Icon as={RiCalendarCheckLine} />
                    Agendar cita
                  </Link>
                </Button.Root>
              </>
            }
          />
        )}
      />
    </>
  );
}
