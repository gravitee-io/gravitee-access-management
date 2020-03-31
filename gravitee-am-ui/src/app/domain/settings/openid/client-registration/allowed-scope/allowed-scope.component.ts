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
import * as _ from 'lodash';
import {DomainService} from '../../../../../services/domain.service';
import {SnackbarService} from '../../../../../services/snackbar.service';
import {Scope} from '../../../../components/scope-selection/scope-selection.component';
import {AuthService} from '../../../../../services/auth.service';

@Component({
  selector: 'app-openid-client-registration-allowed-scope',
  templateUrl: './allowed-scope.component.html',
  styleUrls: ['./allowed-scope.component.scss']
})

export class ClientRegistrationAllowedScopeComponent implements OnInit {
  domain: any = {};
  formChanged: boolean;
  dcrIsEnabled: boolean;
  isAllowedScopesEnabled: boolean;
  initialSelectedScopes: string[];
  selectedScopes: Scope[];
  readonly: boolean;

  constructor(private domainService: DomainService,
              private route: ActivatedRoute,
              private snackbarService: SnackbarService,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.dcrIsEnabled = this.domain.oidc.clientRegistrationSettings.isDynamicClientRegistrationEnabled;
    this.isAllowedScopesEnabled = this.domain.oidc.clientRegistrationSettings.isAllowedScopesEnabled;
    this.initialSelectedScopes = this.domain.oidc.clientRegistrationSettings.allowedScopes;
    this.readonly = !this.authService.hasPermissions(['domain_openid_create', 'domain_openid_update']);
  }

  enableAllowedScopesFilter(event) {
    this.isAllowedScopesEnabled = event.checked;
    this.formChanged = true;
  }

  onChange(currentSelectedScopes) {
    this.selectedScopes = currentSelectedScopes;
    this.formChanged = true;
  }

  patch() {
    const domain = {
      oidc: {
        clientRegistrationSettings: {
          allowedScopes: _.map(this.selectedScopes, scope => scope.key),
          isAllowedScopesEnabled: this.isAllowedScopesEnabled
        }
      }
    };

    this.domainService.patchOpenidDCRSettings(this.domain.id, domain).subscribe(response => {
      this.domain = response;
      this.snackbarService.open('Domain ' + this.domain.name + ' updated');
      this.formChanged = false;
    });
  }
}
