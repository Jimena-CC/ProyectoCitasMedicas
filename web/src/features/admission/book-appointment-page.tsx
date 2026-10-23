import * as React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import {
  RiAlertFill,
  RiArrowLeftLine,
  RiArrowRightLine,
  RiCalendarCheckFill,
  RiCalendarCheckLine,
  RiCalendarLine,
  RiCheckboxCircleFill,
  RiErrorWarningFill,
  RiLoader4Line,
  RiUserAddLine,
  RiUserSearchLine,
} from '@remixicon/react';

import { Card } from '@/components/card';
import { EmptyState } from '@/components/empty-state';
import { SelectField, TextareaField } from '@/components/form-field';
import { LoadingState } from '@/components/loading-state';
import { notify } from '@/components/notify';
import { PageHeader } from '@/components/page-header';
import * as Alert from '@/components/ui/alert';
import * as Button from '@/components/ui/button';
import * as Divider from '@/components/ui/divider';
import * as HorizontalStepper from '@/components/ui/horizontal-stepper';
import { ApiError, catalogApi, frontDeskApi, getErrorMessage, newIdempotencyKey, type Appointment, type Patient, type PatientLookup } from '@/lib/api';
import { cn } from '@/lib/cn';
import { formatDocument, formatLongDate, formatTime } from '@/lib/formatters';

import { AvailabilityPicker, type SelectedSlot } from './components/availability-picker';
import { PatientSearchForm } from './components/patient-search-form';
import { PatientSummaryCard } from './components/patient-summary-card';
import { isPatientNotFound, usePatient, usePatientLookupParams, usePatientSearch } from './hooks/use-patient';
import { useSingleSubmit } from './hooks/use-single-submit';
import { lookupToParams } from './lib/documents';
import { admissionKeys, insuranceLabel, patientDetailPath, patientFullName, registerPatientPath } from './lib/patients';

const STEPS = [
  { label: 'Paciente', indicator: '1' },
  { label: 'Especialidad y sede', indicator: '2' },
  { label: 'Fecha y horario', indicator: '3' },
  { label: 'Confirmar', indicator: '4' },
];

const PARTICULAR = 'particular';

interface Notice {
  status: 'error' | 'warning';
  message: string;
}

/**
 * Los mensajes de estos códigos en la API están escritos para el paciente ("Ya tienes una cita…").
 * En ventanilla se reemplazan por textos dirigidos al personal de admisión (RNF-16).
 */
const OPERATOR_MESSAGES: Record<string, string> = {
  DUPLICATE_APPOINTMENT: 'El paciente ya tiene una cita reservada que se cruza con ese horario. Elige otro horario.',
  COVERAGE_NOT_ACTIVE: 'La cobertura elegida ya no está vigente. Elige otro seguro o continúa como particular.',
};

/**
 * Agendar cita desde ventanilla (CU-018, RF-07/RF-08): asistente de 4 pasos con HorizontalStepper
 * (horizontal-stepper-demo). Acepta ?paciente=<id> (más el documento) para empezar con el paciente cargado.
 */
