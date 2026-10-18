import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {Button} from '../../../../shared/components/button/button';
import {LoadingSpinner} from '../../../../shared/components/loading-spinner/loading-spinner';
import {DisputeListItem, DisputeStatus} from '../models/dispute-models';
import {DisputeListResultsPresentation} from '../interfaces/dispute-list-results.interface';

@Component({
  selector: 'app-dispute-list-results',
  standalone: true,
  imports: [DatePipe, RouterLink, Button, LoadingSpinner],
  templateUrl: './dispute-list-results.html',
  styleUrl: './dispute-list-results.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisputeListResults {
  readonly items = input.required<readonly DisputeListItem[]>();
  readonly total = input.required<number>();
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly loading = input.required<boolean>();
  readonly error = input.required<'generic' | 'account' | ''>();
  readonly currentUrl = input.required<string>();
  readonly statusIconPaths = input.required<Readonly<Record<DisputeStatus, readonly string[]>>>();
  readonly presentation = input.required<DisputeListResultsPresentation>();
  readonly retry = output<void>();
  readonly viewAllAccounts = output<void>();
  readonly pageChange = output<number>();
}
