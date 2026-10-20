import { RiSearchLine } from '@remixicon/react';

import * as Input from '@/components/ui/input';
import { cn } from '@/lib/cn';

interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  id?: string;
}

/** Buscador local con icono de lupa (ejemplo input-demo con Input.Icon). */
export function SearchInput({ value, onChange, placeholder = 'Buscar…', className, id }: SearchInputProps) {
  return (
    <div className={cn('w-full max-w-[320px]', className)}>
      <Input.Root size='small'>
        <Input.Wrapper>
          <Input.Icon as={RiSearchLine} />
          <Input.Input id={id} type='search' placeholder={placeholder} value={value} onChange={(e) => onChange(e.target.value)} aria-label={placeholder} />
        </Input.Wrapper>
      </Input.Root>
    </div>
  );
}