export function BookAppointmentPage() {
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const { lookup } = usePatientLookupParams();
  const preloadedId = /^\d+$/.test(params.get('paciente') ?? '') ? Number(params.get('paciente')) : null;

  const byId = usePatient(preloadedId, lookup);
  const bySearch = usePatientSearch(preloadedId ? null : lookup);
  const patientQuery = preloadedId ? byId : bySearch;
  const patient: Patient | null = patientQuery.data ?? null;

  const [step, setStep] = React.useState(0);
  const [locationId, setLocationId] = React.useState('');
  const [specialtyId, setSpecialtyId] = React.useState('');
  const [selection, setSelection] = React.useState<SelectedSlot | null>(null);
  const [visitReason, setVisitReason] = React.useState('');
  const [chosenInsurance, setInsuranceChoice] = React.useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [notice, setNotice] = React.useState<Notice | null>(null);
  const [idempotencyKey, setIdempotencyKey] = React.useState<string | null>(null);
  const [result, setResult] = React.useState<Appointment | null>(null);
  // Día del último cupo perdido por "cupo tomado", para que la grilla vuelva a esa misma semana.
  const [lostSlotDate, setLostSlotDate] = React.useState<string | null>(null);
  const submit = useSingleSubmit();

  const locationsQuery = useQuery({ queryKey: admissionKeys.locations, queryFn: catalogApi.listLocations, staleTime: 10 * 60_000 });
  const specialtiesQuery = useQuery({
    queryKey: admissionKeys.specialties(Number(locationId) || null),
    queryFn: () => catalogApi.listSpecialties(Number(locationId)),
    enabled: locationId !== '',
    staleTime: 10 * 60_000,
  });
  const insuranceQuery = useQuery({
    queryKey: admissionKeys.insurance(patient?.id ?? 0),
    queryFn: () => frontDeskApi.getInsuranceValidation((patient as Patient).id),
    enabled: patient !== null && step === 3,
  });

  const validCoverages = React.useMemo(() => (insuranceQuery.data?.coverages ?? []).filter((coverage) => coverage.valid), [insuranceQuery.data]);
  // Seguro elegido por el usuario o, por defecto, la primera cobertura vigente (si no hay, particular).
  const insuranceChoice = chosenInsurance ?? (insuranceQuery.data ? (validCoverages[0] ? String(validCoverages[0].patientInsuranceId) : PARTICULAR) : null);

  const location = locationsQuery.data?.find((item) => String(item.id) === locationId) ?? null;
  const specialty = specialtiesQuery.data?.find((item) => String(item.id) === specialtyId) ?? null;

  const bookMutation = useMutation({
    mutationFn: () =>
      frontDeskApi.bookAppointment(
        {
          patientId: (patient as Patient).id,
          slotId: (selection as SelectedSlot).slot.slotId,
          patientInsuranceId: insuranceChoice && insuranceChoice !== PARTICULAR ? Number(insuranceChoice) : null,
          visitReason: visitReason.trim(),
        },
        idempotencyKey ?? newIdempotencyKey(),
      ),
    onSuccess: (appointment) => {
      void queryClient.invalidateQueries({ queryKey: ['admission', 'appointments', (patient as Patient).id] });
      void queryClient.invalidateQueries({ queryKey: ['admission', 'availability'] });
      notify.success(`Cita ${appointment.code} reservada.`);
      setResult(appointment);
    },
    onError: (error) => {
      if (!(error instanceof ApiError)) {
        notify.fromError(error);
        return;
      }
      if (error.isValidation) {
        const errors = error.fieldErrors();
        setFieldErrors(errors);
        // Solo el motivo tiene campo visible en este paso; cualquier otro error de campo se muestra como aviso.
        if (Object.keys(errors).some((field) => field !== 'visitReason')) setNotice({ status: 'error', message: error.message });
        return;
      }
      if (error.code === 'SLOT_NOT_AVAILABLE') {
        setLostSlotDate(selection?.date ?? null);
        setSelection(null);
        void queryClient.invalidateQueries({ queryKey: ['admission', 'availability'] });
        setNotice({ status: 'warning', message: 'Ese horario acaba de ser tomado. Elige otro cupo disponible.' });
        setStep(2);
        return;
      }
      if (error.code === 'COVERAGE_NOT_ACTIVE') {
        // Se vuelve a validar el seguro y se deja de proponer la cobertura vencida.
        void queryClient.invalidateQueries({ queryKey: admissionKeys.insurance((patient as Patient).id) });
        setInsuranceChoice(null);
      }
      if (error.code === 'DUPLICATE_APPOINTMENT' || error.code === 'COVERAGE_NOT_ACTIVE') {
        setNotice({ status: 'error', message: OPERATOR_MESSAGES[error.code] });
        return;
      }
      notify.fromError(error);
    },
  });

  const canContinue = [patient !== null, location !== null && specialty !== null, selection !== null, true][step];

  const goTo = (next: number) => {
    setNotice(null);
    setStep(next);
    // La clave de idempotencia se genera UNA vez al entrar al paso de confirmación y se reutiliza en los reintentos.
    setIdempotencyKey(next === 3 ? newIdempotencyKey() : null);
  };

  const handleContinue = () => {
    if (!canContinue) return;
    goTo(step + 1);
  };

  const handleBook = () => {
    const errors: Record<string, string> = {};
    if (visitReason.trim() === '') errors.visitReason = 'Cuéntanos brevemente el motivo de la consulta.';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setNotice(null);
    submit(() => bookMutation.mutateAsync());
  };

  const resetForAnother = () => {
    setResult(null);
    setLostSlotDate(null);
    setSelection(null);
    setVisitReason('');
    setInsuranceChoice(null);
    setFieldErrors({});
    setNotice(null);
    goTo(1);
  };

  const changePatient = () => {
    setParams(
      (current) => {
        const updated = new URLSearchParams(current);
        ['paciente', 'documentType', 'documentNumber'].forEach((key) => updated.delete(key));
        return updated;
      },
      { replace: true },
    );
    goTo(0);
  };

  // Búsqueda manual en el paso 1: también se quita ?paciente=, para que la consulta pase a ser por documento.
  const searchPatient = (next: PatientLookup) => {
    setParams(
      (current) => {
        const updated = new URLSearchParams(current);
        updated.delete('paciente');
        Object.entries(lookupToParams(next)).forEach(([key, value]) => updated.set(key, value));
        return updated;
      },
      { replace: true },
    );
  };

  if (result && patient) {
    return <BookingConfirmation appointment={result} patient={patient} onAnother={resetForAnother} />;
  }

  return (
    <>
      <PageHeader title='Agendar cita' description='Reserva una cita a nombre del paciente eligiendo especialidad, sede y horario.' />
      <Card className='flex flex-col gap-6'>
        <HorizontalStepper.Root>
          {STEPS.map((item, index) => (
            <React.Fragment key={item.label}>
              <HorizontalStepper.Item
                state={step > index ? 'completed' : step === index ? 'active' : 'default'}
                aria-current={step === index ? 'step' : undefined}
                onClick={() => index < step && goTo(index)}
                disabled={index > step}
                type='button'
              >
                <HorizontalStepper.ItemIndicator>{item.indicator}</HorizontalStepper.ItemIndicator>
                {item.label}
              </HorizontalStepper.Item>
              {index < STEPS.length - 1 && <HorizontalStepper.SeparatorIcon />}
            </React.Fragment>
          ))}
        </HorizontalStepper.Root>
        <Divider.Root />
        {/* Anuncia el paso actual a los lectores de pantalla. */}
        <h2 className='sr-only' aria-live='polite'>
          Paso {step + 1} de {STEPS.length}: {STEPS[step].label}
        </h2>

        {notice && (
          <Alert.Root variant='lighter' status={notice.status} size='small'>
            <Alert.Icon as={notice.status === 'warning' ? RiAlertFill : RiErrorWarningFill} />
            {notice.message}
          </Alert.Root>
        )}

        {step === 0 && (
          <div className='flex flex-col gap-5'>
            {patient ? (
              <PatientSummaryCard
                patient={patient}
                bare
                className='rounded-10 bg-bg-weak-50 p-4'
                actions={
                  <Button.Root variant='neutral' mode='stroke' size='small' onClick={changePatient}>
                    <Button.Icon as={RiUserSearchLine} />
                    Cambiar paciente
                  </Button.Root>
                }
              />
            ) : (
              <>
                <PatientSearchForm initial={preloadedId ? null : lookup} onSearch={searchPatient} isPending={lookup !== null && patientQuery.isFetching} autoFocus />
                {lookup && patientQuery.isPending && <LoadingState rows={2} label='Buscando al paciente…' />}
                {preloadedId !== null && patientQuery.isError && (
                  <EmptyState
                    size='small'
                    icon={RiErrorWarningFill}
                    title='No pudimos cargar al paciente del enlace'
                    description={`${getErrorMessage(patientQuery.error)} Usa el buscador de arriba.`}
                  />
                )}
                {!preloadedId && lookup && patientQuery.isError && isPatientNotFound(patientQuery.error) && (
                  <EmptyState
                    size='small'
                    icon={RiUserAddLine}
                    title='No encontramos un paciente con ese documento'
                    description={`Revisa el número ${formatDocument(lookup.documentType, lookup.documentNumber)} o regístralo para poder agendarle una cita.`}
                    action={
                      <Button.Root variant='primary' mode='filled' size='small' asChild>
                        <Link viewTransition to={registerPatientPath(lookup)}>
                          <Button.Icon as={RiUserAddLine} />
                          Registrar paciente
                        </Link>
                      </Button.Root>
                    }
                  />
                )}
                {!preloadedId && lookup && patientQuery.isError && !isPatientNotFound(patientQuery.error) && (
                  <EmptyState
                    size='small'
                    icon={RiErrorWarningFill}
                    title='No pudimos buscar al paciente'
                    description={getErrorMessage(patientQuery.error)}
                    action={
                      <Button.Root variant='neutral' mode='stroke' size='small' onClick={() => void patientQuery.refetch()}>
                        Reintentar
                      </Button.Root>
                    }
                  />
                )}
              </>
            )}
          </div>
        )}

        {step === 1 && (
          <div className='flex flex-col gap-5'>
            <div className='grid grid-cols-2 gap-4'>
              <SelectField
                id='booking-location'
                label='Sede'
                required
                placeholder={locationsQuery.isPending ? 'Cargando sedes…' : 'Selecciona la sede'}
                value={locationId}
                onValueChange={(value) => {
                  setLocationId(value);
                  setSpecialtyId('');
                  setSelection(null);
                }}
                options={(locationsQuery.data ?? []).map((item) => ({
                  value: String(item.id),
                  label: (
                    <span>
                      {item.name} <span className='text-text-sub-600'>· {item.district}</span>
                    </span>
                  ),
                }))}
                disabled={!locationsQuery.isSuccess}
                error={locationsQuery.isError ? getErrorMessage(locationsQuery.error) : null}
              />
              <SelectField
                id='booking-specialty'
                label='Especialidad'
                required
                placeholder={!locationId ? 'Primero elige la sede' : specialtiesQuery.isPending ? 'Cargando especialidades…' : 'Selecciona la especialidad'}
                value={specialtyId}
                onValueChange={(value) => {
                  setSpecialtyId(value);
                  setSelection(null);
                }}
                options={(specialtiesQuery.data ?? []).map((item) => ({ value: String(item.id), label: item.name }))}
                disabled={!locationId || !specialtiesQuery.isSuccess}
                error={specialtiesQuery.isError ? getErrorMessage(specialtiesQuery.error) : null}
                hint={specialty ? `${specialty.description ?? ''} Consulta de ${specialty.appointmentMinutes} minutos.`.trim() : undefined}
              />
            </div>
            {location && (
              <p className='text-paragraph-sm text-text-sub-600'>
                {location.name}: {location.address}, {location.district}.
              </p>
            )}
          </div>
        )}

        {step === 2 && specialty && location && (
          <AvailabilityPicker
            specialtyId={specialty.id}
            locationId={location.id}
            selectedSlotId={selection?.slot.slotId ?? null}
            initialDate={selection?.date ?? lostSlotDate}
            onSelect={(next) => {
              setSelection(next);
              setNotice(null);
            }}
          />
        )}

        {step === 3 && patient && selection && specialty && location && (
          <div className='grid grid-cols-[minmax(0,5fr)_minmax(0,7fr)] gap-6'>
            <BookingSummary patient={patient} selection={selection} specialtyName={specialty.name} locationName={location.name} />
            <div className='flex flex-col gap-5'>
              <TextareaField
                id='visit-reason'
                label='Motivo de la consulta'
                required
                value={visitReason}
                maxLength={250}
                error={fieldErrors.visitReason}
                textareaProps={{
                  placeholder: 'p. ej. Control de presión arterial',
                  rows: 4,
                  disabled: bookMutation.isPending,
                  onChange: (e) => {
                    setVisitReason(e.target.value);
                    if (fieldErrors.visitReason) setFieldErrors({});
                  },
                }}
              />
              <SelectField
                id='booking-insurance'
                label='Seguro a usar'
                required
                placeholder={insuranceQuery.isPending ? 'Validando cobertura…' : 'Selecciona el seguro'}
                value={insuranceChoice ?? ''}
                onValueChange={setInsuranceChoice}
                options={[
                  ...validCoverages.map((coverage) => ({
                    value: String(coverage.patientInsuranceId),
                    label: (
                      <span>
                        {coverage.insurer} · {coverage.plan}
                        {coverage.policyNumber && <span className='text-text-sub-600'> · Póliza {coverage.policyNumber}</span>}
                      </span>
                    ),
                  })),
                  { value: PARTICULAR, label: 'Particular (sin seguro)' },
                ]}
                disabled={insuranceQuery.isPending || bookMutation.isPending}
                hint={insuranceQuery.data?.message}
                error={insuranceQuery.isError ? getErrorMessage(insuranceQuery.error) : null}
              />
            </div>
          </div>
        )}

        <Divider.Root />
        <div className='flex items-center justify-between gap-3'>
          <Button.Root variant='neutral' mode='stroke' size='medium' onClick={() => goTo(step - 1)} disabled={step === 0 || bookMutation.isPending} className={cn(step === 0 && 'invisible')}>
            <Button.Icon as={RiArrowLeftLine} />
            Atrás
          </Button.Root>
          {step < 3 ? (
            <Button.Root variant='primary' mode='filled' size='medium' onClick={handleContinue} disabled={!canContinue}>
              Continuar
              <Button.Icon as={RiArrowRightLine} />
            </Button.Root>
          ) : (
            <Button.Root variant='primary' mode='filled' size='medium' onClick={handleBook} disabled={bookMutation.isPending || insuranceQuery.isPending || insuranceChoice === null}>
              <Button.Icon as={bookMutation.isPending ? RiLoader4Line : RiCalendarCheckLine} className={bookMutation.isPending ? 'animate-spin' : undefined} />
              {bookMutation.isPending ? 'Reservando…' : 'Reservar cita'}
            </Button.Root>
          )}
        </div>
      </Card>
    </>
  );
}

