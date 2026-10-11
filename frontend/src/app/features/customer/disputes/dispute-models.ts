export type DisputeReason = 'UNRECOGNIZED_TRANSACTION' | 'DUPLICATE_CHARGE' | 'INCORRECT_AMOUNT' | 'GOODS_OR_SERVICES_NOT_RECEIVED' | 'OTHER';
export interface SubmitDisputeRequest { transactionId: string; reason: DisputeReason; description: string; }
export interface DisputeSubmission { id: string; transactionId: string; reason: DisputeReason; description: string; status: 'OPEN'; createdAt: string; }
