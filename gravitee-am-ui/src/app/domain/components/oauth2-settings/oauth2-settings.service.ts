import { Injectable, InjectionToken } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { ApplicationService } from '../../../services/application.service';
import { ProtectedResourceService } from '../../../services/protected-resource.service';

export interface OAuth2Settings {
    settings: any;
    resourceId: string;
    domainId: string;
    resource: any;
}

export const OAUTH2_SETTINGS_SERVICE = new InjectionToken<OAuth2SettingsService>('OAuth2SettingsService');

export interface OAuth2SettingsService {
    getPermission(): string;
    getContext(): string;
    getSettings(route: ActivatedRoute): OAuth2Settings;
    update(domainId: string, resourceId: string, resource: any, oauthSettings: any): Observable<any>;
}

@Injectable()
export class ApplicationOAuth2Service implements OAuth2SettingsService {
    constructor(private applicationService: ApplicationService) {}

    getPermission(): string {
        return 'application_openid_update';
    }

    getContext(): string {
        return 'Application';
    }

    getSettings(route: ActivatedRoute): OAuth2Settings {
        const domainId = route.snapshot.data['domain']?.id;
        const application = structuredClone(route.snapshot.data['application']);
        return {
            domainId,
            resourceId: application.id,
            resource: application,
            settings: application.settings?.oauth || {}
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
        return 'protected_resource_update';
    }

    getContext(): string {
        return 'McpServer';
    }

    getSettings(route: ActivatedRoute): OAuth2Settings {
        const domainId = route.snapshot.data['domain']?.id;
        const resource = route.snapshot.data['mcpServer'];
        return {
            domainId,
            resourceId: resource.id,
            resource: resource,
            settings: resource.settings?.oauth || {}
        };
    }

    update(domainId: string, resourceId: string, resource: any, oauthSettings: any): Observable<any> {
        const updatePayload = {
            name: resource.name,
            resourceIdentifiers: resource.resourceIdentifiers,
            description: resource.description,
            features: resource.features,
            settings: {
                oauth: oauthSettings,
            },
        };
        return this.protectedResourceService.update(domainId, resourceId, updatePayload);
    }
}
