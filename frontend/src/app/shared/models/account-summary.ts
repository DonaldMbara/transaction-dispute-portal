export interface AccountSummary {
  id: string;
  accountType: 'MAIN' | 'SAVINGS';
  displayName: string;
  accountNumber: string | null;
  maskedNumber: string;
  balance: string;
  currency: string;
  primary: boolean;
}
