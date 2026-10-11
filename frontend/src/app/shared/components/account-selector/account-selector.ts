import { ChangeDetectionStrategy, Component, ElementRef, HostListener, input, output, ViewChild } from '@angular/core';
import { AccountSummary } from '../../models/account-summary';
import { AccountCard } from '../account-card/account-card';

@Component({
  selector: 'app-account-selector', standalone: true, imports: [AccountCard],
  templateUrl: './account-selector.html', styleUrl: './account-selector.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AccountSelector {
  readonly accounts = input.required<readonly AccountSummary[]>();
  readonly selectedAccountId = input.required<string | null>();
  readonly selectionChange = output<string>();
  isOpen = false;
  activeIndex = 0;

  @ViewChild('trigger') private trigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('listbox') private listbox?: ElementRef<HTMLElement>;

  get selected(): AccountSummary | undefined {
    return this.accounts().find(account => account.id === this.selectedAccountId());
  }

  get selectedIndex(): number {
    return Math.max(0, this.accounts().findIndex(account => account.id === this.selectedAccountId()));
  }

  toggle(): void {
    this.isOpen ? this.close(true) : this.open();
  }

  open(): void {
    if (!this.accounts().length) return;
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
        this.activeIndex = (this.selectedIndex + delta + this.accounts().length) % this.accounts().length;
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
      this.activeIndex = event.key === 'Home' ? 0 : this.accounts().length - 1;
      this.focusOption(this.activeIndex);
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      const account = this.accounts()[this.activeIndex];
      if (account) this.select(account.id);
    }
  }

  select(id: string): void {
    this.selectionChange.emit(id);
    this.close(true);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.isOpen && !this.element.nativeElement.contains(event.target as Node)) this.close();
  }

  constructor(private readonly element: ElementRef<HTMLElement>) {}

  private move(delta: number): void {
    const length = this.accounts().length;
    if (!length) return;
    this.activeIndex = (this.activeIndex + delta + length) % length;
    this.focusOption(this.activeIndex);
  }

  private focusOption(index: number): void {
    this.listbox?.nativeElement.querySelector<HTMLElement>(`[data-option-index="${index}"]`)?.focus();
  }
}
