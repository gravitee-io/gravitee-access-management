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

@Component({
  selector: 'factor-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss'],
})
export class FactorFormComponent implements OnInit, OnChanges {
  @Input('factorConfiguration') configuration: any = {};
  @Input('factorSchema') factorSchema: any;
  @Output() configurationCompleted = new EventEmitter<any>();
  displayForm = false;
  data: any = {};

  constructor() {}

  ngOnInit() {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes.factorSchema) {
      const _factorSchema = changes.factorSchema.currentValue;
      if (_factorSchema && _factorSchema.id) {
        this.displayForm = true;
      }
    }

    if (changes.configuration) {
      const _factorConfiguration = changes.configuration.currentValue;
      if (_factorConfiguration) {
        this.data = _factorConfiguration;
      }
    }
  }

  onChanges(factorConfiguration) {
    this.configuration = factorConfiguration;
  }

  isValid(isValid: boolean) {
    const configurationWrapper = { isValid: isValid, configuration: this.configuration };
    this.configurationCompleted.emit(configurationWrapper);
  }
}
