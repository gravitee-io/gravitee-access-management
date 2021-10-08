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
  selector: 'device-identifier-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss']
})
export class DeviceIdentifierFormComponent implements OnInit, OnChanges {
  @Input('deviceIdentifierConfiguration') configuration: any = {};
  @Input('deviceIdentifierSchema') deviceIdentifierSchema: any;
  @Output() configurationCompleted = new EventEmitter<any>();
  displayForm = false;
  data: any = {};

  constructor() { }

  ngOnInit() {
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.deviceIdentifierSchema) {
      const _schema = changes.deviceIdentifierSchema.currentValue;
      const length = _schema && _schema.properties ? Object.keys(_schema.properties).length : 0;
      if (_schema && _schema.id && length > 0) {
        this.displayForm = true;
      }
    }

    if (changes.configuration) {
      const _configuration = changes.configuration.currentValue;
      if (_configuration) {
        this.data = _configuration;
      }
    }
  }

  onChanges(configuration) {
    this.configuration = configuration;
  }

  isValid(isValid: boolean) {
    const configurationWrapper = { 'isValid' : isValid, 'configuration': this.configuration};
    this.configurationCompleted.emit(configurationWrapper);
  }
}
