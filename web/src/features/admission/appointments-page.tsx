import { Link } from 'react-router';
import { RiUserLine } from '@remixicon/react';

import { Card } from '@/components/card';
import { PageHeader } from '@/components/page-header';
import * as Button from '@/components/ui/button';

import { PatientAppointments } from './components/patient-appointments';
import { PatientLookupSection } from './components/patient-lookup-section';
import { PatientSummaryCard } from './components/patient-summary-card';
import { usePatientLookupParams } from './hooks/use-patient';
import { patientDetailPath } from './lib/patients';

/** Citas de un paciente (CU-019 reprogramar, CU-020 anular): se busca por documento y se listan sus citas. */
export function AppointmentsPage() {
  const { lookup, setLookup } = usePatientLookupParams();

  return (
    <>
      <PageHeader title='Citas' description='Busca al paciente por su documento para reprogramar o anular sus citas.' />
      <PatientLookupSection
        lookup={lookup}
        onLookupChange={setLookup}
        idleTitle='Busca al paciente para ver sus citas'
        idleDescription='Solo las citas reservadas y futuras se pueden reprogramar o anular.'
        renderFound={(patient) => (
          <Card className='flex flex-col gap-5'>
            <PatientSummaryCard
              patient={patient}
              compact
              bare
              actions={
                <Button.Root variant='neutral' mode='stroke' size='small' asChild>
                  <Link to={patientDetailPath(patient)}>
                    <Button.Icon as={RiUserLine} />
                    Ver ficha
                  </Link>
                </Button.Root>
              }
            />
            <PatientAppointments patient={patient} />
          </Card>
        )}
      />
    </>
  );
}
