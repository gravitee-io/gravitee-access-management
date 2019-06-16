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

@Component({
  selector: 'app-openid-client-registration-default-scope',
  templateUrl: './default-scope.component.html',
  styleUrls: ['./default-scope.component.scss']
})

export class ClientRegistrationDefaultScopeComponent implements OnInit {
  domain: any = {};
  formChanged: boolean;
  dcrIsEnabled: boolean;

  initialSelectedScopes: string[];
  selectedScopes: Scope[];

  constructor(private domainService: DomainService, private route: ActivatedRoute,
              private snackbarService: SnackbarService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.dcrIsEnabled = this.domain.oidc.clientRegistrationSettings.isDynamicClientRegistrationEnabled;
    this.initialSelectedScopes = this.domain.oidc.clientRegistrationSettings.defaultScopes;
  }

  onChange(currentSelectedScopes) {
    this.selectedScopes = currentSelectedScopes;
    this.formChanged = true;
  }

  patch() {
    const domain = {
      oidc: {
        clientRegistrationSettings: {
          defaultScopes: _.map(this.selectedScopes, scope => scope.key)
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
