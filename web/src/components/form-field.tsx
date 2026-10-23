import * as React from 'react';
import { RiErrorWarningFill, RiInformationFill, type RemixiconComponentType } from '@remixicon/react';

import * as Hint from '@/components/ui/hint';
import * as Input from '@/components/ui/input';
import * as Label from '@/components/ui/label';
import * as Select from '@/components/ui/select';
import * as Textarea from '@/components/ui/textarea';
import { cn } from '@/lib/cn';

export interface FormFieldProps {
  label: React.ReactNode;
  /** id del control; se usa en htmlFor. */
  htmlFor?: string;
  required?: boolean;
  /** Muestra "(Opcional)" junto a la etiqueta. */
  optional?: boolean;
  /** Texto de ayuda (se oculta cuando hay error). */
  hint?: React.ReactNode;
  /** Mensaje de error en español; cuando existe se pinta en rojo. */
  error?: string | null;
  className?: string;
  children: React.ReactNode;
}

/**
 * Estructura estándar de campo AlignUI: Label + control + Hint (ejemplo input-label-hint / hint-has-error).
 * El control hijo debe recibir `hasError` por su cuenta (o usar TextField/SelectField/TextareaField).
 */
export function FormField({ label, htmlFor, required, optional, hint, error, className, children }: FormFieldProps) {
  return (
    <div className={cn('flex flex-col gap-1', className)}>
      <Label.Root htmlFor={htmlFor}>
        {label}
        {required && <Label.Asterisk />}
        {optional && <Label.Sub>(Opcional)</Label.Sub>}
      </Label.Root>
      {children}
      {error ? (
        <Hint.Root hasError>
          <Hint.Icon as={RiErrorWarningFill} />
          {error}
        </Hint.Root>
      ) : hint ? (
        <Hint.Root>
          <Hint.Icon as={RiInformationFill} />
          {hint}
        </Hint.Root>
      ) : null}
    </div>
  );
}

type FieldShellProps = Omit<FormFieldProps, 'children' | 'htmlFor'> & { id: string };

export interface TextFieldProps extends FieldShellProps {
  icon?: RemixiconComponentType;
  size?: 'medium' | 'small' | 'xsmall';
  inputProps?: Omit<React.InputHTMLAttributes<HTMLInputElement>, 'id' | 'size'>;
  /** Elemento al final del input (botón de ojo, texto...). */
  trailing?: React.ReactNode;
}

/** Campo de texto completo: FormField + Input.Root con icono opcional. */
export function TextField({ id, icon: Icon, size = 'medium', inputProps, trailing, error, ...field }: TextFieldProps) {
  return (
    <FormField htmlFor={id} error={error} {...field}>
      <Input.Root size={size} hasError={Boolean(error)}>
        <Input.Wrapper>
          {Icon && <Input.Icon as={Icon} />}
          <Input.Input id={id} {...inputProps} />
          {trailing}
        </Input.Wrapper>
      </Input.Root>
    </FormField>
  );
}

export interface SelectOption {
  value: string;
  label: React.ReactNode;
  disabled?: boolean;
}

export interface SelectFieldProps extends FieldShellProps {
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  placeholder?: string;
  options: SelectOption[];
  size?: 'medium' | 'small' | 'xsmall';
  disabled?: boolean;
}

/** Campo de selección: FormField + Select AlignUI (ejemplo select-demo). */
export function SelectField({
  id,
  value,
  defaultValue,
  onValueChange,
  placeholder = 'Selecciona una opción',
  options,
  size = 'medium',
  disabled,
  error,
  ...field
}: SelectFieldProps) {
  return (
    <FormField htmlFor={id} error={error} {...field}>
      <Select.Root value={value} defaultValue={defaultValue} onValueChange={onValueChange} size={size} hasError={Boolean(error)} disabled={disabled}>
        <Select.Trigger id={id}>
          <Select.Value placeholder={placeholder} />
        </Select.Trigger>
        <Select.Content>
          {options.map((option) => (
            <Select.Item key={option.value} value={option.value} disabled={option.disabled}>
              {option.label}
            </Select.Item>
          ))}
        </Select.Content>
      </Select.Root>
    </FormField>
  );
}

export interface TextareaFieldProps extends FieldShellProps {
  textareaProps?: Omit<React.TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'>;
  /** Muestra el contador "n/max" (ejemplo textarea con CharCounter). */
  maxLength?: number;
  value?: string;
}

/** Área de texto: FormField + Textarea AlignUI con contador opcional. */
export function TextareaField({ id, textareaProps, maxLength, value, error, ...field }: TextareaFieldProps) {
  const current = value ?? (typeof textareaProps?.value === 'string' ? textareaProps.value : '');
  return (
    <FormField htmlFor={id} error={error} {...field}>
      <Textarea.Root id={id} hasError={Boolean(error)} maxLength={maxLength} value={value} {...textareaProps}>
        {maxLength !== undefined && <Textarea.CharCounter current={current.length} max={maxLength} />}
      </Textarea.Root>
    </FormField>
  );
}
