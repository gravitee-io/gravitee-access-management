/**
 * Resolves floating AM Docker tags (latest, 4, 4.11, ...) to the concrete X.Y.Z version they
 * currently point to, using the public Docker Hub v2 API (same source as VersionValidator).
 *
 * A floating tag and the concrete version it points to share the same manifest digest, so we read
 * the floating tag's digest and find the plain X.Y.Z tag with a matching digest. No registry auth
 * or manifest-blob fetch is needed. Variant-suffixed tags (latest-debian) do not share a digest
 * with a plain X.Y.Z and therefore fail to resolve (by design — fail fast).
 */
const REGISTRY = 'graviteeio';
const CONCRETE = /^\d+\.\d+\.\d+$/;
const MAX_PAGES = 5;
const PAGE_SIZE = 100;

/** True iff `tag` is a plain X.Y.Z version (no floating alias, no variant suffix). */
export function isConcreteVersion(tag) {
    return CONCRETE.test(String(tag));
}

/**
 * Resolve a floating tag to a concrete X.Y.Z. Concrete tags are returned unchanged with no network
 * call. Throws on no match or fetch failure.
 * @param {string} tag
 * @param {{imageName?: string}} [opts]
 * @returns {Promise<string>}
 */
export async function resolveFloatingTag(tag, { imageName = 'am-management-api' } = {}) {
    if (isConcreteVersion(tag)) return tag;

    const base = `https://hub.docker.com/v2/repositories/${REGISTRY}/${imageName}`;

    const tagRes = await fetch(`${base}/tags/${encodeURIComponent(tag)}/`, { method: 'GET' });
    if (!tagRes.ok) {
        throw new Error(
            `Could not look up floating tag "${tag}" for ${REGISTRY}/${imageName} ` +
            `(HTTP ${tagRes.status}). Check the tag exists at https://hub.docker.com/r/${REGISTRY}/${imageName}/tags.`
        );
    }
    const { digest } = await tagRes.json();
    if (!digest) {
        throw new Error(`Floating tag "${tag}" for ${REGISTRY}/${imageName} has no manifest digest to match.`);
    }

    for (let page = 1; page <= MAX_PAGES; page++) {
        const url = `${base}/tags/?page_size=${PAGE_SIZE}&ordering=last_updated&page=${page}`;
        const res = await fetch(url, { method: 'GET' });
        if (!res.ok) {
            throw new Error(
                `Could not list tags for ${REGISTRY}/${imageName} while resolving "${tag}" ` +
                `(HTTP ${res.status} on page ${page}).`
            );
        }
        const data = await res.json();
        const results = data.results || [];
        const match = results.find((t) => isConcreteVersion(t.name) && t.digest === digest);
        if (match) return match.name;
        if (!data.next) break;
    }

    throw new Error(
        `Could not resolve floating tag "${tag}" to a concrete version for ${REGISTRY}/${imageName}. ` +
        `No X.Y.Z tag shares its image digest. Use a concrete version tag (e.g. 4.11.9).`
    );
}
