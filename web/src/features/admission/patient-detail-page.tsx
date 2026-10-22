import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router';
import {
  RiAlertFill,
  RiCalendarCheckLine,
  RiCalendarLine,
  RiCheckboxCircleFill,
  RiErrorWarningLine,
  RiInformationFill,
  RiMailLine,
  RiPhoneLine,
  RiSearchLine,
  RiShieldCheckLine,
  RiUserLine,
  RiCakeLine,
  RiIdCardLine,
} from '@remixicon/react';

import { Card, CardHeader } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { LoadingState } from '@/components/loading-state';
import { PageHeader } from '@/components/page-header';
import * as Alert from '@/components/ui/alert';
import * as Button from '@/components/ui/button';
import * as Divider from '@/components/ui/divider';
import * as TabMenuHorizontal from '@/components/ui/tab-menu-horizontal';
import { frontDeskApi, getErrorMessage, type Patient } from '@/lib/api';
import { DOCUMENT_TYPE_LABELS, formatDocument, formatNumericDate } from '@/lib/formatters';

import { CoverageTable } from './components/coverage-table';
import { InsuranceForm } from './components/insurance-form';
import { PatientAppointments } from './components/patient-appointments';
import { PatientFact } from './components/patient-summary-card';
import { usePatient } from './hooks/use-patient';
import { lookupFromParams } from './lib/documents';
import { admissionKeys, bookingPath, insuranceLabel, patientAge, patientFullName } from './lib/patients';

type DetailTab = 'datos' | 'seguro' | 'citas';

function isDetailTab(value: string | null): value is DetailTab {
  return value === 'datos' || value === 'seguro' || value === 'citas';
}

