/**
 * Validates that a given AM Docker image tag exists before deploy/upgrade.
 * Fails early to avoid long Helm waits and unclear errors.
 */
const REGISTRY = 'graviteeio';

/**
 * Check if an image tag exists via Docker Hub v2 API (public, no auth).
 * @param {string} imageName - e.g. 'am-management-api'
 * @param {string} tag - e.g. '4.10.0'
 * @returns {Promise<boolean>}
 */
export async function checkImageTagExists(imageName, tag) {
    const url = `https://hub.docker.com/v2/repositories/${REGISTRY}/${imageName}/tags/${encodeURIComponent(tag)}/`;
    try {
        const res = await fetch(url, { method: 'GET' });
        if (!res.ok) return false;
        const data = await res.json();
        return data.name === tag;
    } catch (e) {
        return false;
    }
}

/**
 * Validate that the given version tag exists for at least one AM image (we check am-management-api as canonical).
 * @param {string} tag - e.g. '4.10.0'
 * @param {string} [canonicalImage] - image to check (default: am-management-api)
 * @throws {Error} if tag does not exist
 */
export async function validateAmImageTag(tag, canonicalImage = 'am-management-api') {
    const exists = await checkImageTagExists(canonicalImage, tag);
    if (!exists) {
        throw new Error(
            `AM image tag "${tag}" not found for ${REGISTRY}/${canonicalImage}. ` +
            `Check https://hub.docker.com/r/${REGISTRY}/${canonicalImage}/tags and use an existing tag (e.g. 4.10.0).`
        );
    }
}
