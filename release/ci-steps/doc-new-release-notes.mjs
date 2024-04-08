import { computeVersion, extractVersion } from '../helpers/version-helper.mjs';
import { getJiraIssuesOfVersion, getJiraVersion } from '../helpers/jira-helper.mjs';
import { getChangelogFor } from '../helpers/changelog-helper.mjs';
import { isDryRun } from '../helpers/option-helper.mjs';

console.log(chalk.magenta(`#############################################`));
console.log(chalk.magenta(`# 📰 Open docs PR for new Release Note #`));
console.log(chalk.magenta(`#############################################`));

const releasingVersion = await extractVersion();
const versions = computeVersion(releasingVersion);
const dateOptions = { year: 'numeric', month: 'long', day: 'numeric' };

const docRepository = 'gravitee-platform-docs';
const docRepositoryURL = `https://github.com/gravitee-io/${docRepository}`;
const docAmChangelogFolder = `docs/am/${versions.trimmed}/releases-and-changelog/changelog/`;
const docAmChangelogFile = `${docAmChangelogFolder}am-${versions.branch}.md`;
const localTmpFolder = '.tmp';

echo(chalk.blue(`# Build current changelog branch name`));
const gitBranch = `changelog-am-${releasingVersion}`;

echo(chalk.blue(`# Create local tmp folder: ${localTmpFolder}`));
await $`mkdir -p ${localTmpFolder}`;
cd(localTmpFolder);
await $`rm -rf ${docRepository}`;

echo(chalk.blue(`# Clone ${docRepository} repository`));
await $`git clone --depth 1  ${docRepositoryURL} --single-branch --branch=main`;
cd(docRepository);

const version = await getJiraVersion(releasingVersion);
if (version === undefined) {
  echo(chalk.blue(`No Jira release found for: ${releasingVersion}, nothing to do.`));
  process.exit(0);
}

const issues = await getJiraIssuesOfVersion(version.name);

const features = issues.filter((issue) => issue.issueTypeName === 'Story');

const gatewayIssues = issues
  .filter((issue) => !features.includes(issue))
  .filter((issue) => issue.components.some((cmp) => cmp.name === 'Gateway'));

const managementAPIIssues = issues
  .filter((issue) => !features.includes(issue))
  .filter((issue) => !gatewayIssues.includes(issue))
  .filter((issue) => issue.components.some((cmp) => cmp.name === 'Management API'));

const consoleIssues = issues
  .filter((issue) => !features.includes(issue))
  .filter((issue) => !managementAPIIssues.includes(issue))
  .filter((issue) => !gatewayIssues.includes(issue))
  .filter((issue) => issue.components.some((cmp) => cmp.name === 'Console'));

const otherIssues = issues
  .filter((issue) => !features.includes(issue))
  .filter((issue) => !managementAPIIssues.includes(issue))
  .filter((issue) => !gatewayIssues.includes(issue))
  .filter((issue) => !consoleIssues.includes(issue));

const whatsNewChangelogSection =
  features.length > 0
    ? `
<details>

<summary>What's new !</summary>

${getChangelogFor("=**What's new!**", features)}

</details>

` : '';

let changelogPatchTemplate = `
## Gravitee Access Management ${releasingVersion} - ${new Date().toLocaleDateString('en-US', dateOptions)}
${whatsNewChangelogSection}
<details>

<summary>Bug fixes</summary>

${getChangelogFor('**Gateway**', gatewayIssues)}

${getChangelogFor('**Management API**', managementAPIIssues)}

${getChangelogFor('**Console**', consoleIssues)}

${getChangelogFor('**Other**', otherIssues)}

</details>
`;

echo(changelogPatchTemplate);

// write after anchor
const changelogFileContent = fs.readFileSync(docAmChangelogFile, 'utf8');
const changelogFileContentWithPatch = changelogFileContent.replace(
  `# AM ${versions.branch}`,
  `$&
${changelogPatchTemplate}`,
);
fs.writeFileSync(`${docAmChangelogFile}`, changelogFileContentWithPatch);

const dryRun = isDryRun();

echo(chalk.blue(`# Commit and push changelog to ${gitBranch}`));
try {
  if (!dryRun) {
    await $`gh pr close --delete-branch ${gitBranch}`;
  }
} catch (e) {
  // Best effort to have no open PR before creating a new one
}
await $`git checkout -b ${gitBranch}`;
await $`git add ./${docAmChangelogFile}`;
await $`git commit -m "chore: add changelog for ${releasingVersion}"`;

if (!dryRun) {
  await $`git push --set-upstream origin ${gitBranch}`;
}

const prBody = `
# AM ${releasingVersion} has been released
📝 Please review and merge this pull request to add the changelog to the documentation.

## Jira issues

[See all Jira issues for ${releasingVersion} version](https://gravitee.atlassian.net/jira/software/c/projects/AM/issues/?jql=project%20%3D%20%22AM%22%20and%20fixVersion%20%3D%20${releasingVersion}%20and%20status%20%3D%20Done%20ORDER%20BY%20created%20DESC)
`;
echo(chalk.blue('# Create PR on Github Doc repository'));
echo(prBody);
process.env.PR_BODY = prBody;

if (!dryRun) {
  const releaseNotesPrUrl =
    await $`gh pr create --title "chore: Add changelog for new ${releasingVersion} release" --body "$PR_BODY" --base main --head ${gitBranch}`;
  $`echo ${releaseNotesPrUrl.stdout} > /tmp/releaseNotesPrUrl.txt`;
}
