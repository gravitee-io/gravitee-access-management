import { extractVersion } from '../helpers/version-helper.mjs';
import { getJiraIssuesOfVersion, getJiraVersion } from '../helpers/jira-helper.mjs';
import { getChangelogFor } from '../helpers/changelog-helper.mjs';
import { isDryRun } from '../helpers/option-helper.mjs';

console.log(chalk.magenta(`#############################################`));
console.log(chalk.magenta(`# 📰 Open docs PR for new Release Note #`));
console.log(chalk.magenta(`#############################################`));

const releasingVersion = await extractVersion();

echo(chalk.blue(`# Build current changelog branch name`));
const gitBranch = `changelog-AM-${releasingVersion}`;

echo(chalk.blue(`# Checking out on ${gitBranch} branch from master `));
await $`git checkout master && git checkout -b ${gitBranch}`;

echo(chalk.blue(`# Get changelog file `));
let changelogFileName = 'CHANGELOG-v3.adoc';
const docAmChangelogFile = '../' + changelogFileName;

echo(chalk.blue(`# Write changelog to ${docAmChangelogFile}`));

const version = await getJiraVersion(releasingVersion);
const issues = await getJiraIssuesOfVersion(version.name);

const features = issues.filter((issue) => issue.fields.issuetype.name === 'Story');

const gatewayIssues = issues
  .filter((issue) => !features.includes(issue))
  .filter((issue) => issue.fields.components.some((cmp) => cmp.name === 'Gateway'));

const managementAPIIssues = issues
  .filter((issue) => !features.includes(issue))
  .filter((issue) => !gatewayIssues.includes(issue))
  .filter((issue) => issue.fields.components.some((cmp) => cmp.name === 'Management API'));

const consoleIssues = issues
  .filter((issue) => !features.includes(issue))
  .filter((issue) => !managementAPIIssues.includes(issue))
  .filter((issue) => !gatewayIssues.includes(issue))
  .filter((issue) => issue.fields.components.some((cmp) => cmp.name === 'Console'));

const otherIssues = issues
  .filter((issue) => !features.includes(issue))
  .filter((issue) => !managementAPIIssues.includes(issue))
  .filter((issue) => !gatewayIssues.includes(issue))
  .filter((issue) => !consoleIssues.includes(issue));

let changelogPatchTemplate = `
== AM - ${releasingVersion} (${new Date().toISOString().slice(0, 10)})

${getChangelogFor("== What's new !", features)}

${getChangelogFor('=== Gateway', gatewayIssues)}

${getChangelogFor('=== Management API', managementAPIIssues)}

${getChangelogFor('=== Console', consoleIssues)}

${getChangelogFor('=== Other', otherIssues)}
`;

echo(changelogPatchTemplate);

// write after anchor
const changelogFileContent = fs.readFileSync(docAmChangelogFile, 'utf8');
const changelogFileContentWithPatch = changelogFileContent.replace(
  '# Change Log',
  `# Change Log
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
await $`git add ./${docAmChangelogFile}`;
await $`git commit -m "chore: add changelog for ${releasingVersion}"`;

if (!dryRun) {
  await $`git push --set-upstream origin ${gitBranch}`;
}

const prBody = `
# New version ${releasingVersion} has been released
📝 You can modify the changelog template online [here](https://github.com/gravitee-io/gravitee-access-management/edit/${gitBranch}/${changelogFileName})

## Jira issues

[See all Jira issues for ${releasingVersion} version](https://gravitee.atlassian.net/jira/software/c/projects/AM/issues/?jql=project%20%3D%20%22AM%22%20and%20fixVersion%20%3D%20${releasingVersion}%20and%20status%20%3D%20Done%20ORDER%20BY%20created%20DESC)
`;
echo(chalk.blue('# Create PR on Github Doc repository'));
echo(prBody);
process.env.PR_BODY = prBody;

if (!dryRun) {
  const releaseNotesPrUrl =
    await $`gh pr create --title "chore: Add changelog for new ${releasingVersion} release" --body "$PR_BODY" --base master --head ${gitBranch}`;
  $`echo ${releaseNotesPrUrl.stdout} > /tmp/releaseNotesPrUrl.txt`;
}
