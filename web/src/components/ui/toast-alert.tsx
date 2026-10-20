import * as React from 'react';
import {
  RiAlertFill,
  RiCheckboxCircleFill,
  RiErrorWarningFill,
  RiInformationFill,
  RiMagicFill,
} from '@remixicon/react';

import * as Alert from '@/components/ui/alert';
import { toast } from '@/components/ui/toast';

type AlertToastProps = {
  t: string | number;
  status?: React.ComponentPropsWithoutRef<typeof Alert.Root>['status'];
  variant?: React.ComponentPropsWithoutRef<typeof Alert.Root>['variant'];
  message: string;
  dismissable?: boolean;
  icon?: React.ElementType;
};

const AlertToast = React.forwardRef<
  React.ComponentRef<typeof Alert.Root>,
  AlertToastProps
>(
  (
    {
      t,
      status = 'feature',
      variant = 'stroke',
      message,
      dismissable = true,
      icon,
    },
    forwardedRef,
  ) => {
    let Icon: React.ElementType;

    if (icon) {
      Icon = icon;
    } else {
      switch (status) {
        case 'success':
          Icon = RiCheckboxCircleFill;
          break;
        case 'warning':
          Icon = RiAlertFill;
          break;
        case 'error':
          Icon = RiErrorWarningFill;
          break;
        case 'information':
          Icon = RiInformationFill;
          break;
        case 'feature':
          Icon = RiMagicFill;
          break;
        default:
          Icon = RiErrorWarningFill;
          break;
      }
    }

    return (
      <Alert.Root
        ref={forwardedRef}
        status={status}
        variant={variant}
        size='small'
        className='w-[360px]'
      >
        <Alert.Icon as={Icon} />
        {message}
        {dismissable && (
          <button type='button' onClick={() => toast.dismiss(t)}>
            <Alert.CloseIcon />
          </button>
        )}
      </Alert.Root>
    );
  },
);
AlertToast.displayName = 'AlertToast';

export { AlertToast as Root };