interface BookingSummaryProps {
  patient: Patient;
  selection: SelectedSlot;
  specialtyName: string;
  locationName: string;
}

/** Resumen de la reserva (estilo SummaryCard): filas etiqueta/valor sobre fondo suave. */
function BookingSummary({ patient, selection, specialtyName, locationName }: BookingSummaryProps) {
  const rows: { label: string; value: React.ReactNode }[] = [
    { label: 'Paciente', value: `${patientFullName(patient)} · ${formatDocument(patient.documentType, patient.documentNumber)}` },
    { label: 'Especialidad', value: specialtyName },
    { label: 'Médico', value: `${selection.slot.doctor.fullName} · CMP ${selection.slot.doctor.license}` },
    { label: 'Fecha', value: formatLongDate(selection.date) },
    { label: 'Hora', value: `${formatTime(selection.slot.startTime)} – ${formatTime(selection.slot.endTime)}` },
    { label: 'Sede', value: `${locationName}${selection.slot.room ? ` · ${selection.slot.room}` : ''}` },
  ];
  return (
    <div className='flex flex-col gap-4 rounded-10 bg-bg-weak-50 p-5'>
      <div className='flex items-center gap-2.5'>
        <RiCalendarLine className='size-5 text-text-sub-600' />
        <span className='text-label-md text-text-strong-950'>Resumen de la cita</span>
      </div>
      <dl className='flex flex-col divide-y divide-stroke-soft-200'>
        {rows.map((row) => (
          <div key={row.label} className='flex items-start justify-between gap-4 py-2.5'>
            <dt className='shrink-0 text-paragraph-sm text-text-sub-600'>{row.label}</dt>
            <dd className='text-right text-label-sm text-text-strong-950'>{row.value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

interface BookingConfirmationProps {
  appointment: Appointment;
  patient: Patient;
  onAnother: () => void;
}

/** Pantalla de éxito con el código de la cita y los datos reservados. */
function BookingConfirmation({ appointment, patient, onAnother }: BookingConfirmationProps) {
  const rows: { label: string; value: string }[] = [
    { label: 'Paciente', value: patientFullName(patient) },
    { label: 'Especialidad', value: appointment.specialty },
    { label: 'Médico', value: appointment.doctor },
    { label: 'Fecha', value: formatLongDate(appointment.date) },
    { label: 'Hora', value: `${formatTime(appointment.startTime)} – ${formatTime(appointment.endTime)}` },
    { label: 'Sede', value: `${appointment.location}${appointment.room ? ` · ${appointment.room}` : ''}` },
    { label: 'Cobertura', value: appointment.coverage || insuranceLabel(patient) },
    { label: 'Motivo', value: appointment.visitReason },
  ];
  return (
    <>
      <PageHeader title='Agendar cita' description='La cita quedó reservada a nombre del paciente.' />
      <Card className='mx-auto flex w-full max-w-[640px] flex-col items-center gap-6 p-8 text-center'>
        <div className='flex size-14 items-center justify-center rounded-full bg-success-lighter'>
          <RiCheckboxCircleFill className='size-7 text-success-base' />
        </div>
        <div className='flex flex-col gap-1'>
          <h2 className='text-title-h6 text-text-strong-950'>Cita reservada</h2>
          <p className='text-paragraph-sm text-text-sub-600'>Entrega este código al paciente. También le enviaremos la confirmación y un recordatorio.</p>
        </div>
        <div className='flex items-center gap-3 rounded-10 bg-bg-weak-50 px-6 py-4'>
          <RiCalendarCheckFill className='size-6 text-primary-base' />
          <span className='text-title-h5 tracking-wide text-text-strong-950'>{appointment.code}</span>
        </div>
        <dl className='flex w-full flex-col divide-y divide-stroke-soft-200 text-left'>
          {rows.map((row) => (
            <div key={row.label} className='flex items-start justify-between gap-4 py-2.5'>
              <dt className='shrink-0 text-paragraph-sm text-text-sub-600'>{row.label}</dt>
              <dd className='text-right text-label-sm text-text-strong-950'>{row.value}</dd>
            </div>
          ))}
        </dl>
        <div className='flex w-full items-center gap-3'>
          <Button.Root variant='neutral' mode='stroke' size='medium' className='w-full' onClick={onAnother}>
            Agendar otra
          </Button.Root>
          <Button.Root variant='primary' mode='filled' size='medium' className='w-full' asChild>
            <Link viewTransition to={patientDetailPath(patient, 'citas')}>
              <Button.Icon as={RiCalendarLine} />
              Ver citas del paciente
            </Link>
          </Button.Root>
        </div>
      </Card>
    </>
  );
}
