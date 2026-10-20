import * as React from 'react';

import { FormField } from '@/components/form-field';
import * as Select from '@/components/ui/select';
import type { DoctorDetail } from '@/lib/api/types';

interface DoctorSelectProps {
  id: string;
  label?: string;
  required?: boolean;
  doctors: DoctorDetail[];
  value: string;
  onValueChange: (value: string) => void;
  error?: string | null;
  hint?: React.ReactNode;
  disabled?: boolean;
  size?: 'medium' | 'small';
}

/** Select de médicos activos agrupados por especialidad (ejemplo select-label con Select.Group/GroupLabel). */
export function DoctorSelect({ id, label = 'Médico', required, doctors, value, onValueChange, error, hint, disabled, size = 'medium' }: DoctorSelectProps) {
  const groups = React.useMemo(() => {
    const bySpecialty = new Map<string, DoctorDetail[]>();
    doctors
      .filter((d) => d.active)
      .forEach((doctor) => {
        const list = bySpecialty.get(doctor.specialty.name) ?? [];
        list.push(doctor);
        bySpecialty.set(doctor.specialty.name, list);
      });
    return [...bySpecialty.entries()].sort(([a], [b]) => a.localeCompare(b, 'es'));
  }, [doctors]);

  return (
    <FormField label={label} htmlFor={id} required={required} error={error} hint={hint}>
      <Select.Root value={value} onValueChange={onValueChange} size={size} hasError={Boolean(error)} disabled={disabled || groups.length === 0}>
        <Select.Trigger id={id}>
          <Select.Value placeholder={groups.length === 0 ? 'No hay médicos activos' : 'Selecciona el médico'} />
        </Select.Trigger>
        <Select.Content>
          {groups.map(([specialty, list]) => (
            <Select.Group key={specialty}>
              <Select.GroupLabel>{specialty}</Select.GroupLabel>
              {list.map((doctor) => (
                <Select.Item key={doctor.id} value={String(doctor.id)}>
                  {doctor.fullName}
                </Select.Item>
              ))}
            </Select.Group>
          ))}
        </Select.Content>
      </Select.Root>
    </FormField>
  );
}
