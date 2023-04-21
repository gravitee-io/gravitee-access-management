/**
 * Get the Ascii doc formatted changelog for input issues
 *
 * @param issues {Array<{id: string, fields: Array<{customfield_10115: string, summary: string}>}>}
 */
export function getChangelogFor(issues) {
  return issues
    .filter((issue) => {
      return issue.fields.issuetype.name === 'Public Bug';
    })
    .filter((issue) => {
      return issue.fields.status.name === 'Done';
    })
    .sort((issue1, issue2) => {
      // if null or undefined, put it at the end
      const githubIssueNumber1 = issue1.fields.customfield_10115;
      const githubIssueNumber2 = issue2.fields.customfield_10115;
      if (!githubIssueNumber1) {
        return 1;
      }
      if (!githubIssueNumber2) {
        return -1;
      }
      return githubIssueNumber1 - githubIssueNumber2;
    })
    .map((issue) => {
      const githubIssue = `${issue.fields.customfield_10115}`;
      const githubLink = `https://github.com/gravitee-io/issues/issues/${githubIssue}`;
      return `* ${issue.fields.summary} ${githubLink}[#${githubIssue}]`;
    })
    .join('\n');
}
