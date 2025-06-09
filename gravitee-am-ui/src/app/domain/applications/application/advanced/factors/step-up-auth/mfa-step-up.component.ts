/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { ExpressionInfoDialogComponent } from '../expression-info-dialog/expression-info-dialog.component';
import { StepUpAuth } from '../model';

@Component({
  selector: 'mfa-step-up',
  templateUrl: './mfa-step-up.component.html',
  styleUrls: ['./mfa-step-up.component.scss'],
  standalone: false,
})
export class MfaStepUpComponent {
  @Output() settingsChange: EventEmitter<StepUpAuth> = new EventEmitter<StepUpAuth>();
  @Input() stepUpAuth: StepUpAuth;

  constructor(private dialog: MatDialog) {}

  updateRule($event: any): void {
    if ($event.target) {
      this.update();
    }
  }

  openInfoDialog($event: any): void {
    $event.preventDefault();
    this.dialog.open(ExpressionInfoDialogComponent, {
      width: '700px',
      data: {
        info: `{#request.params['scope'][0] == 'write'}
{#request.params['audience'][0] == 'https://myapi.com'}`,
      },
    });
  }

  onToggle($event: any): void {
    this.stepUpAuth.active = $event.checked;
    this.update();
  }

  private update(): void {
    this.settingsChange.emit(this.stepUpAuth);
  }
}
