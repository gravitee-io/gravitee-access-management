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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';

import { DialogResult, FactorsSelectDialogComponent } from './factors-select-dialog/factors-select-dialog.component';
import { MfaIconsResolver } from './mfa-icons-resolver';

import { MfaFactor } from '../model';

@Component({
  selector: 'mfa-select',
  templateUrl: './mfa-select.component.html',
  styleUrls: ['./mfa-select.component.scss'],
})
export class MfaSelectComponent implements OnChanges {
  private iconResolver = new MfaIconsResolver();

  @Input() factors: MfaFactor[];
  @Input() editMode: boolean;
  @Output() settingsChange = new EventEmitter<MfaFactor[]>();

  expanded = false;

  selectedFactors: MfaFactor[];

  constructor(public dialog: MatDialog, private route: ActivatedRoute) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.factors) {
      this.selectedFactors = this.factors ? this.factors.filter((f) => f.selected) : [];
      if (this.selectedFactors.length > 0 && this.selectedFactors.filter((f) => f.isDefault).length == 0) {
        this.setFactorDefault(changes, this.selectedFactors[0].id);
      }
    }
  }

  anyFactorSelected(): boolean {
    return this.selectedFactorsCount() > 0;
  }

  selectedFactorsCount(): number {
    return this.selectedFactors ? this.selectedFactors.length : 0;
  }

  setFactorDefault($event: any, factorId: string): void {
    if (this.editMode) {
      let factors = [...this.factors];
      factors.forEach((factor) => (factor.isDefault = factor.id === factorId));
      this.settingsChange.emit(this.factors);
    }
  }

  removeSelectedFactor($event: any, factorId: string): void {
    let factors = [...this.factors];
    factors.filter((mfa) => mfa.id === factorId).forEach((factor) => (factor.selected = false));
    this.settingsChange.next(factors);
  }

  openFactorSelectionDialog(event: Event): void {
    event.preventDefault();
    const domainName = this.route.snapshot.data['domain']?.hrid;
    const environment = this.route.snapshot.data['domain']?.referenceId;
    const dialogRef = this.dialog.open(FactorsSelectDialogComponent, {
      data: {
        factors: [...this.factors],
        domainName: domainName,
        environment: environment,
      },
      width: '35%',
    });
    dialogRef.afterClosed().subscribe((result: DialogResult) => {
      if (result && result.changed) {
        this.settingsChange.next(result.factors);
      }
    });
  }

  getFactorIconType(type: any): string {
    return this.iconResolver.getFactorTypeIcon(type);
  }

  getDisplayFactorType(type: any): string {
    return this.iconResolver.getDisplayFactorType(type);
  }

  expand(expanded: boolean): void {
    this.expanded = expanded;
  }
}