/** Ficha del paciente: cabecera con nombre y documento, y pestañas Datos / Seguro (CU-017) / Citas. */
export function PatientDetailPage() {
  const { patientId: rawId } = useParams();
  const [params, setParams] = useSearchParams();
  const patientId = /^\d+$/.test(rawId ?? '') ? Number(rawId) : null;
  const lookup = React.useMemo(() => lookupFromParams(params), [params]);
  const query = usePatient(patientId, lookup);

  const tabParam = params.get('tab');
  const tab: DetailTab = isDetailTab(tabParam) ? tabParam : 'datos';
  const setTab = (next: string) => {
    setParams(
      (current) => {
        const updated = new URLSearchParams(current);
        if (next === 'datos') updated.delete('tab');
        else updated.set('tab', next);
        return updated;
      },
      { replace: true },
    );
  };

  if (patientId === null || query.isError) {
    // Sin documento en el enlace no hay con qué buscar al paciente: se pide abrir la ficha desde la búsqueda.
    const missingDocument = patientId !== null && lookup === null;
    return (
      <>
        <PageHeader title='Ficha del paciente' description='Datos del paciente, su seguro y sus citas.' />
        <Card>
          <EmptyState
            icon={missingDocument ? RiSearchLine : RiErrorWarningLine}
            title={missingDocument ? 'Busca al paciente para ver su ficha' : 'No pudimos cargar la ficha'}
            description={patientId === null ? 'El enlace no corresponde a un paciente válido.' : getErrorMessage(query.error)}
            action={
              <Button.Root variant='primary' mode='filled' size='small' asChild>
                <Link viewTransition to='/ventanilla/pacientes'>
                  <Button.Icon as={RiSearchLine} />
                  Buscar paciente
                </Link>
              </Button.Root>
            }
          />
        </Card>
      </>
    );
  }

  if (query.isPending) {
    return (
      <>
        <PageHeader title='Ficha del paciente' description='Cargando los datos del paciente…' />
        <Card>
          <LoadingState rows={4} label='Cargando la ficha del paciente…' />
        </Card>
      </>
    );
  }

  const patient = query.data;

  return (
    <>
      <PageHeader
        eyebrow='Ficha del paciente'
        title={patientFullName(patient)}
        description={`${formatDocument(patient.documentType, patient.documentNumber)} · ${insuranceLabel(patient)}`}
        actions={
          <>
            <Button.Root variant='neutral' mode='stroke' size='small' asChild>
              <Link viewTransition to='/ventanilla/pacientes'>
                <Button.Icon as={RiSearchLine} />
                Buscar otro
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

      <Card className='flex flex-col gap-5'>
        <TabMenuHorizontal.Root value={tab} onValueChange={setTab}>
          {/* Sin borde superior: dentro de la Card la línea quedaría suelta a 20 px del borde. */}
          <TabMenuHorizontal.List className='border-t-0'>
            <TabMenuHorizontal.Trigger value='datos'>
              <TabMenuHorizontal.Icon as={RiUserLine} />
              Datos
            </TabMenuHorizontal.Trigger>
            <TabMenuHorizontal.Trigger value='seguro'>
              <TabMenuHorizontal.Icon as={RiShieldCheckLine} />
              Seguro
            </TabMenuHorizontal.Trigger>
            <TabMenuHorizontal.Trigger value='citas'>
              <TabMenuHorizontal.Icon as={RiCalendarLine} />
              Citas
            </TabMenuHorizontal.Trigger>
          </TabMenuHorizontal.List>
          <TabMenuHorizontal.Content value='datos' className='pt-5'>
            <PatientDataTab patient={patient} />
          </TabMenuHorizontal.Content>
          <TabMenuHorizontal.Content value='seguro' className='pt-5'>
            <PatientInsuranceTab patient={patient} />
          </TabMenuHorizontal.Content>
          <TabMenuHorizontal.Content value='citas' className='pt-5'>
            <PatientAppointments patient={patient} withHeader />
          </TabMenuHorizontal.Content>
        </TabMenuHorizontal.Root>
      </Card>
    </>
  );
}

function PatientDataTab({ patient }: { patient: Patient }) {
  const age = patientAge(patient.birthDate);
  return (
    <div className='flex flex-col gap-6'>
      {/* El nombre y el documento ya están en la cabecera de la página. */}
      <dl className='grid grid-cols-3 gap-x-6 gap-y-5'>
        <PatientFact icon={RiIdCardLine} label='Documento'>
          {DOCUMENT_TYPE_LABELS[patient.documentType]} {patient.documentNumber}
        </PatientFact>
        <PatientFact icon={RiCakeLine} label='Fecha de nacimiento'>
          {formatNumericDate(patient.birthDate)}
          {age !== null && <span className='text-text-sub-600'> · {age} años</span>}
        </PatientFact>
        <PatientFact icon={RiShieldCheckLine} label='Seguro vigente'>
          {insuranceLabel(patient)}
          {patient.insurance?.policyNumber && <span className='text-text-sub-600'> · Póliza {patient.insurance.policyNumber}</span>}
        </PatientFact>
        <PatientFact icon={RiPhoneLine} label='Celular'>
          {patient.phone || '—'}
        </PatientFact>
        <PatientFact icon={RiMailLine} label='Correo electrónico' title={patient.email || undefined}>
          {patient.email || '—'}
        </PatientFact>
      </dl>
    </div>
  );
}

/** Pestaña Seguro (CU-017): resultado de la validación, coberturas registradas y formulario para asociar/actualizar. */
function PatientInsuranceTab({ patient }: { patient: Patient }) {
  const query = useQuery({
    queryKey: admissionKeys.insurance(patient.id),
    queryFn: () => frontDeskApi.getInsuranceValidation(patient.id),
  });
  const hasCoverages = (query.data?.coverages.length ?? 0) > 0;

  return (
    <div className='flex flex-col gap-6'>
      <div className='flex flex-col gap-4'>
        <CardHeader title='Validación de cobertura' description='Se consulta al momento para saber si puede atenderse con su seguro.' />
        {query.isSuccess && (
          // Sin coberturas registradas es un paciente particular: se informa sin tono de alerta.
          <Alert.Root variant='lighter' status={query.data.valid ? 'success' : hasCoverages ? 'warning' : 'information'} size='small'>
            <Alert.Icon as={query.data.valid ? RiCheckboxCircleFill : hasCoverages ? RiAlertFill : RiInformationFill} />
            {query.data.message}
          </Alert.Root>
        )}
        {/* La tabla solo se muestra si hay coberturas; así el formulario queda a la vista. */}
        {(!query.isSuccess || hasCoverages) && (
          <CoverageTable
            coverages={query.data?.coverages}
            isLoading={query.isPending}
            errorMessage={query.isError ? getErrorMessage(query.error) : null}
            onRetry={() => void query.refetch()}
          />
        )}
      </div>
      <Divider.Root />
      <InsuranceForm patient={patient} />
    </div>
  );
}
