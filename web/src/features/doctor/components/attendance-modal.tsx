import * as React from 'react';
import { RiCheckboxCircleFill, RiUserUnfollowFill } from '@remixicon/react';

import { ConfirmModal } from '@/components/confirm-modal';
import { TextareaField } from '@/components/form-field';
import type { AgendaItem, AttendanceStatus } from '@/lib/api/types';
import { formatDocument, formatTime } from '@/lib/formatters';

export const NOTE_MAX_LENGTH = 250;

export interface AttendanceTarget {
  item: AgendaItem;
  status: AttendanceStatus;
}

interface AttendanceModalProps {
  target: AttendanceTarget | null;
  onOpenChange: (open: boolean) => void;
  note: string;
  onNoteChange: (note: string) => void;
  noteError: string | null;
  isPending: boolean;
  onConfirm: () => void | Promise<void>;
}

const COPY: Record<AttendanceStatus, { title: string; confirmLabel: string; description: (name: string, time: string) => string }> = {
  ATTENDED: {
    title: 'Registrar paciente atendido',
    confirmLabel: 'Marcar como atendido',
    description: (name, time) => `Confirma que atendiste a ${name} en la cita de las ${time}.`,
  },
  NO_SHOW: {
    title: 'Registrar inasistencia',
    confirmLabel: 'Marcar como no asistió',
    description: (name, time) => `Confirma que ${name} no se presentó a la cita de las ${time}.`,
  },
};

/**
 * Confirmación del registro de asistencia (modal-demo vía ConfirmModal): datos del paciente,
 * nota opcional con contador y botón de confirmar según el resultado elegido.
 */
export function AttendanceModal({ target, onOpenChange, note, onNoteChange, noteError, isPending, onConfirm }: AttendanceModalProps) {
  // Conserva el último objetivo para que el contenido no se vacíe durante la animación de cierre.
  const [shown, setShown] = React.useState<AttendanceTarget | null>(target);
  if (target && target !== shown) setShown(target);
  if (!shown) return null;

  const { item, status } = shown;
  const copy = COPY[status];
  const isAttended = status === 'ATTENDED';

  return (
    <ConfirmModal
      open={target !== null}
      onOpenChange={onOpenChange}
      title={copy.title}
      description={copy.description(item.patient.fullName, formatTime(item.startTime))}
      status={isAttended ? 'success' : 'warning'}
      icon={isAttended ? RiCheckboxCircleFill : RiUserUnfollowFill}
      confirmLabel={copy.confirmLabel}
      onConfirm={onConfirm}
      isPending={isPending}
    >
      <div className='flex flex-col gap-4'>
        <dl className='grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 rounded-10 bg-bg-weak-50 p-3 text-paragraph-sm'>
          <dt className='text-text-sub-600'>Paciente</dt>
          <dd className='text-label-sm text-text-strong-950'>{item.patient.fullName}</dd>
          <dt className='text-text-sub-600'>Documento</dt>
          <dd className='text-text-strong-950'>{formatDocument(item.patient.documentType, item.patient.documentNumber)}</dd>
          <dt className='text-text-sub-600'>Cita</dt>
          <dd className='text-text-strong-950'>
            {formatTime(item.startTime)} – {formatTime(item.endTime)} · {item.code}
          </dd>
          <dt className='text-text-sub-600'>Motivo</dt>
          <dd className='text-text-strong-950'>{item.visitReason}</dd>
        </dl>
        <TextareaField
          id='attendance-note'
          label='Nota'
          optional
          hint='Se guarda en el historial de la cita.'
          error={noteError}
          maxLength={NOTE_MAX_LENGTH}
          value={note}
          textareaProps={{
            placeholder: isAttended ? 'Ejemplo: paciente llegó con 10 minutos de retraso.' : 'Ejemplo: no contestó la llamada de confirmación.',
            rows: 3,
            disabled: isPending,
            onChange: (event) => onNoteChange(event.target.value),
          }}
        />
      </div>
    </ConfirmModal>
  );
}
