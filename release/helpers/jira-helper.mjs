/**
 * Get the Jira version associated to the given version name
 * @param versionName {string} The version name
 * @returns {Promise<{
 *   self: string,
 *   id: string,
 *   name: string,
 *   archived: boolean,
 *   released: boolean,
 *   releaseDate: string,
 *   userReleaseDate: string,
 *   projectId: number
 * }>}
 */
export function getJiraVersion(versionName) {
  const token = process.env.JIRA_TOKEN;

  return fetch(`https://gravitee.atlassian.net/rest/api/3/project/AM/versions`, {
    method: 'GET',
    headers: {
      Authorization: `Basic ${token}`,
      Accept: 'application/json',
    },
  })
    .then((response) => response.json())
    .then((versions) => versions.find((version) => version.name === versionName));
}

/**
 * Get all Jira issues associated to the given version id
 * @param versionId {string} The version id
 * @returns {Promise<Array<{id: string, key: string, fields: Record<string, any>}>>}
 */
export function getJiraIssuesOfVersion(versionId) {
  const token = process.env.JIRA_TOKEN;

  return fetch(`https://gravitee.atlassian.net/rest/api/3/search?jql=project=AM AND fixVersion=${versionId}`, {
    method: 'GET',
    headers: {
      Authorization: `Basic ${token}`,
      Accept: 'application/json',
    },
  })
    .then((response) => response.json())
    .then((issues) => issues.issues);
}
