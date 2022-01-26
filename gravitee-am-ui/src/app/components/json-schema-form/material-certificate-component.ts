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
import {Component, Input, OnInit} from '@angular/core';
import { JsonSchemaFormService } from '@ajsf/core';
import {CertificateService} from "../../services/certificate.service";
import {ActivatedRoute} from "@angular/router";

@Component({
  selector: 'select-certificate-widget',
  template: `
    <div class="mat-input-flex mat-form-field-flex mat-form-field">
        <mat-form-field style="margin-top: 10px" floatLabel="always">
          <mat-label>{{ options?.title }}</mat-label>
          <mat-select [(value)]="controlValue" (selectionChange)="updateValue($event)">
            <mat-option>None</mat-option>
            <mat-option [attr.selected]="certificate?.id === controlValue"
                        *ngFor="let certificate of this.certificates"
                        [value]="certificate.id">
              {{certificate.name}}
            </mat-option>
          </mat-select>
          <mat-hint class="mat-form-field-hint-end">{{options?.description}}</mat-hint>
        </mat-form-field>
    </div>
  `,
})
export class MaterialCertificateComponent implements OnInit {
  @Input() layoutNode: any;
  @Input() layoutIndex: number[];
  @Input() dataIndex: number[];
  options: any;
  certificates: any;
  controlValue: any;

  constructor(
    private jsf: JsonSchemaFormService,
    private route: ActivatedRoute
  ) { }

  ngOnInit() {
    this.certificates = this.route.snapshot.data['certificates'];
    this.options = this.layoutNode.options;
    this.jsf.initializeControl(this);
  }

  updateValue(e) {
    this.jsf.updateValue(this, e.value);
  }
}
