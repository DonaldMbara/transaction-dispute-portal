export function formatZar(amount: string): string {
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(amount);
  if (!match) throw new Error('Invalid account balance returned by the service.');
  const whole = match[1].replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  const fraction = (match[2] ?? '').padEnd(2, '0');
  return `R ${whole}.${fraction}`;
}
