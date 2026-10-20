import * as Label from '@/components/ui/label';
import * as Switch from '@/components/ui/switch';

interface SwitchFieldProps {
  id: string;
  label: string;
  description?: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  disabled?: boolean;
}

/** Interruptor con etiqueta y descripción (ejemplos switch-with-label y modal-header). */
export function SwitchField({ id, label, description, checked, onCheckedChange, disabled }: SwitchFieldProps) {
  return (
    <div className='flex items-center gap-3.5 rounded-10 bg-bg-weak-50 px-3 py-2.5'>
      <div className='flex-1 space-y-0.5'>
        <Label.Root htmlFor={id} className='text-label-sm text-text-strong-950'>
          {label}
        </Label.Root>
        {description && <div className='text-paragraph-xs text-text-sub-600'>{description}</div>}
      </div>
      <Switch.Root id={id} checked={checked} onCheckedChange={onCheckedChange} disabled={disabled} />
    </div>
  );
}
