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
import {Component, EventEmitter, Input, Output} from "@angular/core";

@Component({
  selector: 'mfa-select',
  templateUrl: './mfa-select.component.html',
  styleUrls: ['./mfa-select.component.scss']
})
export class MfaSelectComponent {

  @Input() factors: any[];
  @Input() applicationFactors: any[];
  @Input() editMode: boolean;
  @Output("select-factor") selectFactorEmitter = new EventEmitter<any>();

  private factorTypes: any = {
    'OTP': 'TOTP',
    'SMS': 'SMS',
    'EMAIL': 'EMAIL',
    'CALL': 'CALL',
    'HTTP': 'HTTP',
    'RECOVERY_CODE' : 'Recovery Code',
    'FIDO2' : 'FIDO2'
  };

  private factorIcons: any = {
    'OTP': 'mobile_friendly',
    'SMS': 'sms',
    'EMAIL': 'email',
    'CALL': 'call',
    'HTTP': 'http',
    'RECOVERY_CODE': 'autorenew',
    'FIDO2': 'fingerprint'
  };

  getFactorTypeIcon(type) {
    const factorType = type.toUpperCase();
    if (this.factorIcons[factorType]) {
      return this.factorIcons[factorType];
    }
    return 'donut_large';
  }

  displayFactorType(type) {
    const factorType = type.toUpperCase();
    if (this.factorTypes[factorType]) {
      return this.factorTypes[factorType];
    }
    return 'Custom';
  }

  hasFactors() {
    return this.factors && this.factors.length > 0;
  }

  isFactorSelected(factorId: string) {
    return this.applicationFactors !== undefined && this.applicationFactors.includes(factorId);
  }

  selectFactor($event, factorId: string) {
    if (this.editMode) {
      this.selectFactorEmitter.emit({ "checked" : $event.checked, "factorId": factorId });
    }
  }
}
