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

export interface SelectionRuleDialogResult {
  selectionRule: string;
}
@Component({
  selector: 'factor-selection-rule-dialog',
  templateUrl: './selection-rule-dialog.component.html',
  standalone: false,
})
export class SelectionRuleDialogComponent implements OnInit {
  selectionRule: string;
  constructor(
    public dialogRef: MatDialogRef<SelectionRuleDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any,
  ) {}

  ngOnInit(): void {
    this.selectionRule = this.data.selectionRule;
  }
  save(): void {
    this.dialogRef.close({
      selectionRule: this.selectionRule,
    });
  }

  updateRule($event: any): void {
    if ($event.target) {
      this.selectionRule = $event.target.value;
    }
  }
}
