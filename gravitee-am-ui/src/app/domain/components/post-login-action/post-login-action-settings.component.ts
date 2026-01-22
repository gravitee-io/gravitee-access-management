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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';

@Component({
  selector: 'app-post-login-action-settings',
  templateUrl: './post-login-action-settings.component.html',
  styleUrls: ['./post-login-action-settings.component.scss'],
  standalone: false,
})
export class PostLoginActionSettingsComponent implements OnChanges {
  // eslint-disable-next-line @angular-eslint/no-output-on-prefix
  @Output() onSavedPostLoginAction = new EventEmitter<any>();
  @Input() postLoginAction: any;
  @Input() inheritMode = false;
  @Input() readonly = false;
  @Input() certificates: any[] = [];
  @ViewChild('postLoginActionForm', { static: true }) form: any;
  formChanged = false;
  httpsUrlPattern = '^https?://.+$';

  private defaultSettings = {
    enabled: false,
    inherited: true,
    timeout: 60000,
    responseTokenParam: 'token',
    successClaim: 'status',
    successValue: 'approved',
    errorClaim: 'error',
    dataClaim: 'data',
  };

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.postLoginAction) {
      this.postLoginAction = this.withDefaults(changes.postLoginAction.currentValue);
      if (!this.inheritMode) {
        this.postLoginAction.inherited = false;
      }
    }
  }

  save(): void {
    let postLoginAction = { ...this.postLoginAction };
    if (this.inheritMode && postLoginAction.inherited) {
      postLoginAction = { inherited: true };
    }
    this.onSavedPostLoginAction.emit(postLoginAction);
    this.formChanged = false;
  }

  enableInheritMode(event) {
    this.postLoginAction.inherited = event.checked;
    this.formChanged = true;
  }

  isInherited(): boolean {
    return this.postLoginAction?.inherited;
  }

  toggleEnabled(event) {
    this.postLoginAction.enabled = event.checked;
    if (event.checked) {
      this.postLoginAction = this.withDefaults(this.postLoginAction);
    }
    this.formChanged = true;
  }

  isEnabled(): boolean {
    return this.postLoginAction?.enabled;
  }

  setUrl(value: string) {
    this.postLoginAction.url = value;
    this.formChanged = true;
  }

  setCertificateId(value: string) {
    this.postLoginAction.certificateId = value;
    this.formChanged = true;
  }

  setTimeout(value: number) {
    this.postLoginAction.timeout = value;
    this.formChanged = true;
  }

  setResponsePublicKey(value: string) {
    this.postLoginAction.responsePublicKey = value;
    this.formChanged = true;
  }

  setResponseTokenParam(value: string) {
    this.postLoginAction.responseTokenParam = value;
    this.formChanged = true;
  }

  setSuccessClaim(value: string) {
    this.postLoginAction.successClaim = value;
    this.formChanged = true;
  }

  setSuccessValue(value: string) {
    this.postLoginAction.successValue = value;
    this.formChanged = true;
  }

  setErrorClaim(value: string) {
    this.postLoginAction.errorClaim = value;
    this.formChanged = true;
  }

  setDataClaim(value: string) {
    this.postLoginAction.dataClaim = value;
    this.formChanged = true;
  }

  isFormValid(): boolean {
    return this.form.disabled ? true : this.form.valid;
  }

  private withDefaults(settings: any): any {
    return { ...this.defaultSettings, ...(settings || {}) };
  }
}
