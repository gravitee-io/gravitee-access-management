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

import { ExpressionInfoDialogComponent } from '../../expression-info-dialog/expression-info-dialog.component';
import { Enroll } from '../../model';

@Component({
  selector: 'mfa-conditional',
  templateUrl: './mfa-conditional.component.html',
  styleUrls: ['./mfa-conditional.component.scss'],
  standalone: false,
})
export class MfaConditionalComponent {
  @Input() enrollment: Enroll;
  @Output() settingsChange = new EventEmitter<Enroll>();

  constructor(private dialog: MatDialog) {}

  openInfoDialog($event: any): void {
    $event.preventDefault();
    this.dialog.open(ExpressionInfoDialogComponent, { width: '700px' });
  }

  updateRule($event: any): void {
    if ($event.target) {
      this.enrollment.enrollmentRule = $event.target.value;
      this.update();
    }
  }
  updateSkipRule($event: any): void {
    if ($event.target) {
      this.enrollment.enrollmentSkipRule = $event.target.value;
      this.update();
    }
  }
  switchSkipConditional(): void {
    this.enrollment.enrollmentSkipActive = !this.enrollment.enrollmentSkipActive;
  }
  onSettingChange(skipTimeSeconds: number): void {
    this.enrollment.skipTimeSeconds = skipTimeSeconds;
    this.update();
  }
  private update(): void {
    this.settingsChange.emit(this.enrollment);
  }
}
