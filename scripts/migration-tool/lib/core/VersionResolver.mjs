import { execFileSync } from 'node:child_process';

const DOCKER_HUB_REGISTRY = 'graviteeio';
const CANONICAL_IMAGE = 'am-management-api';
const POM_PATH = 'gravitee-am-service/pom.xml';

/**
 * Parse a semver-like tag to major.minor.
 * Matches: 4.10.0, 4.11.0-alpha.3, 4.12.0-SNAPSHOT, 4.10
 * @param {string} tag
 * @returns {string|null} e.g. '4.10' or null if not parseable
 */
export function parseSemverTag(tag) {
    const match = tag.match(/^v?(\d+)\.(\d+)/);
    return match ? `${match[1]}.${match[2]}` : null;
}

/**
 * Parse a branch-latest tag pattern.
 * - 'master-latest' -> { branch: 'master', version: null }
 * - '4-10-x-latest' -> { branch: null, version: '4.10' }
 * @param {string} tag
 * @returns {{ branch: string|null, version: string|null }|null}
 */
export function parseBranchTag(tag) {
    if (!tag.endsWith('-latest')) return null;
    const prefix = tag.slice(0, -'-latest'.length);

    // Pattern: N-M-x (e.g. 4-10-x)
    const branchVersionMatch = prefix.match(/^(\d+)-(\d+)-x$/);
    if (branchVersionMatch) {
        return { branch: null, version: `${branchVersionMatch[1]}.${branchVersionMatch[2]}` };
    }

    // Pattern: branch name (e.g. master)
    if (/^[a-zA-Z][\w-]*$/.test(prefix)) {
        return { branch: prefix, version: null };
    }

    return null;
}

/**
 * Resolve version from a git branch by reading pom.xml.
 * @param {string} branch - e.g. 'master'
 * @returns {string} e.g. '4.12'
 * @throws if branch or pom not found
 */
export function resolveFromGit(branch) {
    try {
        const output = execFileSync('git', ['show', `origin/${branch}:${POM_PATH}`], {
            encoding: 'utf8',
            timeout: 10000
        });
        const versionMatch = output.match(/<version>(\d+\.\d+\.\d+[^<]*)<\/version>/);
        if (versionMatch) {
            return parseSemverTag(versionMatch[1]);
        }
        throw new Error(`No version found in pom.xml on branch ${branch}`);
    } catch (e) {
        throw new Error(
            `Could not resolve version from git branch '${branch}'. ` +
            `Ensure 'origin/${branch}' is fetched (run: git fetch origin ${branch}). ` +
            `Original error: ${e.message}`
        );
    }
}

/**
 * Resolve the 'latest' Docker Hub tag to its actual version.
 * Queries Docker Hub API to find the real semver tag that 'latest' points to.
 * @param {string} tag - should be 'latest'
 * @returns {Promise<string>} e.g. '4.10'
 */
export async function resolveFromDockerHub(tag) {
    const tagUrl = `https://hub.docker.com/v2/repositories/${DOCKER_HUB_REGISTRY}/${CANONICAL_IMAGE}/tags/${encodeURIComponent(tag)}/`;
    const tagRes = await fetch(tagUrl);
    if (!tagRes.ok) {
        throw new Error(`Docker Hub tag '${tag}' not found for ${DOCKER_HUB_REGISTRY}/${CANONICAL_IMAGE}`);
    }
    const tagData = await tagRes.json();
    const digest = tagData.images?.[0]?.digest;
    if (!digest) {
        throw new Error(`No digest found for tag '${tag}'`);
    }

    // Find semver tags sharing the same digest
    const listUrl = `https://hub.docker.com/v2/repositories/${DOCKER_HUB_REGISTRY}/${CANONICAL_IMAGE}/tags?page_size=50&ordering=last_updated`;
    const listRes = await fetch(listUrl);
    if (!listRes.ok) {
        throw new Error('Failed to list Docker Hub tags');
    }
    const listData = await listRes.json();

    for (const t of listData.results || []) {
        if (t.name === tag) continue;
        const version = parseSemverTag(t.name);
        if (version && t.images?.some(img => img.digest === digest)) {
            return version;
        }
    }

    throw new Error(`Could not find a semver tag matching digest of '${tag}'`);
}

/**
 * Resolve any tag to major.minor version string.
 * Tries in order: semver parse, branch-latest parse, git lookup, Docker Hub lookup.
 * @param {string} tag
 * @returns {Promise<string>} e.g. '4.12'
 */
export async function resolveTagToVersion(tag) {
    // 1. Try direct semver parse
    const semver = parseSemverTag(tag);
    if (semver) return semver;

    // 2. Try branch-latest pattern
    const branchInfo = parseBranchTag(tag);
    if (branchInfo) {
        if (branchInfo.version) return branchInfo.version;
        if (branchInfo.branch) return resolveFromGit(branchInfo.branch);
    }

    // 3. Try Docker Hub lookup for 'latest'
    if (tag === 'latest') {
        return resolveFromDockerHub(tag);
    }

    throw new Error(
        `Cannot resolve version from tag '${tag}'. ` +
        `Valid formats: semver (4.10.0, 4.11.0-alpha.3), branch-latest (master-latest, 4-10-x-latest), or 'latest'.`
    );
}

/**
 * Compare two major.minor version strings.
 * @returns {number} negative if a < b, 0 if equal, positive if a > b
 */
function compareVersions(a, b) {
    const [aMaj, aMin] = a.split('.').map(Number);
    const [bMaj, bMin] = b.split('.').map(Number);
    if (aMaj !== bMaj) return aMaj - bMaj;
    return aMin - bMin;
}

/**
 * Check if a tag requires a custom registry (ACR branch-latest tags).
 */
function requiresRegistry(tag) {
    const branch = parseBranchTag(tag);
    return branch !== null;
}

/**
 * Validate migration parameters and resolve versions.
 * @param {{ fromTag: string, toTag: string, registry?: string }} options
 * @returns {Promise<{ fromVersion: string, toVersion: string }>}
 * @throws with clear message on validation failure
 */
export async function validateMigrationParams(options) {
    const { fromTag, toTag, registry } = options;

    // Check registry requirement for ACR tags
    if (!registry && requiresRegistry(toTag)) {
        throw new Error(
            `Tag '${toTag}' is a branch-latest tag that requires --registry (e.g. --registry graviteeio.azurecr.io)`
        );
    }
    if (!registry && requiresRegistry(fromTag)) {
        throw new Error(
            `Tag '${fromTag}' is a branch-latest tag that requires --registry (e.g. --registry graviteeio.azurecr.io)`
        );
    }

    const fromVersion = await resolveTagToVersion(fromTag);
    const toVersion = await resolveTagToVersion(toTag);

    const cmp = compareVersions(fromVersion, toVersion);
    if (cmp === 0) {
        throw new Error(
            `from-tag '${fromTag}' and to-tag '${toTag}' both resolve to ${fromVersion}. No migration to test.`
        );
    }
    if (cmp > 0) {
        throw new Error(
            `from-tag '${fromTag}' (${fromVersion}) must be older than to-tag '${toTag}' (${toVersion}).`
        );
    }

    return { fromVersion, toVersion };
}
