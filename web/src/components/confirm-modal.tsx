import * as React from 'react';
import {
  RiAlertFill,
  RiCheckboxCircleFill,
  RiErrorWarningFill,
  RiInformationFill,
  RiLoader4Line,
  type RemixiconComponentType,
} from '@remixicon/react';

import * as Button from '@/components/ui/button';
import * as Modal from '@/components/ui/modal';
import { cn } from '@/lib/cn';

type ConfirmStatus = 'success' | 'error' | 'warning' | 'information';

const STATUS_STYLES: Record<ConfirmStatus, { box: string; icon: string; defaultIcon: RemixiconComponentType }> = {
  success: { box: 'bg-success-lighter', icon: 'text-success-base', defaultIcon: RiCheckboxCircleFill },
  error: { box: 'bg-error-lighter', icon: 'text-error-base', defaultIcon: RiErrorWarningFill },
  warning: { box: 'bg-warning-lighter', icon: 'text-warning-base', defaultIcon: RiAlertFill },
  information: { box: 'bg-information-lighter', icon: 'text-information-base', defaultIcon: RiInformationFill },
};

export interface ConfirmModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: React.ReactNode;
  /** Define color del icono y del botón principal (error → botón rojo). */
  status?: ConfirmStatus;
  icon?: RemixiconComponentType;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void | Promise<void>;
  /** Deshabilita los botones y muestra el spinner mientras se procesa. */
  isPending?: boolean;
  /** Contenido extra bajo la descripción (p. ej. un campo "motivo"). */
  children?: React.ReactNode;
  /** Bloquea el botón de confirmar (p. ej. mientras un campo obligatorio está vacío). */
  confirmDisabled?: boolean;
}

/** Modal de confirmación según el ejemplo modal-demo de AlignUI (icono en caja, texto y dos botones a ancho completo). */
export function ConfirmModal({
  open,
  onOpenChange,
  title,
  description,
  status = 'warning',
  icon,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  onConfirm,
  isPending = false,
  children,
  confirmDisabled = false,
}: ConfirmModalProps) {
  const styles = STATUS_STYLES[status];
  const Icon = icon ?? styles.defaultIcon;

  return (
    <Modal.Root open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <Modal.Content className='max-w-[440px]'>
        <Modal.Body className='flex items-start gap-4'>
          <div className={cn('flex size-10 shrink-0 items-center justify-center rounded-10', styles.box)}>
            <Icon className={cn('size-6', styles.icon)} />
          </div>
          <div className='flex min-w-0 flex-1 flex-col gap-1'>
            <div className='text-label-md text-text-strong-950'>{title}</div>
            {description && <div className='text-paragraph-sm text-text-sub-600'>{description}</div>}
            {children && <div className='mt-3'>{children}</div>}
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Modal.Close asChild>
            <Button.Root variant='neutral' mode='stroke' size='small' className='w-full' disabled={isPending}>
              {cancelLabel}
            </Button.Root>
          </Modal.Close>
          <Button.Root
            variant={status === 'error' ? 'error' : 'primary'}
            mode='filled'
            size='small'
            className='w-full'
            disabled={isPending || confirmDisabled}
            onClick={() => void onConfirm()}
          >
            {isPending && <Button.Icon as={RiLoader4Line} className='animate-spin' />}
            {confirmLabel}
          </Button.Root>
        </Modal.Footer>
      </Modal.Content>
    </Modal.Root>
  );
}
