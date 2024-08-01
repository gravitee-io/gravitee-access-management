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

import { MaterialCertificateComponent } from '../../../../../components/json-schema-form/material-certificate-component';

@Component({
  selector: 'provider-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss'],
})
export class ProviderFormComponent implements OnChanges {
  // eslint-disable-next-line @angular-eslint/no-input-rename
  @Input('providerConfiguration') configuration: any = {};
  @Input() providerSchema: any;
  @Output() configurationCompleted = new EventEmitter<any>();
  schemaWithLayout: any;
  displayForm = false;
  data: any = {};
  customWidgets = {
    graviteeCertificate: MaterialCertificateComponent,
  };

  ngOnChanges(changes: SimpleChanges) {
    if (changes.providerSchema) {
      const _providerSchema = changes.providerSchema.currentValue;
      if (_providerSchema && _providerSchema.id) {
        this.providerSchema = this.applyPasswordInputToSensitiveFields(structuredClone(_providerSchema));
        this.displayForm = true;
      }
    }

    if (changes.configuration) {
      const _providerConfiguration = changes.configuration.currentValue;
      if (_providerConfiguration) {
        this.data = _providerConfiguration;
      }
    }
  }

  applyPasswordInputToSensitiveFields(schema: any) {
    console.log('Setting to schema:', schema);
    if (typeof schema !== 'object') {
      return schema;
    }
    for (const key in schema) {
      this.applyPasswordInputToSensitiveFields(schema[key]);
      if (schema[key].sensitive) {
        schema[key].widget = 'password';
        // Object.assign(schema[key], { 'widget': { type: 'password' } });
      }
    }
    return schema;
  }

  onChanges(providerConfiguration) {
    this.configuration = providerConfiguration;
  }

  isValid(isValid: boolean) {
    const configurationWrapper = { isValid: isValid, configuration: this.configuration };
    this.configurationCompleted.emit(configurationWrapper);
  }
}
