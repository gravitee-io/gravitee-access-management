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

import { SnackbarService } from '../../../../../../services/snackbar.service';
import { AppConfig } from '../../../../../../../config/app.config';

export interface AccountTokenCopyDialogData {
  token: string;
  orgId: string;
}

@Component({
  selector: 'account-token-copy-dialog',
  templateUrl: './account-token-copy-dialog.component.html',
  styleUrls: ['./account-token-dialog.component.scss'],
  standalone: false,
})
export class AccountTokenCopyDialogComponent {
  notCopied = true;
  token: string;
  orgId: string;

  constructor(
    @Inject(MAT_DIALOG_DATA) dialogData: AccountTokenCopyDialogData,
    public dialogRef: MatDialogRef<AccountTokenCopyDialogData, void>,
    private snackbarService: SnackbarService,
  ) {
    this.token = dialogData.token;
    this.orgId = dialogData.orgId;
  }

  valueCopied(message: string) {
    this.notCopied = false;
    this.snackbarService.open(message);
  }

  curlCopied(message: string) {
    this.snackbarService.open(message);
  }

  curlCommand(copy: boolean = false): string {
    if (copy) {
      return `curl  ${AppConfig.settings.organizationBaseURL.replace(':organizationId', this.orgId)}/environments -H 'Authorization: Bearer ${this.token}' `;
    } else {
      return `curl  ${AppConfig.settings.organizationBaseURL.replace(':organizationId', this.orgId)}/environments \n -H'Authorization: Bearer ${this.token}' `;
    }
  }
}
