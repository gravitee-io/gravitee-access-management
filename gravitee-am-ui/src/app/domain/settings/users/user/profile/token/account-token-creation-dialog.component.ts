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

import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { FormControl, Validators } from '@angular/forms';

export interface AccountTokenCreationDialogResult {
  name: string;
}

@Component({
  selector: 'account-token-creation-dialog',
  templateUrl: './account-token-creation-dialog.component.html',
  styleUrls: ['./account-token-dialog.component.scss'],
})
export class AccountTokenCreationDialogComponent {
  tokenControl = new FormControl<string>('', [Validators.required, Validators.minLength(3)]);

  constructor(public dialogRef: MatDialogRef<void, AccountTokenCreationDialogResult>) {}

  onSubmit(): void {
    this.dialogRef.close({ name: this.tokenControl.value });
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
