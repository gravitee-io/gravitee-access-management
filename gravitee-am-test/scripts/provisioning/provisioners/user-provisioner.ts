import faker from 'faker';
import { getUserApi } from '@management-commands/service/utils';
import { startSpinner, updateSpinner, stopSpinner, ansi, ICON } from '../logger';

export async function createUsersForDomain(
  accessToken: string,
  orgId: string,
  envId: string,
  domainId: string,
  domainOrdinal: number,
  namePrefix: string,
  runTag: string,
  usersPerDomain: number,
  idpId?: string,
): Promise<number> {
  if (usersPerDomain <= 0) return 0;
  const api = getUserApi(accessToken);
  const total = usersPerDomain;
  const batchSize = Math.min(200, total);
  const spin = startSpinner(`Creating ${total} user(s): 0/${total}`);

  function makeUser(u: number) {
    const username = `${namePrefix}-user-${runTag}-${domainOrdinal}-${u}`;
    const email = `${username}@example.test`;
    const password = 'SomeP@ssw0rd';
    return {
      username,
      email,
      firstName: faker.name.firstName(),
      lastName: faker.name.lastName(),
      password,
      preRegistration: false,
      registrationCompleted: true,
      additionalInformation: {},
      source: idpId,
    } as any;
  }

  const items = Array.from({ length: total }, (_, i) => makeUser(i + 1));
  let created = 0;
  for (let start = 0; start < total; start += batchSize) {
    const batch = items.slice(start, start + batchSize);
    updateSpinner(spin, `Creating ${total} user(s): ${Math.min(start + batch.length, total)}/${total}`);
    await api.bulkUserOperation({
      organizationId: orgId,
      environmentId: envId,
      domain: domainId,
      domainUserBulkRequest: { action: 'CREATE', items: batch, failOnErrors: 0 },
    });
    created += batch.length;
    updateSpinner(spin, `Creating ${total} user(s): ${created}/${total}`);
  }

  stopSpinner(spin, `${ansi.green}${ICON.ok} Users created: ${created}/${total}${ansi.reset}`);
  return created;
}


