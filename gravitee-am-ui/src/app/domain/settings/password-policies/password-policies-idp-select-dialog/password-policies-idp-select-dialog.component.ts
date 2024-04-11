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
import { FlexModule } from '@angular/flex-layout';
import { MatButton } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { MatBadge } from '@angular/material/badge';

import {
  IdpDataModel,
  PasswordPoliciesIdpSelectTableComponent,
} from './password-policies-idp-select-table/password-policies-idp-select-table.component';

export interface DialogData {
  linkedIdps: IdpDataModel[];
  unlinkedIdps: IdpDataModel[];
}

@Component({
  selector: 'app-password-policies-idp-select-dialog',
  standalone: true,
  imports: [
    FlexModule,
    MatButton,
    NgxDatatableModule,
    ReactiveFormsModule,
    PasswordPoliciesIdpSelectTableComponent,
    MatTabsModule,
    MatIconModule,
    MatBadge,
  ],
  templateUrl: './password-policies-idp-select-dialog.component.html',
  styleUrl: './password-policies-idp-select-dialog.component.scss',
})
export class PasswordPoliciesIdpSelectDialogComponent {
  unlinkedResult = new Map<string, boolean>();
  linkedResult = new Map<string, boolean>();

  constructor(
    private dialogRef: MatDialogRef<PasswordPoliciesIdpSelectDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData,
  ) {}

  closeDialog(): void {
    this.dialogRef.close();
  }

  confirmSelection(): void {
    this.dialogRef.close(this.unlinkedResult);
  }

  handleEvent(event: { id: string; selected: boolean }): void {
    this.unlinkedResult.set(event.id, event.selected);
  }
}
