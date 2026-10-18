import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {DatePipe} from '@angular/common';
import {AgentCaseDetailPresentation, AgentCaseSummary} from '../utils/agent-case-detail-presentation';

@Component({
  selector: 'app-agent-case-summary',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './case-summary.html',
  styleUrl: './case-summary.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaseSummary {
  readonly case = input.required<AgentCaseSummary>();
  readonly presentation = input.required<AgentCaseDetailPresentation>();
}
