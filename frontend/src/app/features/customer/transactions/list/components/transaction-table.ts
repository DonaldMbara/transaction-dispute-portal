import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '../../../../../shared/components/button/button';
import {
  SortField,
  TransactionPage,
} from '../../models/transaction-models';
import { TransactionListPresentation } from '../presentation/transaction-presentation';

@Component({
  selector: 'app-transaction-table',
  standalone: true,
  imports: [RouterLink, Button],
  templateUrl: './transaction-table.html',
  styleUrl: './transaction-table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionTable {
  readonly page = input.required<TransactionPage>();
  readonly pageNumber = input.required<number>();
  readonly size = input.required<string>();
  readonly returnTo = input.required<string>();
  readonly presentation = input.required<TransactionListPresentation>();
  readonly expandedId = signal<string | null>(null);
  readonly sortChange = output<Exclude<SortField, 'reference'>>();
  readonly pageSizeChange = output<string>();
  readonly previousPage = output<void>();
  readonly nextPage = output<void>();

  isExpanded(id: string): boolean {
    return this.expandedId() === id;
  }

  toggleExpanded(id: string): void {
    this.expandedId.update((current) => (current === id ? null : id));
  }

  onRowClick(event: MouseEvent, id: string): void {
    if ((event.target as HTMLElement | null)?.closest('a, button')) return;
    this.toggleExpanded(id);
  }
}