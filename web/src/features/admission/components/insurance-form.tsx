import * as React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RiLoader4Line, RiShieldCheckLine, RiUserUnfollowLine } from '@remixicon/react';

import { CardHeader } from '@/components/card';
import { ConfirmModal } from '@/components/confirm-modal';
import { SelectField, TextField } from '@/components/form-field';
import { notify } from '@/components/notify';
import * as Button from '@/components/ui/button';
import { ApiError, catalogApi, frontDeskApi, type Patient } from '@/lib/api';
import { formatCurrency } from '@/lib/formatters';

import { useUpdatePatientCache } from '../hooks/use-patient';
import { useSingleSubmit } from '../hooks/use-single-submit';
import { admissionKeys } from '../lib/patients';

interface InsuranceFormProps {
  patient: Patient;
}

/**
 * Asociar o actualizar el seguro del paciente (CU-017): aseguradora → plan + número de póliza,
 * y la opción "Dejar como particular" con confirmación.
 */
export function InsuranceForm({ patient }: InsuranceFormProps) {
  const queryClient = useQueryClient();
  const updateCache = useUpdatePatientCache();
  const insurersQuery = useQuery({ queryKey: admissionKeys.insurers, queryFn: catalogApi.listInsurers, staleTime: 10 * 60_000 });

  const currentInsurer = React.useMemo(
    () => insurersQuery.data?.find((insurer) => insurer.name === patient.insurance?.insurer) ?? null,
    [insurersQuery.data, patient.insurance],
  );
  const currentPlan = currentInsurer?.plans.find((plan) => plan.name === patient.insurance?.plan) ?? null;

  // null = el usuario aún no tocó el campo: se muestra el seguro vigente cuando llega el catálogo.
  const [chosenInsurerId, setInsurerId] = React.useState<string | null>(null);
  const [chosenPlanId, setPlanId] = React.useState<string | null>(null);
  const [policyNumber, setPolicyNumber] = React.useState(patient.insurance?.policyNumber ?? '');
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [confirmParticular, setConfirmParticular] = React.useState(false);
  const submit = useSingleSubmit();

  const insurerId = chosenInsurerId ?? (currentInsurer ? String(currentInsurer.id) : '');
  const planId = chosenPlanId ?? (currentPlan ? String(currentPlan.id) : '');

  const selectedInsurer = insurersQuery.data?.find((insurer) => String(insurer.id) === insurerId) ?? null;
  const selectedPlan = selectedInsurer?.plans.find((plan) => String(plan.id) === planId) ?? null;

  const afterSave = (updated: Patient) => {
    updateCache(updated);
    void queryClient.invalidateQueries({ queryKey: admissionKeys.insurance(patient.id) });
  };

  const saveMutation = useMutation({
    mutationFn: () => frontDeskApi.updateInsurance(patient.id, { planId: Number(planId), policyNumber: policyNumber.trim() || null }),
    onSuccess: (updated) => {
      afterSave(updated);
      notify.success('Seguro del paciente actualizado.');
    },
    onError: (error) => {
      if (error instanceof ApiError && error.isValidation) setFieldErrors(error.fieldErrors());
      else notify.fromError(error);
    },
  });

  const particularMutation = useMutation({
    mutationFn: () => frontDeskApi.updateInsurance(patient.id, { planId: null }),
    onSuccess: (updated) => {
      afterSave(updated);
      setInsurerId('');
      setPlanId('');
      setPolicyNumber('');
      setConfirmParticular(false);
      notify.success('El paciente quedó registrado como particular.');
    },
    onError: (error) => notify.fromError(error),
  });

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const errors: Record<string, string> = {};
    if (!insurerId) errors.insurerId = 'Selecciona la aseguradora.';
    if (insurerId && !planId) errors.planId = 'Selecciona el plan.';
    if (policyNumber.trim().length > 30) errors.policyNumber = 'El número de póliza no puede superar los 30 caracteres.';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    submit(() => saveMutation.mutateAsync());
  };

  const isPending = saveMutation.isPending || particularMutation.isPending;

  return (
    <form className='flex flex-col gap-5' onSubmit={handleSubmit} noValidate>
      <CardHeader
        title={patient.insurance ? 'Actualizar seguro' : 'Asociar un seguro'}
        description='Al guardar, el plan elegido queda como cobertura vigente y las anteriores se suspenden.'
      />
      <div className='grid grid-cols-3 gap-4'>
        <SelectField
          id='insurer'
          label='Aseguradora'
          required
          placeholder={insurersQuery.isPending ? 'Cargando…' : 'Selecciona la aseguradora'}
          value={insurerId}
          onValueChange={(value) => {
            setInsurerId(value);
            setPlanId('');
            setFieldErrors({});
          }}
          options={(insurersQuery.data ?? []).map((insurer) => ({ value: String(insurer.id), label: insurer.name }))}
          disabled={insurersQuery.isPending || isPending}
          error={fieldErrors.insurerId}
        />
        <SelectField
          id='plan'
          label='Plan'
          required
          placeholder={selectedInsurer ? 'Selecciona el plan' : 'Primero elige la aseguradora'}
          value={planId}
          onValueChange={(value) => {
            setPlanId(value);
            setFieldErrors({});
          }}
          options={(selectedInsurer?.plans ?? []).map((plan) => ({
            value: String(plan.id),
            label: (
              <span>
                {plan.name} <span className='text-text-sub-600'>· copago {formatCurrency(plan.consultationCopay)}</span>
              </span>
            ),
          }))}
          disabled={!selectedInsurer || isPending}
          error={fieldErrors.planId}
          hint={selectedPlan ? `Copago por consulta: ${formatCurrency(selectedPlan.consultationCopay)}` : undefined}
        />
        <TextField
          id='policy-number'
          label='Número de póliza'
          optional
          error={fieldErrors.policyNumber}
          inputProps={{
            value: policyNumber,
            onChange: (e) => setPolicyNumber(e.target.value),
            placeholder: 'p. ej. RM-00458712',
            maxLength: 30,
            disabled: isPending,
          }}
        />
      </div>
      <div className='flex items-center justify-between gap-3'>
        {patient.insurance ? (
          <Button.Root type='button' variant='error' mode='ghost' size='small' onClick={() => setConfirmParticular(true)} disabled={isPending}>
            <Button.Icon as={RiUserUnfollowLine} />
            Dejar como particular
          </Button.Root>
        ) : (
          <span className='text-paragraph-sm text-text-sub-600'>Hoy el paciente se atiende como particular.</span>
        )}
        <Button.Root type='submit' variant='primary' mode='filled' size='small' disabled={isPending || insurersQuery.isPending}>
          <Button.Icon as={saveMutation.isPending ? RiLoader4Line : RiShieldCheckLine} className={saveMutation.isPending ? 'animate-spin' : undefined} />
          {saveMutation.isPending ? 'Guardando…' : 'Guardar seguro'}
        </Button.Root>
      </div>

      <ConfirmModal
        open={confirmParticular}
        onOpenChange={setConfirmParticular}
        status='warning'
        title='¿Dejar al paciente como particular?'
        description='Se suspenderá su cobertura vigente. Las citas ya reservadas con ese seguro no cambian.'
        confirmLabel='Sí, dejar como particular'
        onConfirm={() => submit(() => particularMutation.mutateAsync())}
        isPending={particularMutation.isPending}
      />
    </form>
  );
}
