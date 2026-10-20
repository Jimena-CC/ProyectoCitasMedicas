import * as React from 'react';
import { RiFileList3Line } from '@remixicon/react';

import * as Button from '@/components/ui/button';
import * as Modal from '@/components/ui/modal';
import { AuditActionBadge } from '@/features/admin/audit/components/audit-action-badge';
import { AuditUserCell } from '@/features/admin/audit/components/audit-user-cell';
import { auditEntityLabel, auditSourceLabel } from '@/features/admin/audit/audit-labels';
import type { AuditEntry } from '@/lib/api';
import { formatLongDate, formatTime } from '@/lib/formatters';

interface AuditDetailModalProps {
  entry: AuditEntry | null;
  onClose: () => void;
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className='flex flex-col gap-1'>
      <span className='text-subheading-xs uppercase text-text-soft-400'>{label}</span>
      <div className='text-paragraph-sm text-text-strong-950'>{children}</div>
    </div>
  );
}

/** Detalle completo de un registro de auditoría (ejemplo modal-header). */
export function AuditDetailModal({ entry, onClose }: AuditDetailModalProps) {
  return (
    <Modal.Root open={entry !== null} onOpenChange={(open) => !open && onClose()}>
      <Modal.Content className='max-w-[560px]'>
        <Modal.Header
          icon={RiFileList3Line}
          title={entry ? `Registro #${entry.id}` : 'Registro'}
          description={entry ? `${formatLongDate(entry.registeredAt)}, ${formatTime(entry.registeredAt)}` : undefined}
        />
        {entry && (
          <Modal.Body className='flex flex-col gap-5'>
            <div className='grid grid-cols-2 gap-5'>
              <Row label='Usuario'>
                <AuditUserCell user={entry.user} />
              </Row>
              <Row label='Acción'>
                <AuditActionBadge action={entry.action} />
              </Row>
              <Row label='Entidad'>
                {auditEntityLabel(entry.entity)}
                {entry.entityId !== null && <span className='text-text-sub-600'> · #{entry.entityId}</span>}
              </Row>
              <Row label='Dirección de origen'>{auditSourceLabel(entry.sourceIp)}</Row>
            </div>
            <Row label='Detalle'>
              <p className='whitespace-pre-wrap break-words rounded-10 bg-bg-weak-50 p-3 text-paragraph-sm text-text-strong-950'>{entry.detail}</p>
            </Row>
          </Modal.Body>
        )}
        <Modal.Footer>
          <Modal.Close asChild>
            <Button.Root variant='neutral' mode='stroke' size='small' className='w-full'>
              Cerrar
            </Button.Root>
          </Modal.Close>
        </Modal.Footer>
      </Modal.Content>
    </Modal.Root>
  );
}
