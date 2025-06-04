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

import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AbstractControl, FormControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

export interface AccountTokenRevokationDialogData {
  tokenId: string;
  name: string;
  createdAt: string;
}

export interface AccountTokenRevokationDialogResult {
  tokenId: string;
}

function valueEquals(expectedValue: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const matches = control.value == expectedValue;
    return matches ? null : { valueEquals: { value: control.value } };
  };
}

@Component({
  selector: 'account-token-revokation-dialog',
  templateUrl: './account-token-revokation-dialog.component.html',
  styleUrls: ['./account-token-revokation-dialog.component.scss'],
  standalone: false,
})
export class AccountTokenRevokationDialogComponent {
  tokenControl: FormControl<string>;

  constructor(
    public dialogRef: MatDialogRef<AccountTokenRevokationDialogData, AccountTokenRevokationDialogResult>,
    @Inject(MAT_DIALOG_DATA) public data: AccountTokenRevokationDialogData,
  ) {
    this.tokenControl = new FormControl<string>('', [Validators.required, valueEquals(data.name)]);
  }

  onSubmit(): void {
    this.dialogRef.close({ tokenId: this.data.tokenId });
  }
}
