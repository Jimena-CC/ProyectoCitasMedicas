import * as React from 'react';
import { RiFilter3Line, RiRefreshLine } from '@remixicon/react';

import { Card } from '@/components/card';
import { SelectField, TextField } from '@/components/form-field';
import * as Button from '@/components/ui/button';
import {
  AUDIT_ACTIONS,
  AUDIT_ENTITIES,
  AUDIT_ENTITY_LABELS,
  actionsForEntity,
  isAuditAction,
  isAuditEntity,
} from '@/features/admin/audit/audit-labels';
import type { AuditAction, AuditEntity, UserSummary } from '@/lib/api';
import { formatRole } from '@/lib/formatters';

/** Valor del Select que representa "sin filtro" (Radix no admite cadenas vacías como valor). */
const ALL = 'ALL';

export interface AuditFilterValues {
  entity: AuditEntity | null;
  action: AuditAction | null;
  userId: number | null;
  from: string;
  to: string;
}

interface AuditFiltersProps {
  value: AuditFilterValues;
  users: UserSummary[] | undefined;
  onApply: (next: AuditFilterValues) => void;
  onClear: () => void;
}

/**
 * Barra de filtros de la auditoría (ejemplo select-label): entidad, acción dependiente, usuario y rango de fechas.
 * El borrador se reinicia desde la página con `key` cuando cambian los filtros aplicados (p. ej. "Limpiar").
 */
export function AuditFilters({ value, users, onApply, onClear }: AuditFiltersProps) {
  const [draft, setDraft] = React.useState<AuditFilterValues>(value);
  const [dateError, setDateError] = React.useState<string | null>(null);

  const actionOptions = React.useMemo(
    () => [
      { value: ALL, label: 'Todas las acciones' },
      ...actionsForEntity(draft.entity).map((action) => ({
        value: action,
        label: AUDIT_ACTIONS[action].label,
      })),
    ],
    [draft.entity],
  );

  const userOptions = React.useMemo(
    () => [
      { value: ALL, label: 'Todos los usuarios' },
      ...(users ?? []).map((user) => ({
        value: String(user.id),
        label: `${user.username} · ${formatRole(user.role)}`,
      })),
    ],
    [users],
  );

  const handleEntityChange = (next: string) => {
    const entity = isAuditEntity(next) ? next : null;
    setDraft((current) => ({
      ...current,
      entity,
      // La acción elegida se conserva solo si pertenece a la nueva entidad.
      action: current.action && entity && AUDIT_ACTIONS[current.action].entity !== entity ? null : current.action,
    }));
  };

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (draft.from && draft.to && draft.from > draft.to) {
      setDateError('La fecha final debe ser igual o posterior a la fecha inicial.');
      return;
    }
    setDateError(null);
    onApply(draft);
  };

  return (
    <Card>
      <form className='flex flex-col gap-4' onSubmit={handleSubmit} noValidate>
        <div className='flex items-start gap-3'>
          <SelectField
            id='audit-entity'
            label='Entidad'
            size='small'
            className='flex-1'
            value={draft.entity ?? ALL}
            onValueChange={handleEntityChange}
            options={[
              { value: ALL, label: 'Todas las entidades' },
              ...AUDIT_ENTITIES.map((entity) => ({
                value: entity,
                label: AUDIT_ENTITY_LABELS[entity],
              })),
            ]}
          />
          <SelectField
            id='audit-action'
            label='Acción'
            size='small'
            className='flex-1'
            value={draft.action ?? ALL}
            onValueChange={(next) =>
              setDraft((current) => ({
                ...current,
                action: isAuditAction(next) ? next : null,
              }))
            }
            options={actionOptions}
          />
          <SelectField
            id='audit-user'
            label='Usuario'
            size='small'
            className='flex-1'
            value={draft.userId === null ? ALL : String(draft.userId)}
            onValueChange={(next) =>
              setDraft((current) => ({
                ...current,
                userId: next === ALL ? null : Number(next),
              }))
            }
            options={userOptions}
          />
          <TextField
            id='audit-from'
            label='Desde'
            size='small'
            className='w-[150px] shrink-0'
              inputProps={{
              type: 'date',
              value: draft.from,
              max: draft.to || undefined,
              onChange: (e) => setDraft((c) => ({ ...c, from: e.target.value })),
            }}
          />
          <TextField
            id='audit-to'
            label='Hasta'
            size='small'
            className='w-[150px] shrink-0'
            error={dateError}
            inputProps={{
              type: 'date',
              value: draft.to,
              min: draft.from || undefined,
              onChange: (e) => setDraft((c) => ({ ...c, to: e.target.value })),
            }}
          />
        </div>
        <div className='flex items-center justify-end gap-2'>
          <Button.Root type='button' variant='neutral' mode='stroke' size='small' onClick={onClear}>
            <Button.Icon as={RiRefreshLine} />
            Limpiar
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
