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
import { Component, Input, EventEmitter, Output, OnChanges, SimpleChanges, OnInit } from '@angular/core';

@Component({
  selector: 'extension-grant-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss'],
  standalone: false,
})
export class ExtensionGrantFormComponent implements OnChanges, OnInit {
  // eslint-disable-next-line @angular-eslint/no-input-rename
  @Input('extensionGrantConfiguration') configuration: any = {};
  @Input() extensionGrantSchema: any;
  @Output() configurationCompleted = new EventEmitter<any>();
  displayForm = false;
  data: any = {};

  ngOnInit(): void {
    this.data.publicKeyResolver ??= 'GIVEN_KEY';
  }
  ngOnChanges(changes: SimpleChanges) {
    if (changes.extensionGrantSchema) {
      const _extensionGrantSchema = changes.extensionGrantSchema.currentValue;
      if (_extensionGrantSchema?.id) {
        this.displayForm = true;
      }
    }

    if (changes.configuration) {
      const _extensionGrantConfiguration = changes.configuration.currentValue;
      if (_extensionGrantConfiguration) {
        this.data = _extensionGrantConfiguration;
      }
    }
  }

  onChanges(extensionGrantConfiguration) {
    this.configuration = extensionGrantConfiguration;
  }

  isValid(isValid: boolean) {
    const configurationWrapper = { isValid: isValid, configuration: this.configuration };
    this.configurationCompleted.emit(configurationWrapper);
  }
}
