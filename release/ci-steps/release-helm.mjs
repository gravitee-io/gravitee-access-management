import { extractVersion } from '../helpers/version-helper.mjs';

console.log(chalk.magenta(`#############################################`));
console.log(chalk.magenta(`#  Open AM Helm chart PR for new Release  #`));
console.log(chalk.magenta(`#############################################`));

const releasingVersion = await extractVersion();
const helmRepository = 'https://github.com/gravitee-io/helm-charts';
const gitBranch = `release-am-chart-${releasingVersion}`;

echo(chalk.blue(`# Clone helm-charts repository`));
cd('/home/circleci');
await $`git clone --depth 1  ${helmRepository} --single-branch --branch=gh-pages`;
cd('/home/circleci/helm-charts');
await $`git checkout -b ${gitBranch}`;

await $`cp /home/circleci/project/helm/charts/am-${releasingVersion}.tgz /home/circleci/helm-charts/helm/am/am-${releasingVersion}.tgz`;

cd('/home/circleci/helm-charts');
await $`helm repo index --url https://helm.gravitee.io/helm helm`;
await $`mv helm/index.yaml .`;

await $`git add . && git commit -m "chore: Release AM Chart ${releasingVersion}"`;
await $`git push --set-upstream origin ${gitBranch}`;

const prBody = `
# New APIM Helm Chart version ${releasingVersion} has been released
`;
echo(chalk.blue('# Create PR on Github helm-charts repository'));
echo(prBody);
process.env.PR_BODY = prBody;

await $`gh pr create --title "[AM] Helm charts ${releasingVersion} release" --body "$PR_BODY" --base gh-pages --head ${gitBranch}`;
