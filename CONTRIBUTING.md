# Contributing to StopScrolling

These are the rules for our team. They are short on purpose, so that everybody can follow them.

## Branches

- `main` always compiles and runs. Nobody pushes to `main` directly. All changes go through a pull request.
- We use one branch for each task (GitHub issue).
- Branch name: `<type>/<item-id>-<short-name>`, with the short name in lowercase and words separated by `-`.
  - `feature/US-08-new-group-screen`
  - `fix/US-06-app-icon-crash`
  - `spike/SP-01-usage-stats`
  - `docs/readme-build-steps`
- Types: `feature`, `fix`, `spike`, `docs`, `test`.
- Before starting a task, update `main` and create the branch from it:

  ```
  git checkout main
  git pull
  git checkout -b feature/US-08-new-group-screen
  ```

- Keep branches short (one to three days). Long branches create big merge conflicts.
- Delete the branch after the merge.

## Commits

- Make small commits, with a short message in English that starts with a verb: `Add Room entities for app groups`.
- Link the issue in the pull request description with `Closes #12`. GitHub closes the issue when the pull request is merged.
- Do not commit passwords, API keys, `local.properties` or build files. The `.gitignore` file already covers most of them.
- Every team member sets their own name and the email of their GitHub account in this project:

  ```
  git config user.name "Your Name"
  git config user.email "your.email@example.com"
  ```

## Pull requests

1. Push your branch and open a pull request to `main`.
2. Title: the item id and a short description, for example `US-08: Add new group screen`.
3. Description: use the template. Write what changed, how to test it, and `Closes #<issue number>`.
4. Ask at least one other team member to review it. Nobody merges their own pull request without an approval.
5. The reviewer reads the code, checks the checklist, and runs the app when the change can be seen on screen. Comments should be kind and clear. We try to review within one working day.
6. The author answers the comments by pushing new commits to the same branch.
7. We merge only when: the pull request is approved, the app compiles, there are no merge conflicts, and the checklist is done.
8. Use the normal **Merge pull request** button, so that the single commits stay visible in the history.
9. If there are merge conflicts, the author solves them by merging the latest `main` into the branch.

## Definition of Done

A task is done when all the points of the pull request checklist are true. The checklist is the same as the Definition of Done in the Sprint plan:

- The code is merged into `main` through a pull request reviewed by another team member.
- The app compiles and runs on an emulator or a physical device.
- There are no lint warnings.
- There is a unit test for the logic.
- The code has clear names and comments.
- New screens are checked for accessibility (contrast, content labels).
- All the acceptance criteria of the item are met.
- The GitHub issue is closed.
