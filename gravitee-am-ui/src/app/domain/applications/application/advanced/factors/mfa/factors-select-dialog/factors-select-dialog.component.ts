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
import { AfterViewChecked, Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { MfaFactor } from '../../model';
import { getDisplayFactorType, getFactorTypeIcon } from '../mfa-select-icon';

export interface DialogData {
  factors: MfaFactor[];
  domainName: string;
  environment: string;
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
})
export class FactorsSelectDialogComponent implements OnInit, AfterViewChecked {
  factors: MfaFactor[];
  model: any;

  constructor(public dialogRef: MatDialogRef<FactorsSelectDialogComponent>, @Inject(MAT_DIALOG_DATA) public data: DialogData) {}

  ngOnInit() {
    this.factors = this.data.factors;
    this.model = this.data.factors.reduce((model, factor) => {
      model[factor.id] = factor.selected;
      return model;
    }, {});
  }

  ngAfterViewChecked(): void {
    // https://github.com/swimlane/ngx-datatable/issues/1266
    // ngx-datatable within matDialog doesn't resize to fit the full width
    window.dispatchEvent(new Event('resize'));
  }

  confirmSelection(): void {
    this.factors.forEach((factor) => (factor.selected = this.model[factor.id]));
    const result = {
      factors: [...this.factors],
      changed: true,
      firstSelection: this.data.factors.length === 0 && this.factors.length > 0,
    } as DialogResult;
    this.dialogRef.close(result);
  }

  anyFactorExists(): boolean {
    return this.factors?.length > 0;
  }

  cancel(): void {
    this.dialogRef.close();
  }

  goToMfaFactorSettingsPage(event: any) {
    event.preventDefault();
    const url = `/environments/${this.data.environment}/domains/${this.data.domainName}/settings/factors`;
    window.open(url, '_blank');
  }

  getFactorIconType(type: any): string {
    return getFactorTypeIcon(type);
  }

  getDisplayFactorType(type: any): string {
    return getDisplayFactorType(type);
  }
}
