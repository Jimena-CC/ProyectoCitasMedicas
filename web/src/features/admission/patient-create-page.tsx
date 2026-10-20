import * as React from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { RiArrowLeftLine, RiErrorWarningFill, RiLoader4Line, RiUserAddLine } from '@remixicon/react';

import { Card, CardHeader } from '@/components/card';
import { SelectField, TextField } from '@/components/form-field';
import { notify } from '@/components/notify';
import { PageHeader } from '@/components/page-header';
import * as Alert from '@/components/ui/alert';
import * as Button from '@/components/ui/button';
import * as Label from '@/components/ui/label';
import * as LinkButton from '@/components/ui/link-button';
import * as Switch from '@/components/ui/switch';
import { ApiError, frontDeskApi, type DocumentType, type PatientRequest } from '@/lib/api';
import { todayIso } from '@/lib/formatters';

import { useUpdatePatientCache } from './hooks/use-patient';
import {
  DOCUMENT_TYPE_OPTIONS,
  documentMaxLength,
  documentPlaceholder,
  isDocumentType,
  sanitizeDocumentNumber,
  validateDocumentNumber,
} from './lib/documents';
import { patientDetailPath } from './lib/patients';

const SEX_NONE = 'none';
const SEX_OPTIONS = [
  { value: SEX_NONE, label: 'Prefiere no indicarlo' },
  { value: 'M', label: 'Masculino' },
  { value: 'F', label: 'Femenino' },
];

interface FormState {
  documentType: DocumentType;
  documentNumber: string;
  firstNames: string;
  lastNames: string;
  birthDate: string;
  sex: string;
  phone: string;
  email: string;
  acceptsNotifications: boolean;
}

