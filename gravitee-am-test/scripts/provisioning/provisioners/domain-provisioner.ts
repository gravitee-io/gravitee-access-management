import { getDomainApi } from '@management-commands/service/utils';
import { section, info, success, ICON, ansi } from '../logger';

export type CreatedDomain = { id: string; name: string; ordinal: number };

export async function createAndStartDomains(
  accessToken: string,
  orgId: string,
  envId: string,
  namePrefix: string,
  runTag: string,
  domainCount: number,
): Promise<CreatedDomain[]> {
  section('Create and start domains');
  const api = getDomainApi(accessToken);
  const created: CreatedDomain[] = [];
  for (let d = 1; d <= domainCount; d++) {
    const domainName = `${namePrefix}-domain-${runTag}-${d}`;
    info(`Creating domain: ${domainName}`);
    const domain = await api.createDomain({
      organizationId: orgId,
      environmentId: envId,
      newDomain: { name: domainName, description: 'Provisioned by provision.ts', dataPlaneId: 'default' },
    });
    const domainId = (domain as any).id;
    info(`Starting domain: ${domainName} (${domainId})`);
    await api.patchDomain({ organizationId: orgId, environmentId: envId, domain: domainId, patchDomain: { enabled: true } });
    const started = await api.findDomain({ organizationId: orgId, environmentId: envId, domain: domainId });
    if (!(started as any).enabled) {
      throw new Error(`Domain ${domainName} (${domainId}) failed to enable`);
    }
    success(`Domain ready: ${domainName} (${domainId})`);
    created.push({ id: domainId, name: domainName, ordinal: d });
  }
  return created;
}

export async function waitForDomainsReady(ms: number) {
  section('Readiness wait');
  const { startSpinner, stopSpinner } = await import('../logger');
  const spin = startSpinner(`${ICON.hourglass} Waiting ${Math.floor(ms / 1000)}s for domains to be ready...`);
  await new Promise((r) => setTimeout(r, ms));
  stopSpinner(spin, `${ansi.green}${ICON.ok} Domains are ready${ansi.reset}`);
  success('Wait complete');
}


