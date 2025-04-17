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

import { MfaFactor } from '../model';
import { SelectionRuleDialogComponent, SelectionRuleDialogResult } from '../selection-rule-dialog/selection-rule-dialog.component';

import { DialogResult, FactorsSelectDialogComponent } from './factors-select-dialog/factors-select-dialog.component';
import { getDisplayFactorType, getFactorTypeIcon } from './mfa-select-icon';

@Component({
  selector: 'mfa-select',
  templateUrl: './mfa-select.component.html',
  styleUrls: ['./mfa-select.component.scss'],
})
export class MfaSelectComponent implements OnChanges {
  @Input() factors: MfaFactor[];
  @Input() editMode: boolean;
  @Output() settingsChange = new EventEmitter<MfaFactor[]>();

  expanded = true;

  selectedFactors: MfaFactor[];

  constructor(
    public dialog: MatDialog,
    private route: ActivatedRoute,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.factors) {
      this.selectedFactors = this.factors ? this.factors.filter((f) => f.selected) : [];
      if (this.selectedFactors.length > 0 && this.selectedFactors.filter((f) => f.isDefault).length === 0) {
        this.setFactorDefault(this.selectedFactors[0].id);
      }
    }
  }

  anyFactorSelected(): boolean {
    return this.selectedFactorsCount() > 0;
  }

  selectedFactorsCount(): number {
    return this.selectedFactors ? this.selectedFactors.length : 0;
  }

  setFactorDefault(factorId: string): void {
    if (this.editMode) {
      this.factors.forEach((factor) => (factor.isDefault = factor.id === factorId));
      this.update();
    }
  }

  removeSelectedFactor($event: any, factorId: string): void {
    this.factors.filter((mfa) => mfa.id === factorId).forEach((factor) => (factor.selected = false));
    this.update();
  }

  openFactorSelectionDialog(event: Event): void {
    event.preventDefault();

    const dialogRef = this.dialog.open(FactorsSelectDialogComponent, {
      data: {
        factors: [...this.factors],
        mfsSettingsLink: this.getDomainMfaSettingsLink(),
      },
      width: '540px',
    });
    dialogRef.afterClosed().subscribe((result: DialogResult) => {
      if (result?.changed) {
        this.factors = result.factors;
        if (result.firstSelection) {
          this.expanded = true;
        }
        this.update();
      }
    });
  }

  private getDomainMfaSettingsLink(): string {
    const domainId = this.route.snapshot.data['domain']?.id;
    const environment = this.route.snapshot.data['domain']?.referenceId;
    return `/environments/${environment}/domains/${domainId}/settings/factors`.toLowerCase();
  }

  getFactorIconType(type: any): string {
    return getFactorTypeIcon(type);
  }

  getDisplayFactorType(type: any): string {
    return getDisplayFactorType(type);
  }

  expand(expanded: boolean): void {
    this.expanded = expanded;
  }

  addSelectionRule(factor: MfaFactor): void {
    this.dialog
      .open(SelectionRuleDialogComponent, {
        data: {
          selectionRule: factor.selectionRule,
        },
      })
      .afterClosed()
      .subscribe((result: SelectionRuleDialogResult) => {
        if (result?.selectionRule != null) {
          this.factors.filter((f) => f.id === factor.id)[0].selectionRule = result.selectionRule;
          this.update();
        }
      });
  }

  private update() {
    this.settingsChange.next(this.factors);
  }
}
