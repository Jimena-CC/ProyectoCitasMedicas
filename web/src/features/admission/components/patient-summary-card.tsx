import * as React from 'react';
import { RiMailLine, RiPhoneLine, RiShieldCheckLine, RiCakeLine, type RemixiconComponentType } from '@remixicon/react';

import { Card } from '@/components/card';
import * as Avatar from '@/components/ui/avatar';
import * as Badge from '@/components/ui/badge';
import type { Patient } from '@/lib/api';
import { cn } from '@/lib/cn';
import { formatDocument, formatNumericDate, initialsOf } from '@/lib/formatters';

import { insuranceLabel, patientAge, patientFullName } from '../lib/patients';

interface PatientSummaryCardProps {
  patient: Patient;
  /** Botones a la derecha (Ver ficha, Agendar cita...). */
  actions?: React.ReactNode;
  /** Versión reducida: solo nombre, documento y seguro (para pasos del asistente). */
  compact?: boolean;
  /** Sin tarjeta propia (para incrustar dentro de otra Card). */
  bare?: boolean;
  className?: string;
}

/** Datos del paciente encontrado (CU-016): avatar con iniciales, nombre, documento y datos de contacto. */
export function PatientSummaryCard({ patient, actions, compact = false, bare = false, className }: PatientSummaryCardProps) {
  const age = patientAge(patient.birthDate);
  const Wrapper = bare ? 'div' : Card;

  return (
    <Wrapper className={cn('flex flex-col gap-5', className)}>
      <div className='flex flex-wrap items-start justify-between gap-4'>
        <div className='flex items-center gap-3.5'>
          <Avatar.Root size={compact ? '40' : '48'} color='blue'>
            {initialsOf(patientFullName(patient))}
          </Avatar.Root>
          <div className='flex flex-col gap-1'>
            <div className={cn('text-text-strong-950', compact ? 'text-label-md' : 'text-label-lg')}>{patientFullName(patient)}</div>
            <div className='flex flex-wrap items-center gap-2 text-paragraph-sm text-text-sub-600'>
              <span>{formatDocument(patient.documentType, patient.documentNumber)}</span>
              <Badge.Root variant='lighter' color={patient.insurance ? 'green' : 'gray'} size='small'>
                {insuranceLabel(patient)}
              </Badge.Root>
            </div>
          </div>
        </div>
        {actions && <div className='flex shrink-0 items-center gap-2'>{actions}</div>}
      </div>

      {!compact && (
        <dl className='grid grid-cols-2 gap-x-6 gap-y-4 xl:grid-cols-[auto_auto_minmax(0,1fr)_minmax(0,1fr)]'>
          <PatientFact icon={RiCakeLine} label='Fecha de nacimiento'>
            {formatNumericDate(patient.birthDate)}
            {age !== null && <span className='text-text-sub-600'> · {age} años</span>}
          </PatientFact>
          <PatientFact icon={RiPhoneLine} label='Celular'>
            {patient.phone || '—'}
          </PatientFact>
          <PatientFact icon={RiMailLine} label='Correo electrónico' title={patient.email || undefined}>
            {patient.email || '—'}
          </PatientFact>
          <PatientFact
            icon={RiShieldCheckLine}
            label='Seguro vigente'
            title={`${insuranceLabel(patient)}${patient.insurance?.policyNumber ? ` · Póliza ${patient.insurance.policyNumber}` : ''}`}
          >
            {insuranceLabel(patient)}
            {patient.insurance?.policyNumber && <span className='text-text-sub-600'> · Póliza {patient.insurance.policyNumber}</span>}
          </PatientFact>
        </dl>
      )}
    </Wrapper>
  );
}

interface PatientFactProps {
  icon: RemixiconComponentType;
  label: string;
  /** Texto completo para el tooltip nativo cuando el valor se corta. */
  title?: string;
  children: React.ReactNode;
}

/**
 * Dato con etiqueta pequeña en mayúsculas (text-subheading-xs) y valor en text-label-sm. Debe ir dentro de un <dl>:
 * el div raíz solo contiene dt y dd (HTML válido) y el icono se posiciona dentro del dt.
 */
export function PatientFact({ icon: Icon, label, title, children }: PatientFactProps) {
  return (
    <div className='relative flex min-w-0 flex-col gap-0.5 pl-[42px]'>
      <dt className='text-subheading-2xs uppercase text-text-soft-400'>
        <span className='absolute left-0 top-0 flex size-8 items-center justify-center rounded-full bg-bg-weak-50 ring-1 ring-inset ring-stroke-soft-200'>
          <Icon className='size-4 text-text-sub-600' aria-hidden />
        </span>
        {label}
      </dt>
      <dd className='truncate text-label-sm text-text-strong-950' title={title}>
        {children}
      </dd>
    </div>
  );
}
