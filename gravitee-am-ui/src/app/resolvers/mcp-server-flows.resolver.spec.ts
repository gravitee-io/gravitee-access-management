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
import { ActivatedRouteSnapshot } from '@angular/router';
import { of } from 'rxjs';

import { ProtectedResourceService } from '../services/protected-resource.service';

import { McpServerFlowsResolver } from './mcp-server-flows.resolver';

describe('McpServerFlowsResolver', () => {
  it('should resolve flows for the MCP server', () => {
    const flows = [{ type: 'token' }];
    const service = { flows: jest.fn().mockReturnValue(of(flows)) } as unknown as ProtectedResourceService;
    const resolver = new McpServerFlowsResolver(service);

    const route = {
      parent: { data: { domain: { id: 'domain-id' } } },
      paramMap: { get: (key: string) => (key === 'mcpServerId' ? 'mcp-1' : null) },
    } as unknown as ActivatedRouteSnapshot;

    resolver.resolve(route).subscribe((result) => {
      expect(result).toBe(flows);
    });
    expect(service.flows).toHaveBeenCalledWith('domain-id', 'mcp-1');
  });
});
