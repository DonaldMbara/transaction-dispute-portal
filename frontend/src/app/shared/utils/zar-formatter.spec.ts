import {formatZar} from './zar-formatter';

describe('formatZar', () => {
  it('formats decimal strings without floating point or locale dependence', () => {
    expect(formatZar('12345678901234567.80')).toBe('R 12 345 678 901 234 567.80');
    expect(formatZar('0')).toBe('R 0.00');
    expect(formatZar('0001234.5')).toBe('R 1 234.50');
  });
});
