import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Button } from '../../../shared/components/button/button';
import {
  AgentCaseQueuePresentation,
} from './agent-case-queue-presentation';
import {
  AgentDisputeListItem,
  AgentQueue,
  AgentSortField,
} from '../models/agent-dispute-models';

@Component({
  selector: 'app-case-queue-results',
  standalone: true,
  imports: [DatePipe, RouterLink, Button],
  templateUrl: './case-queue-results.html',
  styleUrl: './case-queue-results.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaseQueueResults {
  readonly items = input.required<readonly AgentDisputeListItem[]>();
  readonly queue = input.required<AgentQueue>();
  readonly identityId = input<string | undefined>();
  readonly detailQueryParams = input.required<Record<string, string>>();
  readonly presentation = input.required<AgentCaseQueuePresentation>();
  readonly page = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly total = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly sortBy = input.required<AgentSortField>();
  readonly sortDirection = input.required<'ASC' | 'DESC'>();
  readonly sortChange = output<AgentSortField>();
  readonly pageChange = output<number>();
}