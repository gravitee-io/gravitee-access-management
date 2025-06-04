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
import { Component, Inject, OnInit } from '@angular/core';
import { FlexModule } from '@angular/flex-layout';
import { MatButton } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { GioSafePipeModule } from '@gravitee/ui-particles-angular';
import { NgIf } from '@angular/common';

export interface IdpDataModel {
  id: string;
  name: string;
  selected: boolean;
  type: {
    icon: string;
    name: string;
  };
}

@Component({
  selector: 'app-password-policies-idp-select-dialog',
  imports: [
    FlexModule,
    MatButton,
    NgxDatatableModule,
    ReactiveFormsModule,
    MatTabsModule,
    MatIconModule,
    GioSafePipeModule,
    NgIf,
    FormsModule,
  ],
  templateUrl: './password-policies-idp-select-dialog.component.html',
  styleUrl: './password-policies-idp-select-dialog.component.scss',
})
export class PasswordPoliciesIdpSelectDialogComponent implements OnInit {
  result: Map<string, boolean> = new Map<string, boolean>();
  selectionModel: Map<string, boolean>;
  initialSelected: Map<string, boolean>;

  constructor(
    private dialogRef: MatDialogRef<PasswordPoliciesIdpSelectDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public rows: IdpDataModel[],
  ) {}

  ngOnInit(): void {
    const selection: [string, boolean][] = this.rows.map((idp: IdpDataModel): [string, boolean] => [idp.id, idp.selected]);
    this.selectionModel = new Map<string, boolean>(selection);
    this.initialSelected = new Map<string, boolean>(selection);
  }

  closeDialog(): void {
    this.dialogRef.close();
  }

  confirmSelection(): void {
    this.dialogRef.close(this.result);
  }

  select(rowId: string): void {
    const value: boolean = this.selectionModel.get(rowId);
    this.selectionModel.set(rowId, !value);
    this.result.set(rowId, !value);
  }

  disabledAdd(): boolean {
    if (this.selectionModel.size !== this.initialSelected.size) {
      return true;
    }
    for (const [key, value] of this.selectionModel) {
      if (!this.initialSelected.has(key)) {
        return true;
      }
      if (this.initialSelected.get(key) !== value) {
        return false;
      }
    }
    return true;
  }
}
