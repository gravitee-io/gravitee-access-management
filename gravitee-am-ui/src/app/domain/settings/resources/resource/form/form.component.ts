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
  selector: 'resource-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss']
})
export class ResourceFormComponent implements OnInit, OnChanges {
  @Input('resourceConfiguration') configuration: any = {};
  @Input('resourceSchema') resourceSchema: any;
  @Output() configurationCompleted = new EventEmitter<any>();
  displayForm = false;
  data: any = {};

  constructor() { }

  ngOnInit() {
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.resourceSchema) {
      const _resourceSchema = changes.resourceSchema.currentValue;
      if (_resourceSchema && _resourceSchema.id) {
        this.displayForm = true;
      }
    }

    if (changes.configuration) {
      const _resourceConfiguration = changes.configuration.currentValue;
      if (_resourceConfiguration) {
        this.data = _resourceConfiguration;
      }
    }
  }

  onChanges(resourceConfiguration) {
    this.configuration = resourceConfiguration;
  }

  isValid(isValid: boolean) {
    const configurationWrapper = { 'isValid' : isValid, 'configuration': this.configuration};
    this.configurationCompleted.emit(configurationWrapper);
  }
}
