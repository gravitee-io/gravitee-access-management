/**
 * Get the version provided as argument or exit the process
 * @returns {Promise<string>} the version to release
 */
export async function extractVersion() {
  if (argv.version) {
    return argv.version;
  } else {
    console.log(chalk.red("You have to provide the version to release with '--version=VERSION'"));
    process.exit();
  }
}

/**
 * @param {string} releasingVersion, for example: '3.15.11'
 * @returns {{version: string, branch: string, trimmed: string}} the version to release, the branch to release and the
 * trimmed version (looking like {'version': '3.15.11', 'branch': '3.15.x', 'trimmed': '3.15'})
 */
export function computeVersion(releasingVersion) {
  return {
    version: releasingVersion,
    branch: branch(releasingVersion),
    trimmed: trimmed(releasingVersion),
    pattern: pattern(releasingVersion),
  };
}

/**
 * Returns the branch related to the version to release. If patch version is 0, then the branch is master, else it's Major.Minor.x
 * @param releasingVersion
 * @returns {string|string}
 */
function branch(releasingVersion) {
  const split = releasingVersion.split('.');
  return split[2] === 0 ? 'master' : `${split[0]}.${split[1]}.x`;
}

function trimmed(releasingVersion) {
  const split = releasingVersion.split('.');
  return `${split[0]}.${split[1]}`;
}

function pattern(releasingVersion) {
  const releaseBranch = branch(releasingVersion);
  return releaseBranch === 'master' ? '' : releaseBranch.replace('x', '*');
}
