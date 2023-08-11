/**
 * Get the MarkDown doc formatted changelog for input issues
 *
 * @param title {string}
 * @param issues {Array<{id: string, key: string, githubIssue: string, summary: string, components: Array<string>, issueTypeName: string, statusName: string}>}
 */
export function getChangelogFor(title, issues) {
  const authorizedIssueTypes = ['Public Bug', 'Public Security', 'Story'];

  const filteredIssues = issues
    .filter((issue) => {
      return !!issue.issueTypeName && authorizedIssueTypes.includes(issue.issueTypeName);
    })
    .filter((issue) => {
      return issue.statusName === 'Done';
    })
    .sort((issue1, issue2) => {
      // if null or undefined, put it at the end
      const githubIssueNumber1 = issue1.githubIssue;
      const githubIssueNumber2 = issue2.githubIssue;
      if (!githubIssueNumber1) {
        return 1;
      }
      if (!githubIssueNumber2) {
        return -1;
      }
      return githubIssueNumber1 - githubIssueNumber2;
    })
    .map((issue) => {
      const githubIssue = issue.githubIssue;
      let publicIssueContent = '';
      if (githubIssue) {
        const githubLink = `https://github.com/gravitee-io/issues/issues/${githubIssue}`;
        publicIssueContent = ` ${githubLink}[#${githubIssue}]`;
      }
      return `* ${issue.summary}${publicIssueContent}`;
    })
    .join('\n');

  if (filteredIssues.length === 0) {
    return '';
  }

  return `${title}

${filteredIssues}`;
}
