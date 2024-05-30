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
import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { IdpDataModel, PasswordPoliciesIdpSelectDialogComponent } from './password-policies-idp-select-dialog.component';

export type DialogCallback = (data: Map<string, boolean>) => void;

@Injectable()
export class PasswordPoliciesIdpSelectDialogFactory {
  constructor(private dialog: MatDialog) {}

  public openDialog(data: IdpDataModel[], callback: DialogCallback): void {
    const cfg = {
      data: data,
      width: '740px',
    };
    const dialogRef = this.dialog.open(PasswordPoliciesIdpSelectDialogComponent, cfg);
    dialogRef.afterClosed().subscribe((result: Map<string, boolean>) => callback(result));
  }
}
