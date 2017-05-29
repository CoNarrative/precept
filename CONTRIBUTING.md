# Contributing

We want contributing to Precept to be easy. If you have any suggestions for how we may improve our process, please don't hesitate to open an issue or ping Alex on Discord.

Contributions start with an issue. We like to discuss changes before anyone invests time and effort on a feature or fix.

Once the change is proposed and discussed, we will assign the issue to you. This helps avoid two people working on the same problem and gives everyone a picture of what issues are being worked on and who's working on them.

We follow the usual Github "fork, branch, pull-request" process.

If you're working on issue # 1, name your branch `issue-1`. This makes it easier for us to map your pull request to the discussion and proposed changes and prevents you from having to come up with a creative yet succinct branch name. :simple-smile:

All pull requests must pass CircleCI before being merged. You can run all tests locally with:
```
lein test :only precept.test-runner`
```

Once a pull request has been opened, it is reviewed and commented. Additional changes may be requested.

You may choose to squash your commits, but we don't require it. We can do this via Github when merging your pull request into `master`.