/** Registrar paciente desde ventanilla (RF-01, CU-016): datos personales a la izquierda y contacto a la derecha. */
export function PatientCreatePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const updateCache = useUpdatePatientCache();

  const initialType = params.get('documentType');
  const [form, setForm] = React.useState<FormState>(() => {
    const documentType: DocumentType = isDocumentType(initialType) ? initialType : 'DNI';
    return {
      documentType,
      documentNumber: sanitizeDocumentNumber(documentType, params.get('documentNumber') ?? ''),
      firstNames: '',
      lastNames: '',
      birthDate: '',
      sex: SEX_NONE,
      phone: '',
      email: '',
      acceptsNotifications: true,
    };
  });
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [duplicate, setDuplicate] = React.useState<{ message: string; detailPath: string | null } | null>(null);

  const update = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
    setFieldErrors((current) => {
      if (!(key in current)) return current;
      const next = { ...current };
      delete next[key];
      return next;
    });
  };

  const mutation = useMutation({
    mutationFn: (body: PatientRequest) => frontDeskApi.createPatient(body),
    onSuccess: (patient) => {
      updateCache(patient);
      notify.success(`Paciente ${patient.firstNames} ${patient.lastNames} registrado.`);
      navigate(patientDetailPath(patient), { replace: true });
    },
    onError: async (error) => {
      if (!(error instanceof ApiError)) {
        notify.fromError(error);
        return;
      }
      if (error.isValidation) {
        setFieldErrors(error.fieldErrors());
        return;
      }
      if (error.code === 'DUPLICATE_DOCUMENT') {
        // Buscamos al paciente ya registrado para enlazar directamente a su ficha.
        const existing = await frontDeskApi
          .searchPatient({ documentType: form.documentType, documentNumber: form.documentNumber.trim() })
          .catch(() => null);
        if (existing) updateCache(existing);
        setDuplicate({ message: error.message, detailPath: existing ? patientDetailPath(existing) : null });
        return;
      }
      notify.fromError(error);
    },
  });

  const validate = (): boolean => {
    const errors: Record<string, string> = {};
    const documentError = validateDocumentNumber(form.documentType, form.documentNumber);
    if (documentError) errors.documentNumber = documentError;
    if (form.firstNames.trim() === '') errors.firstNames = 'Ingresa los nombres del paciente.';
    if (form.lastNames.trim() === '') errors.lastNames = 'Ingresa los apellidos del paciente.';
    if (form.birthDate === '') errors.birthDate = 'Ingresa la fecha de nacimiento.';
    else if (form.birthDate >= todayIso()) errors.birthDate = 'La fecha de nacimiento debe ser anterior a hoy.';
    if (form.phone.trim() === '') errors.phone = 'Ingresa el número de celular.';
    else if (!/^9\d{8}$/.test(form.phone.trim())) errors.phone = 'El celular debe tener 9 dígitos y empezar con 9.';
    if (form.email.trim() === '') errors.email = 'Ingresa el correo electrónico.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = 'Ingresa un correo electrónico válido.';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setDuplicate(null);
    if (!validate()) return;
    mutation.mutate({
      documentType: form.documentType,
      documentNumber: form.documentNumber.trim(),
      firstNames: form.firstNames.trim(),
      lastNames: form.lastNames.trim(),
      birthDate: form.birthDate,
      sex: form.sex === SEX_NONE ? null : (form.sex as 'M' | 'F'),
      phone: form.phone.trim(),
      email: form.email.trim(),
      acceptsNotifications: form.acceptsNotifications,
    });
  };

  const isPending = mutation.isPending;

  return (
    <>
      <PageHeader
        title='Registrar paciente'
        description='Crea el perfil de un paciente que se atiende por primera vez. Luego podrás asociarle un seguro desde su ficha.'
        actions={
          <Button.Root variant='neutral' mode='stroke' size='small' asChild>
            <Link to='/ventanilla/pacientes'>
              <Button.Icon as={RiArrowLeftLine} />
              Volver a buscar
            </Link>
          </Button.Root>
        }
      />
      <form onSubmit={handleSubmit} noValidate className='flex flex-col gap-6'>
        {duplicate && (
          <Alert.Root variant='lighter' status='warning' size='small'>
            <Alert.Icon as={RiErrorWarningFill} />
            {duplicate.message}
            {duplicate.detailPath && (
              <LinkButton.Root variant='modifiable' size='medium' underline asChild>
                <Link to={duplicate.detailPath}>Abrir su ficha</Link>
              </LinkButton.Root>
            )}
          </Alert.Root>
        )}

        <div className='grid grid-cols-2 gap-6'>
          <Card className='flex flex-col gap-5'>
            <CardHeader title='Datos personales' description='Tal como figuran en el documento de identidad.' />
            <div className='grid grid-cols-[180px_1fr] gap-4'>
              <SelectField
                id='document-type'
                label='Tipo de documento'
                required
                value={form.documentType}
                onValueChange={(value) => {
                  const type = value as DocumentType;
                  update('documentType', type);
                  update('documentNumber', sanitizeDocumentNumber(type, form.documentNumber));
                }}
                options={DOCUMENT_TYPE_OPTIONS}
                disabled={isPending}
                error={fieldErrors.documentType}
              />
              <TextField
                id='document-number'
                label='Número de documento'
                required
                error={fieldErrors.documentNumber}
                inputProps={{
                  value: form.documentNumber,
                  onChange: (e) => update('documentNumber', sanitizeDocumentNumber(form.documentType, e.target.value)),
                  placeholder: documentPlaceholder(form.documentType),
                  inputMode: form.documentType === 'DNI' ? 'numeric' : 'text',
                  maxLength: documentMaxLength(form.documentType),
                  autoComplete: 'off',
                  disabled: isPending,
                }}
              />
            </div>
            <TextField
              id='first-names'
              label='Nombres'
              required
              error={fieldErrors.firstNames}
              inputProps={{
                value: form.firstNames,
                onChange: (e) => update('firstNames', e.target.value),
                placeholder: 'p. ej. Lucía Fernanda',
                maxLength: 100,
                autoComplete: 'off',
                autoFocus: form.documentNumber !== '',
                disabled: isPending,
              }}
            />
            <TextField
              id='last-names'
              label='Apellidos'
              required
              error={fieldErrors.lastNames}
              inputProps={{
                value: form.lastNames,
                onChange: (e) => update('lastNames', e.target.value),
                placeholder: 'p. ej. Paredes Quispe',
                maxLength: 100,
                autoComplete: 'off',
                disabled: isPending,
              }}
            />
            <div className='grid grid-cols-2 gap-4'>
              <TextField
                id='birth-date'
                label='Fecha de nacimiento'
                required
                error={fieldErrors.birthDate}
                inputProps={{
                  type: 'date',
                  value: form.birthDate,
                  onChange: (e) => update('birthDate', e.target.value),
                  max: todayIso(),
                  disabled: isPending,
                }}
              />
              <SelectField
                id='sex'
                label='Sexo'
                optional
                value={form.sex}
                onValueChange={(value) => update('sex', value)}
                options={SEX_OPTIONS}
                disabled={isPending}
                error={fieldErrors.sex}
              />
            </div>
          </Card>

          <Card className='flex flex-col gap-5'>
            <CardHeader title='Contacto' description='Se usan para confirmar y recordar las citas.' />
            <TextField
              id='phone'
              label='Celular'
              required
              hint='9 dígitos, empieza con 9.'
              error={fieldErrors.phone}
              inputProps={{
                value: form.phone,
                onChange: (e) => update('phone', e.target.value.replace(/\D/g, '').slice(0, 9)),
                placeholder: 'p. ej. 987654321',
                inputMode: 'numeric',
                maxLength: 9,
                autoComplete: 'off',
                disabled: isPending,
              }}
            />
            <TextField
              id='email'
              label='Correo electrónico'
              required
              error={fieldErrors.email}
              inputProps={{
                type: 'email',
                value: form.email,
                onChange: (e) => update('email', e.target.value),
                placeholder: 'p. ej. lucia.paredes@correo.pe',
                maxLength: 120,
                autoComplete: 'off',
                disabled: isPending,
              }}
            />
            <div className='flex items-start gap-3 rounded-10 bg-bg-weak-50 p-4'>
              <Switch.Root
                id='accepts-notifications'
                checked={form.acceptsNotifications}
                onCheckedChange={(checked) => update('acceptsNotifications', checked)}
                disabled={isPending}
                className='mt-0.5'
              />
              <div className='flex flex-col gap-0.5'>
                <Label.Root htmlFor='accepts-notifications' className='text-label-sm'>
                  Acepta recibir avisos de sus citas
                </Label.Root>
                <span className='text-paragraph-xs text-text-sub-600'>Confirmaciones y recordatorios por correo o mensaje de texto.</span>
              </div>
            </div>
          </Card>
        </div>

        <div className='flex items-center justify-end gap-3'>
          <Button.Root variant='neutral' mode='stroke' size='medium' asChild>
            <Link to='/ventanilla/pacientes'>Cancelar</Link>
          </Button.Root>
          <Button.Root type='submit' variant='primary' mode='filled' size='medium' disabled={isPending}>
            <Button.Icon as={isPending ? RiLoader4Line : RiUserAddLine} className={isPending ? 'animate-spin' : undefined} />
            {isPending ? 'Registrando…' : 'Registrar paciente'}
          </Button.Root>
        </div>
      </form>
    </>
  );
}
