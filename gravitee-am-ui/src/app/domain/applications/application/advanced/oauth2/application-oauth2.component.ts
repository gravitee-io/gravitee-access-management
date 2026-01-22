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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { ApplicationService } from '../../../../../services/application.service';
import { ScopeService } from '../../../../../services/scope.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { AuthService } from '../../../../../services/auth.service';

@Component({
  selector: 'application-oauth2',
  templateUrl: './application-oauth2.component.html',
  styleUrls: ['./application-oauth2.component.scss'],
  standalone: false,
})
export class ApplicationOAuth2Component implements OnInit {
  protected domainId: string;
  private applicationId: string;
  application: any;
  oauthSettings: any = {};
  readonly = false;
  formChanged = false;

  // Data for shared components
  customGrantTypes: any[] = [];
  scopes: any[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private applicationService: ApplicationService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private scopeService: ScopeService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = structuredClone(this.route.snapshot.data['application']);
    this.applicationId = this.application.id;

    // Extract oauth settings
    this.oauthSettings = this.application.settings?.oauth || {};

    // Load resolvers data
    this.route.data.subscribe(data => {
      this.customGrantTypes = data['domainGrantTypes'] || [];
      this.scopes = data['scopes'] || [];
      if (this.scopes.length === 0 && this.domainId) {
        // Fallback: manually fetch scopes if resolver failed
        this.scopeService.findAllByDomain(this.domainId).subscribe(scopes => {
          this.scopes = scopes || [];
        });
      }
    });

    this.readonly = !this.authService.hasPermissions(['application_openid_update']);
  }

  updateSettings(newSettings: any) {    
    // Grant flows
    this.oauthSettings.grantTypes = newSettings.grantTypes;
    this.oauthSettings.responseTypes = newSettings.responseTypes;
    this.oauthSettings.redirectUris = newSettings.redirectUris;
    this.oauthSettings.forcePKCE = newSettings.forcePKCE;
    this.oauthSettings.forceS256CodeChallengeMethod = newSettings.forceS256CodeChallengeMethod;
    this.oauthSettings.tokenEndpointAuthMethod = newSettings.tokenEndpointAuthMethod;
    this.oauthSettings.tlsClientAuthSubjectDn = newSettings.tlsClientAuthSubjectDn;
    this.oauthSettings.tlsClientAuthSanDns = newSettings.tlsClientAuthSanDns;
    this.oauthSettings.tlsClientAuthSanUri = newSettings.tlsClientAuthSanUri;
    this.oauthSettings.tlsClientAuthSanIp = newSettings.tlsClientAuthSanIp;
    this.oauthSettings.tlsClientAuthSanEmail = newSettings.tlsClientAuthSanEmail;
    this.oauthSettings.jwksUri = newSettings.jwksUri;
    this.oauthSettings.jwks = newSettings.jwks;
    this.oauthSettings.disableRefreshTokenRotation = newSettings.disableRefreshTokenRotation;
    
    // Scopes
    this.oauthSettings.scopeSettings = newSettings.scopeSettings;
    this.oauthSettings.enhanceScopesWithUserPermissions = newSettings.enhanceScopesWithUserPermissions;
    
    // Tokens
    this.oauthSettings.accessTokenValiditySeconds = newSettings.accessTokenValiditySeconds;
    this.oauthSettings.refreshTokenValiditySeconds = newSettings.refreshTokenValiditySeconds;
    this.oauthSettings.idTokenValiditySeconds = newSettings.idTokenValiditySeconds;
    this.oauthSettings.tokenCustomClaims = newSettings.tokenCustomClaims;
    
    this.formChanged = true;
  }

  onFormChanged(changed: boolean) {
    this.formChanged = changed;
  }

  save() {
    // Validation for private_key_jwt
    if (this.oauthSettings.tokenEndpointAuthMethod === 'private_key_jwt') {
      if (!this.oauthSettings.jwksUri && !this.oauthSettings.jwks) {
        this.snackbarService.open("The jwks_uri or jwks are required when using 'private_key_jwt' client authentication method");
        return;
      }
      if (this.oauthSettings.jwksUri && this.oauthSettings.jwks) {
        this.snackbarService.open('The jwks_uri and jwks parameters MUST NOT be used together.');
        return;
      }
      if (this.oauthSettings.jwks) {
        try {
          if (typeof this.oauthSettings.jwks === 'string') {
            JSON.parse(this.oauthSettings.jwks);
          }
        } catch {
          this.snackbarService.open('The jwks parameter is malformed.');
          return;
        }
      }
    }

    // Manually assign only valid properties to avoid sending read-only fields
    const oauthSettings: any = {};
    
    // Grant flows settings
    oauthSettings.grantTypes = this.oauthSettings.grantTypes;
    oauthSettings.responseTypes = this.oauthSettings.responseTypes;
    oauthSettings.redirectUris = this.oauthSettings.redirectUris;
    oauthSettings.forcePKCE = this.oauthSettings.forcePKCE;
    oauthSettings.forceS256CodeChallengeMethod = this.oauthSettings.forceS256CodeChallengeMethod;
    oauthSettings.tokenEndpointAuthMethod = this.oauthSettings.tokenEndpointAuthMethod;
    oauthSettings.tlsClientAuthSubjectDn = this.oauthSettings.tlsClientAuthSubjectDn;
    oauthSettings.tlsClientAuthSanDns = this.oauthSettings.tlsClientAuthSanDns;
    oauthSettings.tlsClientAuthSanUri = this.oauthSettings.tlsClientAuthSanUri;
    oauthSettings.tlsClientAuthSanIp = this.oauthSettings.tlsClientAuthSanIp;
    oauthSettings.tlsClientAuthSanEmail = this.oauthSettings.tlsClientAuthSanEmail;
    oauthSettings.jwksUri = this.oauthSettings.jwksUri;
    oauthSettings.disableRefreshTokenRotation = this.oauthSettings.disableRefreshTokenRotation;
    
    // Parse jwks if it's a string
    if (this.oauthSettings.jwks !== undefined) {
      oauthSettings.jwks = typeof this.oauthSettings.jwks === 'string' ? JSON.parse(this.oauthSettings.jwks) : this.oauthSettings.jwks;
    }
    
    // Scopes
    oauthSettings.scopeSettings = this.oauthSettings.scopeSettings;
    oauthSettings.enhanceScopesWithUserPermissions = this.oauthSettings.enhanceScopesWithUserPermissions;

    // Token settings
    oauthSettings.accessTokenValiditySeconds = this.oauthSettings.accessTokenValiditySeconds;
    oauthSettings.refreshTokenValiditySeconds = this.oauthSettings.refreshTokenValiditySeconds;
    oauthSettings.idTokenValiditySeconds = this.oauthSettings.idTokenValiditySeconds;
    
    // Filter out 'id' property from tokenCustomClaims (used only for UI tracking)
    if (this.oauthSettings.tokenCustomClaims !== undefined) {
      oauthSettings.tokenCustomClaims = this.oauthSettings.tokenCustomClaims.map((claim: any) => {
        const { id, ...rest } = claim;
        return rest;
      });
    }

    this.applicationService.patch(this.domainId, this.applicationId, { settings: { oauth: oauthSettings } }).subscribe(() => {
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
      this.formChanged = false;
    });
  }
}
