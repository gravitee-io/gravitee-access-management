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
  selector: 'bot-detection-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss']
})
export class BotDetectionFormComponent implements OnInit, OnChanges {
  @Input('botDetectionConfiguration') configuration: any = {};
  @Input('botDetectionSchema') botDetectionSchema: any;
  @Output() configurationCompleted = new EventEmitter<any>();
  displayForm = false;
  data: any = {};

  constructor() { }

  ngOnInit() {
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.botDetectionSchema) {
      const _botDetectionSchema = changes.botDetectionSchema.currentValue;
      if (_botDetectionSchema && _botDetectionSchema.id) {
        this.displayForm = true;
      }
    }

    if (changes.configuration) {
      const _botDetectionConfiguration = changes.configuration.currentValue;
      if (_botDetectionConfiguration) {
        this.data = _botDetectionConfiguration;
      }
    }
  }

  onChanges(botDetectionConfiguration) {
    this.configuration = botDetectionConfiguration;
  }

  isValid(isValid: boolean) {
    const configurationWrapper = { 'isValid' : isValid, 'configuration': this.configuration};
    this.configurationCompleted.emit(configurationWrapper);
  }
}
