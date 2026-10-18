import {AsyncViewState} from '../../../../shared/models/async-state';
import {TransactionItem} from '../models/transaction-models';

export type TransactionDetailState = AsyncViewState<TransactionItem, 'not-found'>;
