import { DisputeReason } from '../models/dispute-models';

export const DISPUTE_TRANSACTION_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export const DISPUTE_REASONS: readonly { value: DisputeReason; label: string }[] = [
  { value: 'UNRECOGNIZED_TRANSACTION', label: 'I do not recognise this transaction' },
  { value: 'DUPLICATE_CHARGE', label: 'I was charged more than once' },
  { value: 'INCORRECT_AMOUNT', label: 'The amount is incorrect' },
  { value: 'GOODS_OR_SERVICES_NOT_RECEIVED', label: 'I did not receive the goods or services' },
  { value: 'OTHER', label: 'Other reason' },
];