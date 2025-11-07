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
import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';

@Component({
  selector: 'app-mcp-server-settings',
  templateUrl: './mcp-server-settings.component.html',
  standalone: false,
})
export class McpServerSettingsComponent {
  @Input() domain: any;
  @Input() name: string;
  @Input() resourceIdentifier: string;
  @Input() description: string;
  @Input() readonly = false;
  // eslint-disable-next-line @angular-eslint/no-output-on-prefix
  @Output() onSettingsChange = new EventEmitter<{ name: string; resourceIdentifier: string; description: string }>();
  @ViewChild('settingsForm', { static: true }) form: any;

  // Validation constants - must match backend validation
  public readonly namePattern = String.raw`^[^\s].*`;
  public readonly nameMaxlength = 64;

  onNameChange(): void {
    this.emitChange();
  }

  onResourceIdentifierChange(): void {
    this.emitChange();
  }

  onDescriptionChange(): void {
    this.emitChange();
  }

  private emitChange(): void {
    this.onSettingsChange.emit({
      name: this.name || '',
      resourceIdentifier: this.resourceIdentifier || '',
      description: this.description || '',
    });
  }

  isFormValid(): boolean {
    return this.form?.valid;
  }
}
