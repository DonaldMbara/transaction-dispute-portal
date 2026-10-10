import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-agent',
  standalone: true,
  templateUrl: './agent.html',
  styleUrl: './agent.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class Agent {}
