import * as React from 'react';
import { RiErrorWarningFill, RiLoader4Line, type RemixiconComponentType } from '@remixicon/react';

import * as Alert from '@/components/ui/alert';
import * as Button from '@/components/ui/button';
import * as Modal from '@/components/ui/modal';
import { cn } from '@/lib/cn';

interface FormModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  icon: RemixiconComponentType;
  title: string;
  description?: string;
  /** Mensaje general de error (Alert dentro del cuerpo). */
  errorMessage?: string | null;
  submitLabel: string;
  cancelLabel?: string;
  isPending?: boolean;
  onSubmit: () => void | Promise<void>;
  className?: string;
  children: React.ReactNode;
}

/**
 * Modal de formulario según el ejemplo modal-header de AlignUI:
 * cabecera con icono, cuerpo con los campos y pie con dos botones a ancho completo.
 */
export function FormModal({
  open,
  onOpenChange,
  icon,
  title,
  description,
  errorMessage,
  submitLabel,
  cancelLabel = 'Cancelar',
  isPending = false,
  onSubmit,
  className,
  children,
}: FormModalProps) {
  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!isPending) void onSubmit();
  };

  return (
    <Modal.Root open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <Modal.Content className={cn('max-w-[480px]', className)}>
        <Modal.Header icon={icon} title={title} description={description} />
        <form onSubmit={handleSubmit} noValidate>
          <Modal.Body className='flex flex-col gap-4'>
            {errorMessage && (
              <Alert.Root variant='lighter' status='error' size='small'>
                <Alert.Icon as={RiErrorWarningFill} />
                {errorMessage}
              </Alert.Root>
            )}
            {children}
          </Modal.Body>
          <Modal.Footer>
            <Modal.Close asChild>
              <Button.Root type='button' variant='neutral' mode='stroke' size='small' className='w-full' disabled={isPending}>
                {cancelLabel}
              </Button.Root>
            </Modal.Close>
            <Button.Root type='submit' variant='primary' mode='filled' size='small' className='w-full' disabled={isPending}>
              {isPending && <Button.Icon as={RiLoader4Line} className='animate-spin' />}
              {submitLabel}
            </Button.Root>
          </Modal.Footer>
        </form>
      </Modal.Content>
    </Modal.Root>
  );
}
