export type DisputeStatus = 'OPEN' | 'UNDER_REVIEW' | 'ACCEPTED' | 'REJECTED';
export type DisputeReason =
  | 'UNRECOGNIZED_TRANSACTION'
  | 'DUPLICATE_CHARGE'
  | 'INCORRECT_AMOUNT'
  | 'GOODS_OR_SERVICES_NOT_RECEIVED'
  | 'OTHER';
export type TransactionDirection = 'IN' | 'OUT';
