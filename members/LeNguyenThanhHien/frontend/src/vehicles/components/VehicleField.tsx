import type { InputHTMLAttributes } from 'react';

export default function Field({ label, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label>
      {label}
      <input
        data-validation-label={label.toLowerCase()}
        {...props}
        onKeyDown={event => {
          if (props.type === 'number' && ['ArrowUp', 'ArrowDown'].includes(event.key)) event.preventDefault();
        }}
      />
    </label>
  );
}
