import { RiDownload2Line, RiLoader4Line } from '@remixicon/react';
import { useMutation } from '@tanstack/react-query';

import { notify } from '@/components/notify';
import * as Button from '@/components/ui/button';
import { downloadBlob, reportsApi, type ReportKind, type ReportRange } from '@/lib/api';

const FILE_PREFIX: Record<ReportKind, string> = {
  appointments: 'reporte-citas',
  'no-show-rate': 'reporte-inasistencia',
  occupancy: 'reporte-ocupacion',
};

interface ExportCsvButtonProps {
  kind: ReportKind;
  range: ReportRange;
  disabled?: boolean;
}

/** Descarga el CSV del reporte (RF-36). Usa el nombre del Content-Disposition si la API lo envía. */
export function ExportCsvButton({ kind, range, disabled = false }: ExportCsvButtonProps) {
  const exportMutation = useMutation({
    mutationFn: async () => {
      const result = await reportsApi.exportCsv(kind, range);
      downloadBlob(result, `${FILE_PREFIX[kind]}_${range.from}_${range.to}.csv`);
    },
    onSuccess: () => notify.success('El archivo CSV se descargó correctamente.'),
    onError: (error) => notify.fromError(error, 'No pudimos generar el archivo. Inténtalo de nuevo.'),
  });

  return (
    <Button.Root
      type='button'
      variant='neutral'
      mode='stroke'
      size='small'
      disabled={disabled || exportMutation.isPending}
      onClick={() => exportMutation.mutate()}
    >
      <Button.Icon as={exportMutation.isPending ? RiLoader4Line : RiDownload2Line} className={exportMutation.isPending ? 'animate-spin' : undefined} />
      {exportMutation.isPending ? 'Generando…' : 'Exportar CSV'}
    </Button.Root>
  );
}
