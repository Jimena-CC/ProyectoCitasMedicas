import * as React from 'react';

import { notify } from '@/components/notify';

const DIALOG_SELECTOR = '[role="dialog"], [role="alertdialog"]';
/** Envoltura que Radix agrega al body para popovers y menús; esos no cuentan como modal. */
const POPPER_WRAPPER = '[data-radix-popper-content-wrapper]';

function opensModal(node: Node): boolean {
  if (!(node instanceof HTMLElement) || node.matches(POPPER_WRAPPER)) return false;
  return node.matches(DIALOG_SELECTOR) || node.querySelector(DIALOG_SELECTOR) !== null;
}

/**
 * Cierra los avisos visibles cuando se abre un modal. Los avisos van arriba al centro y, si siguen ahí al
 * abrir un modal, tapan su cabecera (p. ej. médico, fecha y hora de la cita a reprogramar). Los avisos que
 * se muestran con el modal ya abierto (errores al confirmar) no se tocan.
 *
 * Los modales de Radix se montan como hijos directos del body, por eso basta con observar ese nivel.
 */
export function ToastDismissOnModal() {
  React.useEffect(() => {
    const observer = new MutationObserver((mutations) => {
      const opened = mutations.some((mutation) => Array.from(mutation.addedNodes).some(opensModal));
      if (opened) notify.dismiss();
    });
    observer.observe(document.body, { childList: true });
    return () => observer.disconnect();
  }, []);

  return null;
}
