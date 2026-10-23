import * as React from 'react';
import { RiFilter3Line, RiRefreshLine } from '@remixicon/react';

import { Card } from '@/components/card';
import { SelectField, TextField } from '@/components/form-field';
import * as Button from '@/components/ui/button';
import { MAX_RANGE_DAYS, validateRange } from '@/features/admin/reports/report-range';
import type { LocationDetail, SpecialtyDetail } from '@/lib/api';

/** Valor del Select que representa "sin filtro" (Radix no admite cadenas vacías como valor). */
const ALL = 'ALL';

export interface ReportFilterValues {
  from: string;
  to: string;
  specialtyId: number | null;
  locationId: number | null;
}

interface ReportFiltersProps {
  value: ReportFilterValues;
  specialties: SpecialtyDetail[] | undefined;
  locations: LocationDetail[] | undefined;
  onApply: (next: ReportFilterValues) => void;
  onReset: () => void;
}

/**
 * Filtros de los reportes: periodo (obligatorio, máximo 366 días) y especialidad/sede opcionales (solo para citas por estado).
 * La página reinicia el borrador con `key` cuando cambian los filtros aplicados.
 */
export function ReportFilters({ value, specialties, locations, onApply, onReset }: ReportFiltersProps) {
  const [draft, setDraft] = React.useState<ReportFilterValues>(value);
  const [rangeError, setRangeError] = React.useState<string | null>(null);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const error = validateRange({ from: draft.from, to: draft.to });
    setRangeError(error);
    if (!error) onApply(draft);
  };

  const handleDateChange = (field: 'from' | 'to', next: string) => {
    setDraft((current) => ({ ...current, [field]: next }));
    setRangeError(null);
  };

  return (
    <Card>
      <form className='flex flex-col gap-4' onSubmit={handleSubmit} noValidate>
        <div className='flex items-start gap-3'>
          <TextField
            id='report-from'
            label='Desde'
            required
            size='small'
            className='w-[160px] shrink-0'
            inputProps={{ type: 'date', value: draft.from, max: draft.to || undefined, onChange: (e) => handleDateChange('from', e.target.value) }}
          />
          <TextField
            id='report-to'
            label='Hasta'
            required
            size='small'
            className='w-[260px] shrink-0'
            hint={`Máximo ${MAX_RANGE_DAYS} días por reporte.`}
            error={rangeError}
            inputProps={{ type: 'date', value: draft.to, min: draft.from || undefined, onChange: (e) => handleDateChange('to', e.target.value) }}
          />
          <SelectField
            id='report-specialty'
            label='Especialidad'
            optional
            size='small'
            className='flex-1'
            hint='Solo aplica a citas por estado.'
            value={draft.specialtyId === null ? ALL : String(draft.specialtyId)}
            onValueChange={(next) => setDraft((current) => ({ ...current, specialtyId: next === ALL ? null : Number(next) }))}
            options={[
              { value: ALL, label: 'Todas las especialidades' },
              ...(specialties ?? []).map((specialty) => ({ value: String(specialty.id), label: specialty.name })),
            ]}
          />
          <SelectField
            id='report-location'
            label='Sede'
            optional
            size='small'
            className='flex-1'
            hint='Solo aplica a citas por estado.'
            value={draft.locationId === null ? ALL : String(draft.locationId)}
            onValueChange={(next) => setDraft((current) => ({ ...current, locationId: next === ALL ? null : Number(next) }))}
            options={[{ value: ALL, label: 'Todas las sedes' }, ...(locations ?? []).map((location) => ({ value: String(location.id), label: location.name }))]}
          />
        </div>
        <div className='flex items-center justify-end gap-2'>
          <Button.Root type='button' variant='neutral' mode='stroke' size='small' onClick={onReset}>
            <Button.Icon as={RiRefreshLine} />
            Mes actual
          </Button.Root>
          <Button.Root type='submit' variant='primary' mode='filled' size='small'>
            <Button.Icon as={RiFilter3Line} />
            Aplicar
          </Button.Root>
        </div>
      </form>
    </Card>
  );
}
