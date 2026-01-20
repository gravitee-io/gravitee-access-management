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

import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { McpServersService } from '../domain/mcp-servers/mcp-servers.service';

import { ApplicationService } from './application.service';

export interface ClientSecret {
  id: string;
  name: string;
  value?: string;
  secret?: string;
  createdAt?: string;
  // Add other fields as necessary
}

export abstract class ClientSecretService {
  abstract list(domainId: string, parentId: string): Observable<ClientSecret[]>;
  abstract create(domainId: string, parentId: string, name: string): Observable<ClientSecret>;
  abstract renew(domainId: string, parentId: string, secretId: string): Observable<ClientSecret>;
  abstract delete(domainId: string, parentId: string, secretId: string): Observable<any>;
}

@Injectable({
  providedIn: 'root',
})
export class ApplicationClientSecretService extends ClientSecretService {
  constructor(private applicationService: ApplicationService) {
    super();
  }

  list(domainId: string, parentId: string): Observable<ClientSecret[]> {
    return this.applicationService.getClientSecrets(domainId, parentId);
  }

  create(domainId: string, parentId: string, name: string): Observable<ClientSecret> {
    return this.applicationService.createClientSecret(domainId, parentId, name);
  }

  renew(domainId: string, parentId: string, secretId: string): Observable<ClientSecret> {
    return this.applicationService.renewClientSecret(domainId, parentId, secretId);
  }

  delete(domainId: string, parentId: string, secretId: string): Observable<any> {
    return this.applicationService.deleteClientSecret(domainId, parentId, secretId);
  }
}

@Injectable({
  providedIn: 'root',
})
export class McpServerClientSecretService extends ClientSecretService {
  constructor(private mcpServersService: McpServersService) {
    super();
  }

  list(domainId: string, parentId: string): Observable<ClientSecret[]> {
    return this.mcpServersService.getSecrets(domainId, parentId);
  }

  create(domainId: string, parentId: string, name: string): Observable<ClientSecret> {
    return this.mcpServersService.createSecret(domainId, parentId, name);
  }

  renew(domainId: string, parentId: string, secretId: string): Observable<ClientSecret> {
    return this.mcpServersService.renewSecret(domainId, parentId, secretId);
  }

  delete(domainId: string, parentId: string, secretId: string): Observable<any> {
    return this.mcpServersService.deleteSecret(domainId, parentId, secretId);
  }
}
