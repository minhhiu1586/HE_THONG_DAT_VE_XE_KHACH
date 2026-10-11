import type { FormEvent } from 'react';

/** Keep native constraint checks while supplying Vietnamese validation messages. */
export function vietnameseValidation(event: FormEvent<HTMLFormElement>) {
  const input = event.target;
  if (!(input instanceof HTMLInputElement || input instanceof HTMLSelectElement || input instanceof HTMLTextAreaElement)) return;
  input.setCustomValidity('');
  const validity = input.validity;
  let message = '';
  const field = input.dataset.validationLabel;
  if (validity.valueMissing) message = field ? `Vui lòng ${input instanceof HTMLSelectElement ? 'chọn' : 'nhập'} ${field}.` : 'Vui lòng điền thông tin này.';
  else if (validity.typeMismatch && input instanceof HTMLInputElement && input.type === 'email')
    message = 'Email không hợp lệ. Vui lòng nhập địa chỉ có dạng ten@example.com.';
  else if (validity.tooShort && input instanceof HTMLInputElement)
    message = `${input.type === 'password' ? 'Mật khẩu' : 'Thông tin này'} cần ít nhất ${input.minLength} ký tự.`;
  else if (validity.tooLong && !(input instanceof HTMLSelectElement)) message = `Vui lòng nhập tối đa ${input.maxLength} ký tự.`;
  else if (validity.patternMismatch) message = input.title || 'Thông tin không đúng định dạng.';
  else if (validity.rangeUnderflow && input instanceof HTMLInputElement) message = `Vui lòng nhập ${field || 'giá trị'} lớn hơn hoặc bằng ${input.min}.`;
  else if (validity.rangeOverflow && input instanceof HTMLInputElement) message = `Vui lòng nhập ${field || 'giá trị'} nhỏ hơn hoặc bằng ${input.max}.`;
  else if (validity.stepMismatch && input instanceof HTMLInputElement) message = `Vui lòng nhập ${field || 'giá trị'} theo bước ${input.step || '1'}.`;
  else if (validity.badInput || !validity.valid) message = 'Thông tin không hợp lệ. Vui lòng kiểm tra lại.';
  input.setCustomValidity(message);
}

export function clearValidation(event: FormEvent<HTMLFormElement>) {
  if (event.target instanceof HTMLInputElement || event.target instanceof HTMLSelectElement || event.target instanceof HTMLTextAreaElement) event.target.setCustomValidity('');
}
