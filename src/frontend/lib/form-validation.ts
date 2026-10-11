import type { FormEvent } from 'react';

/** Keep native constraint checks while supplying Vietnamese validation messages. */
export function vietnameseValidation(event: FormEvent<HTMLFormElement>) {
  const input = event.target;
  if (!(input instanceof HTMLInputElement)) return;
  input.setCustomValidity('');
  const validity = input.validity;
  let message = '';
  if (validity.valueMissing) message = 'Vui lòng điền thông tin này.';
  else if (validity.typeMismatch && input.type === 'email')
    message = 'Email không hợp lệ. Vui lòng nhập địa chỉ có dạng ten@example.com.';
  else if (validity.tooShort)
    message = `${input.type === 'password' ? 'Mật khẩu' : 'Thông tin này'} cần ít nhất ${input.minLength} ký tự.`;
  else if (validity.tooLong) message = `Vui lòng nhập tối đa ${input.maxLength} ký tự.`;
  else if (validity.patternMismatch) message = input.title || 'Thông tin không đúng định dạng.';
  else if (validity.rangeUnderflow || validity.rangeOverflow) message = 'Giá trị nằm ngoài phạm vi cho phép.';
  else if (validity.badInput || !validity.valid) message = 'Thông tin không hợp lệ. Vui lòng kiểm tra lại.';
  input.setCustomValidity(message);
}

export function clearValidation(event: FormEvent<HTMLFormElement>) {
  if (event.target instanceof HTMLInputElement) event.target.setCustomValidity('');
}
