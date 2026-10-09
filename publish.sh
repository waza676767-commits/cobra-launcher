#!/bin/bash
# Life: publish the code in this folder to GitHub and build the Linux AppImage + Windows .exe there.
# Every step says what it does and stops with a clear message if something is wrong.
cd "$(dirname "$0")" || exit 1
ok()   { printf '\033[1;32m✔ %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m✘ %s\033[0m\n' "$*"; exit 1; }
step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

step "Checking the tools"
command -v git >/dev/null || fail "git is missing:  sudo dnf install git"
command -v gh  >/dev/null || fail "GitHub CLI is missing:  sudo dnf install gh"
gh auth status >/dev/null 2>&1 || fail "Not logged in to GitHub:  gh auth login   (then run this again)"
gh auth setup-git >/dev/null 2>&1
ok "git and gh are ready"

step "Checking this folder is your GitHub project"
[ -d .git ] || fail "This folder isn't connected to GitHub (no .git). Ask for the reconnect commands."
REMOTE=$(git remote get-url origin 2>/dev/null) || fail "No 'origin' remote. Ask for the reconnect commands."
REPO=$(echo "$REMOTE" | sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##')
ok "Repository: $REPO"
git config user.name >/dev/null || git config user.name "$(gh api user -q .login)"
git config user.email >/dev/null || git config user.email "$(gh api user -q .login)@users.noreply.github.com"

step "Saving and uploading your code"
git add -A
if git diff --cached --quiet; then
    ok "Nothing new to commit (uploading what's there)"
else
    git commit -qm "Life update $(date +%F-%H%M)" || fail "Commit failed"
    ok "Committed"
fi
git push -q origin HEAD:main || fail "Upload (push) failed. Run:  gh auth setup-git   and try again"
SHA=$(git rev-parse HEAD)
ok "Uploaded ${SHA:0:7}"

step "Asking GitHub to build and publish"
gh workflow run build.yml -R "$REPO" --ref main -f publish=true || fail "Couldn't start the build (is .github/workflows/build.yml in the repo?)"
RUN=""
for i in $(seq 1 30); do
    sleep 4
    RUN=$(gh run list -R "$REPO" --workflow build.yml --event workflow_dispatch -L 5 --json databaseId,headSha -q ".[] | select(.headSha==\"$SHA\") | .databaseId" | head -1)
    [ -n "$RUN" ] && break
done
[ -n "$RUN" ] || fail "The build didn't show up. Look at https://github.com/$REPO/actions"
ok "Build started: https://github.com/$REPO/actions/runs/$RUN"

step "Building Linux + Windows on GitHub (about 10–15 minutes)"
if ! gh run watch "$RUN" -R "$REPO" --exit-status; then
    echo; echo "The build failed. The important part:"
    gh run view "$RUN" -R "$REPO" --log-failed | tail -60
    fail "Send the lines above to get it fixed."
fi
ok "PUBLISHED"
echo "Download page: https://github.com/$REPO/releases/latest"
gh release view -R "$REPO" --web >/dev/null 2>&1 || true
