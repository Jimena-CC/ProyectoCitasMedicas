import * as React from 'react';
import { useLocation, useSearchParams } from 'react-router';
import { RiBarChartBoxLine, RiCloseLine, RiInformationFill, RiTimeLine, RiUserUnfollowLine } from '@remixicon/react';
import { useQuery } from '@tanstack/react-query';

import { PageHeader } from '@/components/page-header';
import * as Alert from '@/components/ui/alert';
import * as TabMenuHorizontal from '@/components/ui/tab-menu-horizontal';
import { AppointmentsTab } from '@/features/admin/reports/appointments-tab';
import { NoShowTab } from '@/features/admin/reports/no-show-tab';
import { OccupancyTab } from '@/features/admin/reports/occupancy-tab';
import { ReportFilters, type ReportFilterValues } from '@/features/admin/reports/components/report-filters';
import { currentMonthRange, isIsoDate, validateRange } from '@/features/admin/reports/report-range';
import { adminApi } from '@/lib/api';
import { formatShortDate } from '@/lib/formatters';

const TABS = [
  { value: 'citas', label: 'Citas por estado', icon: RiBarChartBoxLine },
  { value: 'inasistencia', label: 'Inasistencia', icon: RiUserUnfollowLine },
  { value: 'ocupacion', label: 'Ocupación', icon: RiTimeLine },
] as const;

type TabValue = (typeof TABS)[number]['value'];

function isTab(value: string | null): value is TabValue {
  return TABS.some((tab) => tab.value === value);
}

interface ReportsUrlState extends ReportFilterValues {
  tab: TabValue;
}

/** El periodo de la URL existe pero no es válido (fechas inexistentes, invertidas o más de 366 días). */
function hasInvalidUrlRange(params: URLSearchParams): boolean {
  if (!params.has('desde') && !params.has('hasta')) return false;
  const from = params.get('desde');
  const to = params.get('hasta');
  return !(isIsoDate(from) && isIsoDate(to) && validateRange({ from, to }) === null);
}

/** Aviso que se pasa en el estado de navegación al corregir una URL inválida. */
interface ReportsLocationState {
  reportNotice?: string;
}

function noticeFrom(state: unknown): string | null {
  const notice = (state as ReportsLocationState | null)?.reportNotice;
  return typeof notice === 'string' ? notice : null;
}

function readUrlState(params: URLSearchParams): ReportsUrlState {
  const month = currentMonthRange();
  const from = params.get('desde');
  const to = params.get('hasta');
  const specialty = Number(params.get('especialidad'));
  const location = Number(params.get('sede'));
  const tab = params.get('vista');
  const hasValidRange = isIsoDate(from) && isIsoDate(to) && validateRange({ from, to }) === null;
  return {
    tab: isTab(tab) ? tab : 'citas',
    from: hasValidRange ? from : month.from,
    to: hasValidRange ? to : month.to,
    specialtyId: Number.isInteger(specialty) && specialty > 0 ? specialty : null,
    locationId: Number.isInteger(location) && location > 0 ? location : null,
  };
}

function writeUrlState(state: ReportsUrlState): URLSearchParams {
  const params = new URLSearchParams();
  if (state.tab !== 'citas') params.set('vista', state.tab);
  params.set('desde', state.from);
  params.set('hasta', state.to);
  if (state.specialtyId !== null) params.set('especialidad', String(state.specialtyId));
  if (state.locationId !== null) params.set('sede', String(state.locationId));
  return params;
}

/**
 * Reportes de gestión (CU-026, CU-027): citas por estado, tasa de inasistencia y ocupación de cupos
 * para un periodo, con exportación a CSV en cada pestaña (ejemplo tab-menu-horizontal-demo).
 */
