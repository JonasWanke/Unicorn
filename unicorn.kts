unicorn {
  command("issue",  false) {
    command("assign",
      help = "") {
      val repo = GitHub.currentRepo
      val issue by argument("id", help = "")
        .int()
        .map { repo.issues[it] }

      {
        issue.assign(GitHub.myself)

        repo.createBranch(issue.formatTitleForBranch(), base = Branch.Type.DEV)
      }
    }

    command("complete") {
      val repo = GitHub.currentRepo
      val branch = repo.currentBranch
      if (branch !is IssueBranch)
        throw IllegalStateException("Current branch is not an issue branch")

      val issue = branch.issue
      issue.createPr()
      repo.openPrPage(branch,
          title = ConventionalCommits.formatTitle(issue),
          labels = issue.labels,
          assignee = GitHub.myself,
          openInBrowser = true)
    }
  }
}
