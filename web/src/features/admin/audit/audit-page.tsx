import * as React from 'react';
import { useSearchParams } from 'react-router';
import { RiEyeLine, RiFileList3Line } from '@remixicon/react';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { format, parseISO, subDays } from 'date-fns';
import { es } from 'date-fns/locale';

import { Card, CardHeader } from '@/components/card';
import { DataTable } from '@/components/data-table';
import { PageHeader } from '@/components/page-header';
import * as Button from '@/components/ui/button';
import * as Select from '@/components/ui/select';
import * as Tooltip from '@/components/ui/tooltip';
import { auditEntityLabel, isAuditAction, isAuditEntity } from '@/features/admin/audit/audit-labels';
import { AuditActionBadge } from '@/features/admin/audit/components/audit-action-badge';
import { AuditDetailModal } from '@/features/admin/audit/components/audit-detail-modal';
import { AuditFilters, type AuditFilterValues } from '@/features/admin/audit/components/audit-filters';
import { AuditUserCell } from '@/features/admin/audit/components/audit-user-cell';
import { adminApi, getErrorMessage, type AuditEntry } from '@/lib/api';
import { formatInteger, todayIso, toIsoDate } from '@/lib/formatters';

const PAGE_SIZES = [20, 50, 100] as const;
const DEFAULT_SIZE = 20;
const DEFAULT_DAYS = 7;

function defaultRange() {
  return { from: toIsoDate(subDays(new Date(), DEFAULT_DAYS - 1)), to: todayIso() };
}

interface AuditUrlState extends AuditFilterValues {
  page: number;
  size: number;
}

/** Lee los filtros de la URL (los valores inválidos se ignoran y vuelven al valor por defecto). */
function readUrlState(params: URLSearchParams): AuditUrlState {
  const range = defaultRange();
  const entityParam = params.get('entidad');
  const actionParam = params.get('accion');
  const userParam = Number(params.get('usuario'));
  const sizeParam = Number(params.get('tamano'));
  const pageParam = Number(params.get('pagina'));
  const isIsoDate = (value: string | null): value is string => value !== null && /^\d{4}-\d{2}-\d{2}$/.test(value);
  const from = params.get('desde');
  const to = params.get('hasta');
  return {
    entity: isAuditEntity(entityParam) ? entityParam : null,
    action: isAuditAction(actionParam) ? actionParam : null,
    userId: Number.isInteger(userParam) && userParam > 0 ? userParam : null,
    from: isIsoDate(from) ? from : range.from,
    to: isIsoDate(to) ? to : range.to,
    page: Number.isInteger(pageParam) && pageParam > 0 ? pageParam - 1 : 0,
    size: (PAGE_SIZES as readonly number[]).includes(sizeParam) ? sizeParam : DEFAULT_SIZE,
  };
}

function writeUrlState(state: AuditUrlState): URLSearchParams {
  const params = new URLSearchParams();
  if (state.entity) params.set('entidad', state.entity);
  if (state.action) params.set('accion', state.action);
  if (state.userId !== null) params.set('usuario', String(state.userId));
  if (state.from) params.set('desde', state.from);
  if (state.to) params.set('hasta', state.to);
  if (state.page > 0) params.set('pagina', String(state.page + 1));
  if (state.size !== DEFAULT_SIZE) params.set('tamano', String(state.size));
  return params;
}

/** "2026-10-20T09:25:10-05:00" → { date: "20 de octubre de 2026", time: "09:25:10" } */
function splitDateTime(value: string): { date: string; time: string } {
  const parsed = parseISO(value);
  if (Number.isNaN(parsed.getTime())) return { date: value, time: '' };
  return { date: format(parsed, "d 'de' MMMM 'de' yyyy", { locale: es }), time: format(parsed, 'HH:mm:ss') };
}

/**
 * Consulta del registro de auditoría (CU-025, RF-32): filtros en la URL, tabla con paginación de servidor
 * y detalle completo en un modal.
 */
