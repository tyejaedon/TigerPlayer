@echo off
cd C:\TigerPlayer

REM Check current git status
echo === GIT STATUS ===
git status --short
echo.

REM Check if tag exists
echo === CHECKING v2.1.3 TAG ===
git tag -l v2.1.3
echo.

REM Publish the release using gh CLI
echo === PUBLISHING RELEASE v2.1.3 ===
gh release edit v2.1.3 --draft=false
echo.

REM Verify release is published
echo === VERIFYING RELEASE ===
gh release view v2.1.3 --json isDraft,title

echo.
echo === RELEASE PUBLISH COMPLETE ===

