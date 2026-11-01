@echo off
REM This batch script will push the v2.1.3 tag to GitHub and trigger the CI/CD workflow

echo.
echo ============================================================
echo TigerPlayer v2.1.3 Release - Push Tag and Trigger Workflow
echo ============================================================
echo.

cd /d C:\TigerPlayer

echo Step 1: Verify local tag exists
echo.
git tag -l v2.1.3
echo.

echo Step 2: Check remote for tag
echo.
git ls-remote origin v2.1.3
echo.

echo Step 3: Push tag to GitHub (will trigger release.yml workflow)
echo.
git push origin v2.1.3
echo.

if %ERRORLEVEL% EQU 0 (
    echo SUCCESS: Tag pushed to origin
    echo.
    echo The GitHub Actions "Release candidate" workflow should now be triggered.
    echo It will:
    echo   - Build both foss and full release APKs
    echo   - Build the full flavor AAB for Play Store
    echo   - Generate SHA256SUMS.txt and R8 mapping files
    echo   - Generate SPDX SBOM
    echo   - Attach all artifacts to the GitHub Release
    echo.
    echo Monitor the workflow at:
    echo https://github.com/tyejaedon/TigerPlayer/actions/workflows/release.yml
    echo.
) else (
    echo FAILED: Tag push encountered an error
    echo Error code: %ERRORLEVEL%
    echo.
)

echo Step 4: Verify release on GitHub
echo.
gh release view v2.1.3
echo.

echo Step 5: Check workflow status
echo.
gh run list --workflow release.yml --limit 3
echo.

echo ============================================================
echo Done!
echo ============================================================
pause

