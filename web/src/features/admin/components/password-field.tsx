import * as React from 'react';
import { RiEyeLine, RiEyeOffLine, RiLock2Line } from '@remixicon/react';

import { TextField } from '@/components/form-field';

interface PasswordFieldProps {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string | null;
  hint?: React.ReactNode;
  disabled?: boolean;
  autoComplete?: string;
}

/** Campo de contraseña con botón para mostrarla (ejemplo input-password). */
export function PasswordField({ id, label, value, onChange, error, hint, disabled, autoComplete = 'new-password' }: PasswordFieldProps) {
  const [visible, setVisible] = React.useState(false);
  return (
    <TextField
      id={id}
      label={label}
      required
      icon={RiLock2Line}
      error={error}
      hint={hint}
      inputProps={{
        type: visible ? 'text' : 'password',
        autoComplete,
        placeholder: '••••••••••',
        value,
        onChange: (e) => onChange(e.target.value),
        disabled,
        maxLength: 72,
      }}
      trailing={
        <button type='button' onClick={() => setVisible((v) => !v)} aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}>
          {visible ? (
            <RiEyeOffLine className='size-5 text-text-soft-400 group-has-[disabled]:text-text-disabled-300' />
          ) : (
            <RiEyeLine className='size-5 text-text-soft-400 group-has-[disabled]:text-text-disabled-300' />
          )}
        </button>
      }
    />
  );
}
