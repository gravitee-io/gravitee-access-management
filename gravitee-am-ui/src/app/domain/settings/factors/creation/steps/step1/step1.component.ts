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
import { Component, OnInit, Input } from '@angular/core';
import { OrganizationService } from "../../../../../../services/organization.service";

@Component({
  selector: 'factor-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss']
})
export class FactorCreationStep1Component implements OnInit {
  private factorTypes: any = {
    'otp-am-factor' : 'Generic OTP Factor',
    'email-am-factor' : 'Email Factor',
    'sms-am-factor' : 'SMS Factor',
    'call-am-factor' : 'Call Factor',
    'http-am-factor': 'HTTP Factor'
  };
  @Input() factor: any;
  factors: any[];
  selectedFactorTypeId: string;

  constructor(private organizationService: OrganizationService) {
  }

  ngOnInit() {
    this.organizationService.factors().subscribe(data => {
      this.factors = data
    });
  }

  selectFactorType() {
    this.factor.type = this.selectedFactorTypeId;
  }

  displayName(factor) {
    if (this.factorTypes[factor.id]) {
      return this.factorTypes[factor.id];
    }
    return factor.name;
  }
}
