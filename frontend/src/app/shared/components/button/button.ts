import { Directive, HostBinding, Input } from '@angular/core';
import { ButtonVariant } from './button.interface';

/** Shared presentation for native buttons; feature components retain their own behavior. */
@Directive({ selector: 'button[appButton], a[appButton]', standalone: true })
export class Button {
  @Input() appButton: ButtonVariant = 'primary';

  @HostBinding('class.app-button') readonly baseClass = true;
  @HostBinding('class.app-button--primary') get primary(): boolean {
    return this.appButton === 'primary';
  }
  @HostBinding('class.app-button--secondary') get secondary(): boolean {
    return this.appButton === 'secondary';
  }
  @HostBinding('class.app-button--tertiary') get tertiary(): boolean {
    return this.appButton === 'tertiary';
  }
}
