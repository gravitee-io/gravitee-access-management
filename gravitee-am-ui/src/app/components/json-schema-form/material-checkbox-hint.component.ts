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
import { Component, Input, OnInit } from '@angular/core';
import { JsonSchemaFormService } from '@ajsf/core';
import { MatCheckboxChange } from '@angular/material/checkbox';

@Component({
  selector: 'material-checkbox-hint-widget',
  template: `
    <mat-checkbox [checked]="controlValue === true" [disabled]="options?.readonly" (change)="updateValue($event)">
      {{ options?.title }}
    </mat-checkbox>
    <div *ngIf="options?.description" class="gv-form-hint">{{ options.description }}</div>
  `,
  standalone: false,
})
export class MaterialCheckboxHintComponent implements OnInit {
  @Input() layoutNode: any;
  @Input() layoutIndex: number[];
  @Input() dataIndex: number[];

  options: any;
  controlValue: any;

  constructor(private jsf: JsonSchemaFormService) {}

  ngOnInit() {
    this.options = this.layoutNode.options;
    this.jsf.initializeControl(this);
  }

  updateValue(event: MatCheckboxChange) {
    this.controlValue = event.checked;
    this.jsf.updateValue(this, event.checked);
  }
}
