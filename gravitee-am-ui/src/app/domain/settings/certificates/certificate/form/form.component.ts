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
import { Component, OnInit, Input, EventEmitter, Output, OnChanges, SimpleChanges } from '@angular/core';
import { MaterialFileComponent } from "../../../../../components/json-schema-form/material-file.component";

@Component({
  selector: 'certificate-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss']
})
export class CertificateFormComponent implements OnInit, OnChanges {
  @Input('certificateConfiguration') configuration: any = {};
  @Input('certificateSchema') certificateSchema: any;
  @Output() configurationCompleted = new EventEmitter<any>();
  displayForm: boolean = false;
  data: any = {};
  customWidgets = {
    file: MaterialFileComponent
  };

  constructor() { }

  ngOnInit() {
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.certificateSchema) {
      let _certificateSchema = changes.certificateSchema.currentValue;
      if (_certificateSchema && _certificateSchema.id) {
        this.displayForm = true;
      }
    }

    if (changes.configuration) {
      let _certificateConfiguration = changes.configuration.currentValue;
      if (_certificateConfiguration) {
        this.data = _certificateConfiguration;
      }
    }
  }

  onChanges(certificateConfiguration) {
    this.configuration = certificateConfiguration;
  }

  isValid(isValid: boolean) {
    let configurationWrapper = { 'isValid' : isValid, 'configuration': this.configuration};
    this.configurationCompleted.emit(configurationWrapper);
  }
}
