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
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';

import { MfaFactor } from '../../model';
import { getDisplayFactorType, getFactorTypeIcon } from '../mfa-select-icon';

export interface DialogData {
  factors: MfaFactor[];
  mfsSettingsLink: string;
}

export interface DialogResult {
  changed: boolean;
  factors: MfaFactor[];
  firstSelection: boolean;
}

@Component({
  selector: 'app-factors-select-dialog',
  templateUrl: './factors-select-dialog.component.html',
  styleUrls: ['./factors-select-dialog.component.scss'],
  standalone: false,
})
export class FactorsSelectDialogComponent implements OnInit {
  factors: MfaFactor[];
  model: Map<string, boolean>;
  mfaSettingsLink: string;

  constructor(
    public dialogRef: MatDialogRef<FactorsSelectDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData,
    private router: Router,
  ) {}

  ngOnInit() {
    this.mfaSettingsLink = this.data.mfsSettingsLink;
    this.factors = this.data.factors;
    const selectedFactors = this.data.factors.map((factor): [string, boolean] => [factor.id, factor.selected]);
    this.model = new Map<string, boolean>(selectedFactors);
  }

  confirmSelection(): void {
    this.factors.forEach((factor) => (factor.selected = this.model.get(factor.id)));
    const result = {
      factors: [...this.factors],
      changed: true,
      firstSelection: this.data.factors.length === 0 && this.factors.length > 0,
    } as DialogResult;
    this.dialogRef.close(result);
  }

  select(rowId: string): void {
    const value = this.model.get(rowId);
    this.model.set(rowId, !value);
  }

  anyFactorExists(): boolean {
    return this.factors?.length > 0;
  }

  noneFactorSelected(): boolean {
    return this.factors.map((factor) => this.model.get(factor.id)).filter((selected) => selected).length === 0;
  }

  closeDialog(): void {
    this.dialogRef.close();
  }

  gotoMfaSettings(): void {
    this.closeDialog();
    this.router.navigate([this.mfaSettingsLink]);
  }

  getFactorIconType(type: any): string {
    return getFactorTypeIcon(type);
  }

  getDisplayFactorType(type: any): string {
    return getDisplayFactorType(type);
  }
}
