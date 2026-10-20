import * as React from 'react';
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

/** Archivo CSV ya armado en el navegador (por ejemplo, con los filtros de especialidad o sede). */
export interface LocalCsvFile {
  content: string;
  filename: string;
}

interface ExportCsvButtonProps {
  kind: ReportKind;
  range: ReportRange;
  /** Si se indica, se descarga este archivo en vez de pedirlo a la API. */
  localFile?: () => LocalCsvFile;
  disabled?: boolean;
}

/** Descarga el CSV del reporte (RF-36). Usa el nombre del Content-Disposition si la API lo envía. */
export function ExportCsvButton({ kind, range, localFile, disabled = false }: ExportCsvButtonProps) {
  // `isPending` se actualiza después del render: el ref evita una segunda descarga mientras la primera sigue en curso.
  const inFlight = React.useRef(false);

  const exportMutation = useMutation({
    mutationFn: async () => {
      if (localFile) {
        const file = localFile();
        downloadBlob({ blob: new Blob([file.content], { type: 'text/csv;charset=utf-8' }), filename: file.filename }, file.filename);
        return;
      }
      const result = await reportsApi.exportCsv(kind, range);
      downloadBlob(result, `${FILE_PREFIX[kind]}_${range.from}_${range.to}.csv`);
    },
    onSuccess: () => notify.success('El archivo CSV se descargó correctamente.'),
    onError: (error) => notify.fromError(error, 'No pudimos generar el archivo. Inténtalo de nuevo.'),
    onSettled: () => {
      inFlight.current = false;
    },
  });

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    // El segundo clic de un doble clic (detail > 1) se ignora: el CSV local se descarga al instante y el ref ya se liberó.
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
      <Button.Icon as={exportMutation.isPending ? RiLoader4Line : RiDownload2Line} className={exportMutation.isPending ? 'animate-spin' : undefined} />
      {exportMutation.isPending ? 'Generando…' : 'Exportar CSV'}
    </Button.Root>
  );
}