export function AuditPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const state = React.useMemo(() => readUrlState(searchParams), [searchParams]);
  const filters = React.useMemo<AuditFilterValues>(
    () => ({ entity: state.entity, action: state.action, userId: state.userId, from: state.from, to: state.to }),
    [state.entity, state.action, state.userId, state.from, state.to],
  );
  const [selected, setSelected] = React.useState<AuditEntry | null>(null);

  const usersQuery = useQuery({ queryKey: ['admin', 'users'], queryFn: adminApi.listUsers });

  const auditQuery = useQuery({
    queryKey: ['admin', 'audit', state],
    queryFn: () =>
      adminApi.searchAudit({
        entity: state.entity,
        action: state.action,
        userId: state.userId,
        from: state.from || null,
        to: state.to || null,
        page: state.page,
        size: state.size,
      }),
    placeholderData: (previous) => previous,
  });

  const update = (patch: Partial<AuditUrlState>) => setSearchParams(writeUrlState({ ...state, ...patch }));

  const columns = React.useMemo<ColumnDef<AuditEntry>[]>(
    () => [
      {
        id: 'registeredAt',
        header: 'Fecha y hora',
        size: 190,
        cell: ({ row }) => {
          const { date, time } = splitDateTime(row.original.registeredAt);
          return (
            <div className='flex flex-col whitespace-nowrap'>
              <span className='text-label-sm text-text-strong-950'>{date}</span>
              <span className='text-paragraph-xs text-text-sub-600'>{time}</span>
            </div>
          );
        },
      },
      {
        id: 'user',
        header: 'Usuario',
        size: 150,
        cell: ({ row }) => <AuditUserCell user={row.original.user} />,
      },
      {
        id: 'action',
        header: 'Acción',
        size: 160,
        cell: ({ row }) => <AuditActionBadge action={row.original.action} />,
      },
      {
        id: 'entity',
        header: 'Entidad',
        size: 100,
        cell: ({ row }) => (
          <div className='flex flex-col'>
            <span className='text-label-sm text-text-strong-950'>{auditEntityLabel(row.original.entity)}</span>
            <span className='text-paragraph-xs text-text-sub-600'>{row.original.entityId !== null ? `#${row.original.entityId}` : '—'}</span>
          </div>
        ),
      },
      {
        id: 'detail',
        header: 'Detalle',
        cell: ({ row }) => (
          <div className='flex items-center justify-between gap-3'>
            <Tooltip.Root>
              <Tooltip.Trigger asChild>
                <span className='line-clamp-1 break-all text-paragraph-sm text-text-sub-600'>{row.original.detail}</span>
              </Tooltip.Trigger>
              <Tooltip.Content className='max-w-[420px] whitespace-pre-wrap'>{row.original.detail}</Tooltip.Content>
            </Tooltip.Root>
            <Button.Root variant='neutral' mode='ghost' size='xsmall' onClick={() => setSelected(row.original)} aria-label={`Ver detalle del registro ${row.original.id}`}>
              <Button.Icon as={RiEyeLine} />
              Ver
            </Button.Root>
          </div>
        ),
      },
    ],
    [],
  );

  const page = auditQuery.data;
  const total = page?.totalElements ?? 0;

  return (
    <>
      <PageHeader title='Auditoría' description='Revisa quién hizo qué y cuándo en el sistema. Los registros más recientes aparecen primero.' />

      <AuditFilters
        key={JSON.stringify(filters)}
        value={filters}
        users={usersQuery.data}
        onApply={(next) => update({ ...next, page: 0 })}
        onClear={() => setSearchParams(writeUrlState({ ...defaultRange(), entity: null, action: null, userId: null, page: 0, size: state.size }))}
      />

      <Card className='flex flex-col gap-4'>
        <CardHeader
          title='Registros'
          description={
            auditQuery.isPending ? 'Cargando registros…' : `${formatInteger(total)} ${total === 1 ? 'registro encontrado' : 'registros encontrados'} en el periodo.`
          }
          actions={
            <div className='flex items-center gap-2'>
              <span className='text-paragraph-sm text-text-sub-600'>Por página</span>
              <Select.Root
                size='xsmall'
                variant='compact'
                value={String(state.size)}
                onValueChange={(next) => update({ size: Number(next), page: 0 })}
              >
                <Select.Trigger aria-label='Registros por página'>
                  <Select.Value />
                </Select.Trigger>
                <Select.Content>
                  {PAGE_SIZES.map((size) => (
                    <Select.Item key={size} value={String(size)}>
                      {size}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </div>
          }
        />
        <DataTable
          columns={columns}
          data={page?.content}
          isLoading={auditQuery.isPending}
          errorMessage={auditQuery.isError ? getErrorMessage(auditQuery.error) : null}
          onRetry={() => void auditQuery.refetch()}
          emptyIcon={RiFileList3Line}
          emptyTitle='No hay registros en este periodo'
          emptyDescription='Prueba con otro rango de fechas o quita algún filtro.'
          getRowId={(row) => String(row.id)}
          skeletonRows={8}
          pagination={{
            page: state.page,
            totalPages: page?.totalPages ?? 1,
            totalElements: page?.totalElements,
            onPageChange: (next) => update({ page: next }),
          }}
        />
      </Card>

      <AuditDetailModal entry={selected} onClose={() => setSelected(null)} />
    </>
  );
}
