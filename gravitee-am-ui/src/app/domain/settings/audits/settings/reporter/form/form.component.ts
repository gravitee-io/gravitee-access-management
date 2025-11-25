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
import { Component, Input, EventEmitter, Output, OnChanges, SimpleChanges } from '@angular/core';

import { MaterialMultiselectComponent } from '../../../../../../components/json-schema-form/material-multiselect.component';

@Component({
  selector: 'reporter-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss'],
  standalone: false,
})
export class ReporterFormComponent implements OnChanges {
  // eslint-disable-next-line @angular-eslint/no-input-rename
  @Input('reporterConfiguration') configuration: any = {};
  @Input() reporterSchema: any;
  @Output() configurationCompleted = new EventEmitter<any>();
  displayForm = false;
  data: any = {};
  customWidgets = {
    graviteeMultiselect: MaterialMultiselectComponent,
  };

  ngOnChanges(changes: SimpleChanges) {
    if (changes.reporterSchema) {
      const _reporterSchema = changes.reporterSchema.currentValue;
      if (_reporterSchema?.id) {
        this.displayForm = true;
      }
    }

    if (changes.configuration) {
      const _reporterConfiguration = changes.configuration.currentValue;
      if (_reporterConfiguration) {
        this.data = _reporterConfiguration;
      }
    }
  }

  onChanges(reporterConfiguration) {
    this.configuration = reporterConfiguration;
  }

  isValid(isValid: boolean) {
    const configurationWrapper = { isValid: isValid, configuration: this.configuration };
    this.configurationCompleted.emit(configurationWrapper);
  }
}
