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

import { SnackbarService } from '../../../../services/snackbar.service';
import { AuthService } from '../../../../services/auth.service';
import { ProtectedResourceService } from '../../../../services/protected-resource.service';

@Component({
  selector: 'domain-mcp-server-oauth2',
  templateUrl: './domain-mcp-server-oauth2.component.html',
  styleUrls: ['./domain-mcp-server-oauth2.component.scss'],
  standalone: false,
})
export class DomainMcpServerOAuth2Component implements OnInit {
  protected domainId: string;
  private mcpServerId: string;
  resource: any;
  oauthSettings: any = {};
  readonly = false;
  formChanged = false;

  // Data for shared components
  customGrantTypes: any[] = [];
  scopes: any[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private protectedResourceService: ProtectedResourceService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.resource = this.route.snapshot.data['mcpServer'];
    this.mcpServerId = this.resource.id;

    // Extract oauth settings
    this.oauthSettings = this.resource.settings?.oauth || {};

    // Load resolvers data
    this.customGrantTypes = this.route.snapshot.data['domainGrantTypes'] || [];
    this.scopes = this.route.snapshot.data['scopes'] || [];

    this.readonly = !this.authService.hasPermissions(['domain_protected_resource_update']);
  }

  updateSettings(newSettings: any) {
    this.oauthSettings = newSettings;
    this.formChanged = true;
  }

  onFormChanged(changed: boolean) {
    this.formChanged = changed;
  }

  save() {
    // Current backend logic maps 'settings' directly.
    const updatePayload = {
      name: this.resource.name,
      resourceIdentifiers: this.resource.resourceIdentifiers,
      description: this.resource.description,
      features: this.resource.features,
      settings: {
        oauth: this.oauthSettings,
      },
    };

    this.protectedResourceService.update(this.domainId, this.mcpServerId, updatePayload).subscribe((updatedResource) => {
      this.resource = updatedResource;
      this.formChanged = false;
      this.snackbarService.open('OAuth2 settings updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
    });
  }
}
