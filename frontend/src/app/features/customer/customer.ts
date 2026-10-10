import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-customer',
  standalone: true,
  templateUrl: './customer.html',
  styleUrl: './customer.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class Customer {}
