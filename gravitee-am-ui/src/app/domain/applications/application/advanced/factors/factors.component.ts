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
import {ActivatedRoute} from '@angular/router';
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
  private factorTypes: any = {
    'otp-am-factor' : 'OTP'
  };
  private factorIcons: any = {
    'otp-am-factor' : 'mobile_friendly'
  };
  application: any;
  formChanged = false;
  factors: any[];
  editMode: boolean;

  constructor(private route: ActivatedRoute,
              private applicationService: ApplicationService,
              private factorService: FactorService,
              private authService: AuthService,
              private snackbarService: SnackbarService) { }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.params['domainId'];
    this.application = this.route.snapshot.data['application'];
    this.editMode = this.authService.hasPermissions(['application_settings_update']);
    this.factorService.findByDomain(this.domainId).subscribe(response => this.factors = [...response]);
  }

  patch(): void {
    this.applicationService.patch(this.domainId, this.application.id, { 'factors': this.application.factors }).subscribe(data => {
      this.application = data;
      this.route.snapshot.data['application'] = this.application;
      this.formChanged = false;
      this.snackbarService.open('Application updated');
    });
  }

  selectFactor(event, factorId) {
    if (event.checked) {
      this.application.factors.push(factorId);
    } else {
      this.application.factors.splice(this.application.identities.indexOf(factorId), 1);
    }
    this.formChanged = true;
  }

  isFactorSelected(factorId) {
    return this.application.factors !== undefined && this.application.factors.includes(factorId);
  }

  hasFactors() {
    return this.factors && this.factors.length > 0;
  }

  getFactorTypeIcon(type) {
    if (this.factorIcons[type]) {
      return this.factorIcons[type];
    }
    return 'donut_large';
  }

  displayFactorType(type) {
    if (this.factorTypes[type]) {
      return this.factorTypes[type];
    }
    return 'Custom';
  }
}
