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
import '@gravitee/ui-components/wc/gv-schema-form';

@Component({
  selector: 'alert-notifier-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss'],
  standalone: false,
})
export class AlertNotifierFormComponent implements OnChanges {
  @Input() notifierConfiguration: any = {};
  @Input() notifierSchema: any;
  @Output() configurationCompleted = new EventEmitter<any>();
  displayForm = false;
  data: any = {};

  ngOnChanges(changes: SimpleChanges) {
    if (changes.notifierSchema) {
      const notifierSchema = changes.notifierSchema.currentValue;
      if (notifierSchema?.id) {
        this.displayForm = true;
      }
    }

    if (changes.notifierConfiguration) {
      const notifierConfiguration = changes.notifierConfiguration.currentValue;
      if (notifierConfiguration) {
        this.data = notifierConfiguration;
      }
    }
  }

  onChanges(notifierConfiguration) {
    this.notifierConfiguration = notifierConfiguration;
  }

  isValid(isValid: boolean) {
    const configurationWrapper = { isValid: isValid, configuration: this.notifierConfiguration };
    this.configurationCompleted.emit(configurationWrapper);
  }
}
