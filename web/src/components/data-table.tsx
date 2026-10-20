import * as React from 'react';
import {
  RiArrowDownSFill,
  RiArrowLeftSLine,
  RiArrowRightSLine,
  RiArrowUpSFill,
  RiErrorWarningLine,
  RiExpandUpDownFill,
  type RemixiconComponentType,
} from '@remixicon/react';
import {
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type Column,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table';

import { EmptyState } from '@/components/empty-state';
import { Skeleton } from '@/components/loading-state';
import * as Button from '@/components/ui/button';
import * as Pagination from '@/components/ui/pagination';
import * as Table from '@/components/ui/table';
import { cn } from '@/lib/cn';

/** Paginación controlada por el servidor (p. ej. GET /admin/audit). `page` es 0-based. */
export interface ServerPagination {
  page: number;
  totalPages: number;
  totalElements?: number;
  onPageChange: (page: number) => void;
}

export interface DataTableProps<T> {
  columns: ColumnDef<T>[];
  data: T[] | undefined;
  isLoading?: boolean;
  /** Mensaje de error ya en español (getErrorMessage(error)). */
  errorMessage?: string | null;
  onRetry?: () => void;
  emptyTitle?: string;
  emptyDescription?: React.ReactNode;
  emptyIcon?: RemixiconComponentType;
  emptyAction?: React.ReactNode;
  /** Paginación en el cliente: filas por página. Ignorado si se pasa `pagination`. */
  pageSize?: number;
  pagination?: ServerPagination;
  sorting?: SortingState;
  onSortingChange?: (sorting: SortingState) => void;
  getRowId?: (row: T, index: number) => string;
  onRowClick?: (row: T) => void;
  /** Filas de esqueleto mientras carga. */
  skeletonRows?: number;
  className?: string;
}

const getSortingIcon = (state: 'asc' | 'desc' | false) => {
  if (state === 'asc') return <RiArrowUpSFill className='size-5 text-text-sub-600' />;
  if (state === 'desc') return <RiArrowDownSFill className='size-5 text-text-sub-600' />;
  return <RiExpandUpDownFill className='size-5 text-text-sub-600' />;
};

/**
 * Cabecera ordenable al estilo data-table-demo:
 *   header: ({ column }) => <SortableHeader column={column}>Paciente</SortableHeader>
 */
export function SortableHeader<T>({ column, children, className }: { column: Column<T, unknown>; children: React.ReactNode; className?: string }) {
  return (
    <div className={cn('flex items-center gap-0.5', className)}>
      {children}
      <button type='button' onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')} aria-label='Ordenar'>
        {getSortingIcon(column.getIsSorted())}
      </button>
    </div>
  );
}

/** Páginas visibles (1-based) con "…" cuando hay muchas. */
function visiblePages(current: number, total: number): (number | '…')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const pages = new Set<number>([1, total, current, current - 1, current + 1]);
  const sorted = [...pages].filter((p) => p >= 1 && p <= total).sort((a, b) => a - b);
  const result: (number | '…')[] = [];
  sorted.forEach((page, index) => {
    if (index > 0 && page - sorted[index - 1] > 1) result.push('…');
    result.push(page);
  });
  return result;
}

/**
 * Tabla de datos sobre @tanstack/react-table y Table de AlignUI (ejemplo data-table-demo):
 * cabecera, RowDivider entre filas, estados de carga/vacío/error y paginación AlignUI.
 */