export function ReportsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const state = React.useMemo(() => readUrlState(searchParams), [searchParams]);
  const filters = React.useMemo<ReportFilterValues>(
    () => ({ from: state.from, to: state.to, specialtyId: state.specialtyId, locationId: state.locationId }),
    [state.from, state.to, state.specialtyId, state.locationId],
  );
  const range = React.useMemo(() => ({ from: state.from, to: state.to }), [state.from, state.to]);

  const specialtiesQuery = useQuery({ queryKey: ['admin', 'specialties'], queryFn: adminApi.listSpecialties });
  const locationsQuery = useQuery({ queryKey: ['admin', 'locations'], queryFn: adminApi.listLocations });

  const update = (patch: Partial<ReportsUrlState>) => setSearchParams(writeUrlState({ ...state, ...patch }));

  // Una URL con un periodo inválido o con una especialidad/sede que no existe se corrige (sin crear una entrada
  // nueva en el historial) y se explica con un aviso, para que la barra de direcciones coincida con lo que se ve.
  const location = useLocation();
  const notice = noticeFrom(location.state);
  const rangeIsInvalid = hasInvalidUrlRange(searchParams);
  const specialties = specialtiesQuery.data;
  const locations = locationsQuery.data;
  React.useEffect(() => {
    const problems: string[] = [];
    let next = state;
    if (rangeIsInvalid) problems.push('El periodo indicado en la dirección no es válido; se muestra el mes actual.');
    if (specialties && state.specialtyId !== null && !specialties.some((specialty) => specialty.id === state.specialtyId)) {
      next = { ...next, specialtyId: null };
      problems.push('La especialidad indicada en la dirección no existe; se muestran todas.');
    }
    if (locations && state.locationId !== null && !locations.some((item) => item.id === state.locationId)) {
      next = { ...next, locationId: null };
      problems.push('La sede indicada en la dirección no existe; se muestran todas.');
    }
    if (problems.length === 0) return;
    const message = [notice, ...problems].filter(Boolean).join(' ');
    setSearchParams(writeUrlState(next), { replace: true, state: { reportNotice: message } satisfies ReportsLocationState });
  }, [rangeIsInvalid, specialties, locations, state, notice, setSearchParams]);

  const dismissNotice = () => setSearchParams(searchParams, { replace: true, state: null });

  return (
    <>
      <PageHeader
        title='Reportes'
        eyebrow={`Periodo: ${formatShortDate(state.from)} – ${formatShortDate(state.to)}`}
        description='Citas por estado, tasa de inasistencia y ocupación de cupos. Cada reporte se puede exportar a CSV con los mismos filtros.'
      />

      {notice && (
        <Alert.Root variant='lighter' status='warning' size='small' role='status'>
          <Alert.Icon as={RiInformationFill} />
          {notice}
          <button type='button' onClick={dismissNotice} aria-label='Cerrar aviso'>
            <Alert.CloseIcon as={RiCloseLine} />
          </button>
        </Alert.Root>
      )}

      <ReportFilters
        key={JSON.stringify(filters)}
        value={filters}
        specialties={specialtiesQuery.data}
        locations={locationsQuery.data}
        onApply={(next) => update(next)}
        onReset={() => update({ ...currentMonthRange(), specialtyId: null, locationId: null })}
      />

      <TabMenuHorizontal.Root value={state.tab} onValueChange={(next) => isTab(next) && update({ tab: next })}>
        <TabMenuHorizontal.List className='mb-6'>
          {TABS.map((tab) => (
            <TabMenuHorizontal.Trigger key={tab.value} value={tab.value}>
              <TabMenuHorizontal.Icon as={tab.icon} />
              {tab.label}
            </TabMenuHorizontal.Trigger>
          ))}
        </TabMenuHorizontal.List>
        <TabMenuHorizontal.Content value='citas'>
          <AppointmentsTab filters={filters} />
        </TabMenuHorizontal.Content>
        <TabMenuHorizontal.Content value='inasistencia'>
          <NoShowTab range={range} />
        </TabMenuHorizontal.Content>
        <TabMenuHorizontal.Content value='ocupacion'>
          <OccupancyTab range={range} />
        </TabMenuHorizontal.Content>
      </TabMenuHorizontal.Root>
    </>
  );
}
