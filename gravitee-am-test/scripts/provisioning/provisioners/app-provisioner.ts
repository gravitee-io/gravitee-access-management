import { getApplicationApi } from '@management-commands/service/utils';
import { runWithConcurrency } from '../utils/concurrency';
import { section, info, success, bullet, ICON, ansi, startSpinner, updateSpinner, stopSpinner } from '../logger';

export type CreatedApp = { id: string; clientId: string; name: string };

export type GrantMode = 'random' | 'code-only' | 'all';

function pickGrantTypes(mode: GrantMode | undefined): string[] {
  const all = ['authorization_code', 'implicit', 'password', 'client_credentials', 'refresh_token'];
  if (mode === 'all') return all;
  if (mode === 'code-only') return ['authorization_code', 'refresh_token'];
  const choices = [...all];
  const count = Math.max(2, Math.floor(Math.random() * choices.length));
  const picked: string[] = [];
  while (picked.length < count && choices.length > 0) {
    const idx = Math.floor(Math.random() * choices.length);
    picked.push(choices.splice(idx, 1)[0]);
  }
  if (!picked.includes('authorization_code')) picked.push('authorization_code');
  if (!picked.includes('refresh_token')) picked.push('refresh_token');
  return Array.from(new Set(picked));
}

export async function createAppsForDomain(
  accessToken: string,
  orgId: string,
  envId: string,
  domainId: string,
  domainOrdinal: number,
  namePrefix: string,
  runTag: string,
  totalApps: number,
  grantMode: GrantMode | undefined,
  idpId?: string,
): Promise<CreatedApp[]> {
  if (totalApps <= 0) return [];
  const api = getApplicationApi(accessToken);
  const total = totalApps;
  let createdCount = 0;
  const spin = startSpinner(`Creating ${total} application(s): 0/${total}`);
  const idxs = Array.from({ length: total }, (_, i) => i + 1);
  const apps = await runWithConcurrency(
    idxs,
    Math.min(5, total),
    async (a) => {
      const appName = `${namePrefix}app${domainOrdinal}${a}`;
      const clientId = appName;
      const grantTypes = pickGrantTypes(grantMode || 'random');
      const newAppBody = {
        name: appName,
        type: 'web',
        clientId,
        clientSecret: 'test',
        redirectUris: [`https://example.com/callback/${clientId}`],
        settings: {
          oauth: {
            grantTypes,
            responseTypes: ['code'],
            applicationType: 'WEB',
            tokenEndpointAuthMethod: 'client_secret_basic',
          },
        },
      } as any;
      const created = await api.createApplication({ organizationId: orgId, environmentId: envId, domain: domainId, newApplication: newAppBody });
      if (idpId) {
        await api.patchApplication({
          organizationId: orgId,
          environmentId: envId,
          domain: domainId,
          application: (created as any).id,
          patchApplication: { identityProviders: [{ identity: idpId, selectionRule: '', priority: 0 }] } as any,
        });
      }
      createdCount++;
      updateSpinner(spin, `Creating ${total} application(s): ${createdCount}/${total}`);
      return { id: (created as any).id, clientId, name: appName };
    },
  );
  stopSpinner(spin, `${ansi.green}${ICON.ok} Applications created: ${total}/${total}${ansi.reset}`);
  for (const app of apps) {
    bullet(`App ${app.name} id=${app.id} clientId=${app.clientId}`);
  }
  return apps;
}


