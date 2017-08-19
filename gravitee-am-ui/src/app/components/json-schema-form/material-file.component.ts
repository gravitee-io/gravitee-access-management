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
import { AbstractControl } from '@angular/forms';
import { JsonSchemaFormService } from "angular2-json-schema-form";

@Component({
  selector: 'material-file-widget',
  template: `<div>
    <div class="mat-input-table">
      <div class="mat-input-infix">
        <input type="file" (change)="updateValue($event)" [required]="options?.required" [placeholder]="options?.title">
        <span class="mat-input-placeholder-wrapper">
          <label class="mat-input-placeholder ng-tns-c11-1 mat-float">{{options?.title}}
            <span *ngIf="options?.required" class="mat-placeholder-required ng-tns-c11-1">*</span> 
          </label>
        </span>
        <span *ngIf="editMode"><i>file currently saved</i> : {{filename}}</span>
      </div>
    </div>
    <div class="mat-input-underline" style="position: inherit;"><span class="mat-input-ripple"></span></div>
    <div class="mat-input-hint-wrapper" style="font-size: 75%;">
      <div class="mat-input-hint-spacer"></div>
      <md-hint align="end" ng-reflect-align="end" class="mat-hint mat-right">{{options?.description}}</md-hint>
    </div>
  </div>`
})
export class MaterialFileComponent implements OnInit {
  formControl: AbstractControl;
  private controlName: string;
  controlValue: any;
  private controlDisabled: boolean = false;
  private boundControl: boolean = false;
  options: any;
  editMode: boolean = false;
  filename: string;
  @Input() formID: number;
  @Input() layoutNode: any;
  @Input() layoutIndex: number[];
  @Input() dataIndex: number[];

  constructor(
    private jsf: JsonSchemaFormService
  ) { }

  ngOnInit() {
    this.options = this.layoutNode.options;
    this.jsf.initializeControl(this);
    if (this.controlValue) {
      this.editMode = true;
      this.filename = this.controlValue.split('/').pop();
    }
  }

  updateValue(event) {
    let fileList: FileList = event.target.files;
    if(fileList.length > 0) {
      let file = fileList[0];
      let reader = new FileReader();
      let self = this;
      reader.readAsDataURL(file);
      reader.onload = function () {
        let jsonFile = {};
        jsonFile["name"] = file.name;
        jsonFile["type"] = file.type;
        jsonFile["size"] = file.size;
        jsonFile["content"] = reader.result.split(",")[1];
        self.jsf.updateValue(self, JSON.stringify(jsonFile));
        self.editMode = false;
      };
      reader.onerror = function (error) {
        console.log('Error uploading file: ', error);
      };
    } else {
      this.jsf.updateValue(this, event.target.value);
    }
  }
}
