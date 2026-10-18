import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  input,
  output,
  ViewChild,
} from '@angular/core';
import {AccountSummary} from '../../models/account-summary';
import {AccountCard} from '../account-card/account-card';

@Component({
  selector: 'app-account-selector',
  standalone: true,
  imports: [AccountCard],
  templateUrl: './account-selector.html',
  styleUrl: './account-selector.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountSelector {
  readonly accounts = input.required<readonly AccountSummary[]>();
  readonly selectedAccountId = input.required<string | null>();
  /** When true, prepends an "All your accounts" option that emits null on selection. */
  readonly showAllAccounts = input(false);
  /** Emits the selected account id, or null when "All your accounts" is selected. */
  readonly selectionChange = output<string | null>();
  isOpen = false;
  activeIndex = 0;
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  @ViewChild('trigger') private trigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('listbox') private listbox?: ElementRef<HTMLElement>;

  get selected(): AccountSummary | undefined {
    return this.accounts().find((account) => account.id === this.selectedAccountId());
  }

  get isAllAccountsSelected(): boolean {
    return this.showAllAccounts() && this.selectedAccountId() === null;
  }

  /** Total number of options including the optional "All your accounts" entry. */
  get totalOptions(): number {
    return this.accounts().length + (this.showAllAccounts() ? 1 : 0);
  }

  /** The index offset for real accounts (1 when showAllAccounts, 0 otherwise). */
  get accountOffset(): number {
    return this.showAllAccounts() ? 1 : 0;
  }

  get selectedIndex(): number {
    if (this.showAllAccounts() && this.selectedAccountId() === null) return 0;
    const accountIndex = this.accounts().findIndex(
      (account) => account.id === this.selectedAccountId(),
    );
    return Math.max(
      this.accountOffset,
      accountIndex < 0 ? this.accountOffset : accountIndex + this.accountOffset,
    );
  }

  toggle(): void {
    if (this.isOpen) this.close(true);
    else this.open();
  }

  open(): void {
    if (!this.totalOptions) return;
    this.activeIndex = this.selectedIndex;
    this.isOpen = true;
    setTimeout(() => this.focusOption(this.activeIndex));
  }

  close(returnFocus = false): void {
    this.isOpen = false;
    if (returnFocus) setTimeout(() => this.trigger?.nativeElement.focus());
  }

  onTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.toggle();
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!this.isOpen) {
        const delta = event.key === 'ArrowDown' ? 1 : -1;
        this.activeIndex = (this.selectedIndex + delta + this.totalOptions) % this.totalOptions;
        this.isOpen = true;
        setTimeout(() => this.focusOption(this.activeIndex));
      } else this.move(event.key === 'ArrowDown' ? 1 : -1);
    }
  }

  onListboxKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close(true);
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.move(event.key === 'ArrowDown' ? 1 : -1);
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault();
      this.activeIndex = event.key === 'Home' ? 0 : this.totalOptions - 1;
      this.focusOption(this.activeIndex);
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.selectByIndex(this.activeIndex);
    }
  }

  select(id: string): void {
    this.selectionChange.emit(id);
    this.close(true);
  }

  selectAll(): void {
    this.selectionChange.emit(null);
    this.close(true);
  }

  selectByIndex(index: number): void {
    if (this.showAllAccounts() && index === 0) {
      this.selectAll();
    } else {
      const account = this.accounts()[index - this.accountOffset];
      if (account) this.select(account.id);
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.isOpen && !this.element.nativeElement.contains(event.target as Node)) this.close();
  }

  private move(delta: number): void {
    const length = this.totalOptions;
    if (!length) return;
    this.activeIndex = (this.activeIndex + delta + length) % length;
    this.focusOption(this.activeIndex);
  }

  private focusOption(index: number): void {
    this.listbox?.nativeElement
      .querySelector<HTMLElement>(`[data-option-index="${index}"]`)
      ?.focus();
  }
}
