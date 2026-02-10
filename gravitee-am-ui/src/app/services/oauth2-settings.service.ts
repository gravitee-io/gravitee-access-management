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

import { Injectable, InjectionToken } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';

import { ApplicationService } from './application.service';
import { ProtectedResourceService } from './protected-resource.service';

export interface OAuth2Settings {
  settings: any;
  resourceId: string;
  domainId: string;
  resource: any;
}

export const OAUTH2_SETTINGS_SERVICE = new InjectionToken<OAuth2SettingsService>('OAuth2SettingsService');

export type OAuth2Context = 'Application' | 'McpServer';

export interface OAuth2SettingsService {
  getPermission(): string;
  getContext(): OAuth2Context;
  getSettings(route: ActivatedRoute): OAuth2Settings;
  update(domainId: string, resourceId: string, resource: any, oauthSettings: any): Observable<any>;
}

@Injectable()
export class ApplicationOAuth2Service implements OAuth2SettingsService {
  constructor(private applicationService: ApplicationService) {}

  getPermission(): string {
    return 'application_openid_update';
  }

  getContext(): OAuth2Context {
    return 'Application';
  }

  getSettings(route: ActivatedRoute): OAuth2Settings {
    const domainId = route.snapshot.data['domain']?.id;
    const application = structuredClone(route.snapshot.data['application']);
    return {
      domainId,
      resourceId: application.id,
      resource: application,
      settings: application.settings?.oauth || {},
    };
  }

  update(domainId: string, resourceId: string, resource: any, oauthSettings: any): Observable<any> {
    return this.applicationService.patch(domainId, resourceId, { settings: { oauth: oauthSettings } });
  }
}

@Injectable()
export class McpServerOAuth2Service implements OAuth2SettingsService {
  constructor(private protectedResourceService: ProtectedResourceService) {}

  getPermission(): string {
    return 'protected_resource_oauth_update';
  }

  getContext(): OAuth2Context {
    return 'McpServer';
  }

  getSettings(route: ActivatedRoute): OAuth2Settings {
    const domainId = route.snapshot.data['domain']?.id;
    const resource = route.snapshot.data['mcpServer'];
    return {
      domainId,
      resourceId: resource.id,
      resource: resource,
      settings: resource.settings?.oauth || {},
    };
  }

  update(domainId: string, resourceId: string, resource: any, oauthSettings: any): Observable<any> {
    const updatePayload = {
      name: resource.name,
      resourceIdentifiers: resource.resourceIdentifiers,
      description: resource.description,
      features: resource.features,
      settings: {
        ...resource.settings,
        oauth: oauthSettings,
      },
    };
    return this.protectedResourceService.update(domainId, resourceId, updatePayload);
  }
}