export function DataTable<T>({
  columns,
  data,
  isLoading = false,
  errorMessage,
  onRetry,
  emptyTitle = 'No hay resultados',
  emptyDescription,
  emptyIcon,
  emptyAction,
  pageSize,
  pagination,
  sorting,
  onSortingChange,
  getRowId,
  onRowClick,
  skeletonRows = 5,
  className,
}: DataTableProps<T>) {
  const [internalSorting, setInternalSorting] = React.useState<SortingState>([]);
  const sortingState = sorting ?? internalSorting;
  const rows = React.useMemo(() => data ?? [], [data]);
  const clientPaginated = pagination === undefined && pageSize !== undefined;

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting: sortingState },
    onSortingChange: (updater) => {
      const next = typeof updater === 'function' ? updater(sortingState) : updater;
      if (onSortingChange) onSortingChange(next);
      else setInternalSorting(next);
    },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: clientPaginated ? getPaginationRowModel() : undefined,
    initialState: clientPaginated ? { pagination: { pageIndex: 0, pageSize } } : undefined,
    getRowId,
    manualPagination: !clientPaginated,
  });

  const columnCount = table.getAllLeafColumns().length;
  const pageRows = table.getRowModel().rows;

  // Datos para el pie: funciona tanto con paginación en cliente como en servidor.
  const footer = (() => {
    if (pagination) {
      return {
        current: pagination.page + 1,
        total: Math.max(pagination.totalPages, 1),
        goTo: (page: number) => pagination.onPageChange(page - 1),
        label:
          pagination.totalElements !== undefined
            ? `${pagination.totalElements} ${pagination.totalElements === 1 ? 'registro' : 'registros'}`
            : null,
      };
    }
    if (clientPaginated) {
      const { pageIndex } = table.getState().pagination;
      const start = rows.length === 0 ? 0 : pageIndex * (pageSize ?? rows.length) + 1;
      const end = Math.min(rows.length, (pageIndex + 1) * (pageSize ?? rows.length));
      return {
        current: pageIndex + 1,
        total: Math.max(table.getPageCount(), 1),
        goTo: (page: number) => table.setPageIndex(page - 1),
        label: rows.length === 0 ? null : `Mostrando ${start}–${end} de ${rows.length}`,
      };
    }
    return null;
  })();

  const showFooter = footer !== null && !isLoading && !errorMessage && rows.length > 0 && footer.total > 1;

  return (
    <div className={cn('flex flex-col gap-4', className)}>
      <Table.Root>
        <Table.Header>
          {table.getHeaderGroups().map((headerGroup) => (
            <Table.Row key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <Table.Head key={header.id} style={{ width: header.getSize() !== 150 ? header.getSize() : undefined }}>
                  {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                </Table.Head>
              ))}
            </Table.Row>
          ))}
        </Table.Header>
        <Table.Body>
          {isLoading ? (
            Array.from({ length: skeletonRows }).map((_, index) => (
              <React.Fragment key={`skeleton-${index}`}>
                <Table.Row>
                  {Array.from({ length: columnCount }).map((__, cellIndex) => (
                    <Table.Cell key={cellIndex}>
                      <Skeleton className={cn('h-4', cellIndex === 0 ? 'w-3/4' : 'w-1/2')} />
                    </Table.Cell>
                  ))}
                </Table.Row>
                {index < skeletonRows - 1 && <Table.RowDivider />}
              </React.Fragment>
            ))
          ) : errorMessage ? (
            <Table.Row>
              <Table.Cell colSpan={columnCount}>
                <EmptyState
                  size='small'
                  icon={RiErrorWarningLine}
                  title='No pudimos cargar la información'
                  description={errorMessage}
                  action={
                    onRetry && (
                      <Button.Root variant='neutral' mode='stroke' size='xsmall' onClick={onRetry}>
                        Reintentar
                      </Button.Root>
                    )
                  }
                />
              </Table.Cell>
            </Table.Row>
          ) : pageRows.length === 0 ? (
            <Table.Row>
              <Table.Cell colSpan={columnCount}>
                <EmptyState size='small' icon={emptyIcon} title={emptyTitle} description={emptyDescription} action={emptyAction} />
              </Table.Cell>
            </Table.Row>
          ) : (
            pageRows.map((row, index, all) => (
              <React.Fragment key={row.id}>
                <Table.Row
                  data-state={row.getIsSelected() ? 'selected' : undefined}
                  className={cn(onRowClick && 'cursor-pointer')}
                  onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                >
                  {row.getVisibleCells().map((cell) => (
                    <Table.Cell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</Table.Cell>
                  ))}
                </Table.Row>
                {index < all.length - 1 && <Table.RowDivider />}
              </React.Fragment>
            ))
          )}
        </Table.Body>
      </Table.Root>

      {showFooter && footer && (
        <div className='flex flex-wrap items-center justify-between gap-3'>
          <span className='text-paragraph-sm text-text-sub-600'>
            {footer.label ?? `Página ${footer.current} de ${footer.total}`}
          </span>
          <Pagination.Root>
            <Pagination.NavButton disabled={footer.current <= 1} onClick={() => footer.goTo(footer.current - 1)} aria-label='Página anterior'>
              <Pagination.NavIcon as={RiArrowLeftSLine} />
            </Pagination.NavButton>
            {visiblePages(footer.current, footer.total).map((page, index) =>
              page === '…' ? (
                <Pagination.Item key={`gap-${index}`} disabled>
                  …
                </Pagination.Item>
              ) : (
                <Pagination.Item key={page} current={page === footer.current} onClick={() => footer.goTo(page)}>
                  {page}
                </Pagination.Item>
              ),
            )}
            <Pagination.NavButton
              disabled={footer.current >= footer.total}
              onClick={() => footer.goTo(footer.current + 1)}
              aria-label='Página siguiente'
            >
              <Pagination.NavIcon as={RiArrowRightSLine} />
            </Pagination.NavButton>
          </Pagination.Root>
        </div>
      )}
    </div>
  );
}
