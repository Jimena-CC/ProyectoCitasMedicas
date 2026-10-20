import * as React from 'react';
import { RiErrorWarningFill, RiIdCardLine, RiInformationFill, RiLoader4Line, RiSearchLine } from '@remixicon/react';

import * as Button from '@/components/ui/button';
import * as Hint from '@/components/ui/hint';
import * as Input from '@/components/ui/input';
import * as Label from '@/components/ui/label';
import * as Select from '@/components/ui/select';
import type { DocumentType, PatientLookup } from '@/lib/api';
import { cn } from '@/lib/cn';

import {
  DOCUMENT_TYPE_OPTIONS,
  documentMaxLength,
  documentPlaceholder,
  sanitizeDocumentNumber,
  validateDocumentNumber,
} from '../lib/documents';

interface PatientSearchFormProps {
  /** Búsqueda actual (p. ej. la que viene en la URL) para precargar los campos. */
  initial?: PatientLookup | null;
  onSearch: (lookup: PatientLookup) => void;
  isPending?: boolean;
  autoFocus?: boolean;
  /** Texto de ayuda bajo el campo cuando no hay error. */
  hint?: React.ReactNode;
  className?: string;
}

/**
 * Buscar paciente por documento (CU-016): un solo control con el número y el tipo de documento integrado
 * (ejemplos select-compact-for-input e input-with-button) más el botón "Buscar".
 */
export function PatientSearchForm({
  initial,
  onSearch,
  isPending = false,
  autoFocus = false,
  hint = 'Escribe el número tal como figura en el documento.',
  className,
}: PatientSearchFormProps) {
  const [documentType, setDocumentType] = React.useState<DocumentType>(initial?.documentType ?? 'DNI');
  const [documentNumber, setDocumentNumber] = React.useState(initial?.documentNumber ?? '');
  const [error, setError] = React.useState<string | null>(null);

  // Si la búsqueda de la URL cambia (p. ej. al navegar atrás), se reflejan los nuevos valores en los campos.
  const [syncedInitial, setSyncedInitial] = React.useState(initial);
  if (initial !== syncedInitial) {
    setSyncedInitial(initial);
    if (initial) {
      setDocumentType(initial.documentType);
      setDocumentNumber(initial.documentNumber);
    }
  }

  const handleTypeChange = (value: string) => {
    const type = value as DocumentType;
    setDocumentType(type);
    setDocumentNumber((current) => sanitizeDocumentNumber(type, current));
    setError(null);
  };

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const message = validateDocumentNumber(documentType, documentNumber);
    setError(message);
    if (message) return;
    onSearch({ documentType, documentNumber: documentNumber.trim() });
  };

  return (
    <form className={cn('flex flex-col gap-1', className)} onSubmit={handleSubmit} noValidate>
      <Label.Root htmlFor='document-number'>
        Documento del paciente <Label.Asterisk />
      </Label.Root>
      <div className='flex items-start gap-3'>
        <div className='w-full max-w-[460px]'>
          <Input.Root hasError={Boolean(error)}>
            <Input.Wrapper>
              <Input.Icon as={RiIdCardLine} />
              <Input.Input
                id='document-number'
                name='documentNumber'
                inputMode={documentType === 'DNI' ? 'numeric' : 'text'}
                autoComplete='off'
                autoFocus={autoFocus}
                placeholder={documentPlaceholder(documentType)}
                maxLength={documentMaxLength(documentType)}
                value={documentNumber}
                aria-invalid={Boolean(error)}
                aria-describedby={error || hint ? 'document-number-hint' : undefined}
                onChange={(e) => {
                  setDocumentNumber(sanitizeDocumentNumber(documentType, e.target.value));
                  if (error) setError(null);
                }}
                disabled={isPending}
              />
            </Input.Wrapper>
            <Select.Root variant='compactForInput' value={documentType} onValueChange={handleTypeChange} disabled={isPending}>
              <Select.Trigger aria-label='Tipo de documento'>
                <Select.Value />
              </Select.Trigger>
              <Select.Content>
                {DOCUMENT_TYPE_OPTIONS.map((option) => (
                  <Select.Item key={option.value} value={option.value}>
                    {option.value}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </Input.Root>
        </div>
        <Button.Root type='submit' variant='primary' mode='filled' size='medium' disabled={isPending}>
          <Button.Icon as={isPending ? RiLoader4Line : RiSearchLine} className={cn(isPending && 'animate-spin')} />
          {isPending ? 'Buscando…' : 'Buscar'}
        </Button.Root>
      </div>
      {error ? (
        <Hint.Root hasError id='document-number-hint' role='alert'>
          <Hint.Icon as={RiErrorWarningFill} />
          {error}
        </Hint.Root>
      ) : hint ? (
        <Hint.Root id='document-number-hint'>
          <Hint.Icon as={RiInformationFill} />
          {hint}
        </Hint.Root>
      ) : null}
    </form>
  );
}
