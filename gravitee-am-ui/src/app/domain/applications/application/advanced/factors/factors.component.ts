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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {ApplicationService} from '../../../../../services/application.service';
import {SnackbarService} from '../../../../../services/snackbar.service';
import {FactorService} from '../../../../../services/factor.service';
import {AuthService} from '../../../../../services/auth.service';

@Component({
  selector: 'app-application-factors',
  templateUrl: './factors.component.html',
  styleUrls: ['./factors.component.scss']
})
export class ApplicationFactorsComponent implements OnInit {
  private domainId: string;

  application: any;
  formChanged = false;
  factors: any[];
  editMode: boolean;

  deviceIdentifiers: any[];

  mfa: any;

  mfaStepUpRule: string = "";
  rememberDevice: any = {};

  adaptiveMfaRule: string = "";
  enrollment: any = {};
  private riskAssessment: any = {};

  constructor(private route: ActivatedRoute,
              private router: Router,
              private applicationService: ApplicationService,
              private factorService: FactorService,
              private authService: AuthService,
              private snackbarService: SnackbarService) {
  }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.deviceIdentifiers = this.route.snapshot.data['deviceIdentifiers'] || [];
    this.mfa = this.application.settings == null ? {} : (this.application.settings.mfa || {});
    this.mfaStepUpRule = this.mfa.stepUpAuthenticationRule ? this.mfa.stepUpAuthenticationRule.slice() : "";
    this.adaptiveMfaRule = this.mfa.adaptiveAuthenticationRule ? this.mfa.adaptiveAuthenticationRule.slice() : "";
    this.rememberDevice = {...this.mfa.rememberDevice};
    this.enrollment = {...this.mfa.enrollment};
    this.application.settings.riskAssessment = this.application.settings.riskAssessment || ApplicationFactorsComponent.getDefaultRiskAssessment();
    this.riskAssessment = {...this.application.settings.riskAssessment};

    this.editMode = this.authService.hasPermissions(['application_settings_update']);
    this.factorService.findByDomain(this.domainId).subscribe(response => this.factors = [...response]);
  }

  private static getDefaultRiskAssessment() {
    return {
      "enabled": false,
      "deviceAssessment": {"enabled": false},
      "ipReputationAssessment": {"enabled": false},
      "geoVelocityAssessment": {"enabled": false}
    };
  }

  patch(): void {
    const data = {
      'factors': this.application.factors,
      'settings': {
        'riskAssessment': this.riskAssessment,
        'mfa': {
          'stepUpAuthenticationRule': this.mfaStepUpRule,
          'adaptiveAuthenticationRule': this.adaptiveMfaRule,
          'rememberDevice': this.rememberDevice,
          'enrollment': this.enrollment
        }
      }
    };
    this.applicationService.patch(this.domainId, this.application.id, data).subscribe(data => {
      this.application = data;
      this.formChanged = false;
      this.mfa = this.application.settings == null ? {} : this.application.settings.mfa || {};
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], {relativeTo: this.route, queryParams: {'reload': true}});
    });
  }

  selectFactor(selectFactorEvent) {
    if (selectFactorEvent.checked) {
      this.application.factors = this.application.factors || [];
      this.application.factors.push(selectFactorEvent.factorId);
    } else {
      this.application.factors.splice(this.application.factors.indexOf(selectFactorEvent.factorId), 1);
    }
    this.formChanged = true;
  }

  updateRememberDevice(rememberDevice) {
    this.rememberDevice = {...rememberDevice};
    if (!this.rememberDevice.deviceIdentifierId) {
      this.rememberDevice.deviceIdentifierId = this.deviceIdentifiers[0].id;
    }
    this.formChanged = true;
  }

  updateActivateMfa(options) {
    this.enrollment = {...options.enrollment};
    this.adaptiveMfaRule = (options.adaptiveMfaRule || "").slice();
    this.riskAssessment = {...options.riskAssessment};
    this.formChanged = true;
  }

  updateStepUpRule(stepUpRule: string) {
    this.mfaStepUpRule = stepUpRule.slice();
    this.formChanged = true;
  }

  hasFactors() {
    return this.factors && this.factors.length > 0;
  }

  hasSelectedFactors() {
    return this.application.factors && this.application.factors.length > 0;
  }
}
