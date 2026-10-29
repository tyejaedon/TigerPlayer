#!/usr/bin/env python3
"""
Final script to push v2.1.3 tag and trigger GitHub Actions workflow
This will write all output to a log file for verification
"""
import subprocess
import sys
from datetime import datetime

log_file = "C:\\TigerPlayer\\final_push_log.txt"

def log_write(message):
    print(message)
    with open(log_file, "a") as f:
        f.write(message + "\n")

# Clear log
with open(log_file, "w") as f:
    f.write(f"=== v2.1.3 Release Tag Push Log ===\n")
    f.write(f"Started: {datetime.now()}\n\n")

try:
    log_write("Step 1: Checking working directory")
    import os
    os.chdir("C:\\TigerPlayer")
    log_write(f"Current directory: {os.getcwd()}")

    log_write("\nStep 2: Checking git status")
    result = subprocess.run(["git", "status", "--short"], capture_output=True, text=True, timeout=10)
    log_write(f"Git status:\n{result.stdout if result.stdout else 'Clean'}")

    log_write("\nStep 3: Verifying v2.1.3 tag exists locally")
    result = subprocess.run(["git", "tag", "-l", "v2.1.3"], capture_output=True, text=True, timeout=10)
    if result.stdout.strip():
        log_write(f"✓ Local tag found: {result.stdout.strip()}")
    else:
        log_write("✗ Local tag not found!")
        sys.exit(1)

    log_write("\nStep 4: Pushing v2.1.3 tag to GitHub")
    log_write("Running: git push origin v2.1.3")
    result = subprocess.run(
        ["git", "push", "origin", "v2.1.3"],
        capture_output=True,
        text=True,
        timeout=30,
        cwd="C:\\TigerPlayer"
    )

    log_write(f"Return code: {result.returncode}")
    if result.stdout:
        log_write(f"Stdout:\n{result.stdout}")
    if result.stderr:
        log_write(f"Stderr:\n{result.stderr}")

    if result.returncode == 0:
        log_write("\n✓ SUCCESS: Tag pushed to GitHub!")
    else:
        log_write(f"\n✗ FAILED: Push returned code {result.returncode}")

    log_write("\nStep 5: Verifying tag on remote")
    result = subprocess.run(
        ["git", "ls-remote", "origin", "v2.1.3"],
        capture_output=True,
        text=True,
        timeout=10
    )
    log_write(f"Remote tag check:\n{result.stdout if result.stdout else 'Not found'}")

    log_write("\nStep 6: Checking GitHub release")
    result = subprocess.run(
        ["gh", "release", "view", "v2.1.3", "--json", "isDraft"],
        capture_output=True,
        text=True,
        timeout=10
    )
    log_write(f"Release status:\n{result.stdout if result.stdout else result.stderr}")

    log_write("\n" + "="*60)
    log_write("Log written to: C:\\TigerPlayer\\final_push_log.txt")
    log_write("="*60)

    with open(log_file, "a") as f:
        f.write(f"\nCompleted: {datetime.now()}\n")

except Exception as e:
    log_write(f"\n✗ ERROR: {str(e)}")
    sys.exit(1)

