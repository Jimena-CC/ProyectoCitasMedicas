import * as React from 'react';
import { RiFileExcel2Line, RiLoader4Line } from '@remixicon/react';
import { useMutation } from '@tanstack/react-query';

import { notify } from '@/components/notify';
import * as Button from '@/components/ui/button';
import { downloadBlob, reportsApi, type ReportKind, type ReportRange } from '@/lib/api';
import type { ExportFilters } from '@/lib/api/reports';

const FILE_PREFIX: Record<ReportKind, string> = {
  appointments: 'reporte-citas',
  'no-show-rate': 'reporte-inasistencia',
  occupancy: 'reporte-ocupacion',
};

interface ExportXlsxButtonProps {
  kind: ReportKind;
  range: ReportRange;
  /** Filtros de especialidad y sede; la API los aplica solo al reporte de citas. */
  filters?: ExportFilters;
  disabled?: boolean;
}

/** Descarga el reporte como libro de Excel (.xlsx) generado por la API con Apache POI (RF-36). */
export function ExportXlsxButton({ kind, range, filters, disabled = false }: ExportXlsxButtonProps) {
  // Igual que en el CSV: el ref evita una segunda descarga mientras la primera sigue en curso.
  const inFlight = React.useRef(false);

  const exportMutation = useMutation({
    mutationFn: async () => {
      const result = await reportsApi.exportXlsx(kind, range, filters);
      downloadBlob(result, `${FILE_PREFIX[kind]}_${range.from}_${range.to}.xlsx`);
    },
    onSuccess: () => notify.success('El archivo de Excel se descargó correctamente.'),
    onError: (error) => notify.fromError(error, 'No pudimos generar el archivo. Inténtalo de nuevo.'),
    onSettled: () => {
      inFlight.current = false;
    },
  });

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    if (event.detail > 1 || inFlight.current) return;
    inFlight.current = true;
    exportMutation.mutate();
  };

  return (
    <Button.Root
      type='button'
      variant='neutral'
      mode='stroke'
      size='small'
      disabled={disabled || exportMutation.isPending}
      onClick={handleClick}
    >
      <Button.Icon as={exportMutation.isPending ? RiLoader4Line : RiFileExcel2Line} className={exportMutation.isPending ? 'animate-spin' : undefined} />
      {exportMutation.isPending ? 'Generando…' : 'Exportar Excel'}
    </Button.Root>
  );
}
