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

import { SnackbarService } from '../../../services/snackbar.service';

export interface ApplicationClientSecretCopyDialogData {
  secret: string;
  renew?: boolean;
}

export type ApplicationClientSecretCopyDialogResult = void;

@Component({
  selector: 'application-client-secret-copy-dialog',
  templateUrl: './application-client-secret-copy-dialog.component.html',
  styleUrls: ['./application-client-secret-dialog.component.scss'],
})
export class ApplicationClientSecretCopyDialogComponent {
  notCopied = true;
  clientSecret: string;
  renew: boolean;

  constructor(
    @Inject(MAT_DIALOG_DATA) dialogData: ApplicationClientSecretCopyDialogData,
    public dialogRef: MatDialogRef<ApplicationClientSecretCopyDialogData, ApplicationClientSecretCopyDialogResult>,
    private snackbarService: SnackbarService,
  ) {
    this.clientSecret = dialogData.secret;
    this.renew = dialogData.renew;
  }

  valueCopied(message: string) {
    this.notCopied = false;
    this.snackbarService.open(message);
  }

  onSubmit() {
    this.dialogRef.close();
  }
}
