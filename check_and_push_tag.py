#!/usr/bin/env python3
import subprocess
import json
import os
import sys

os.chdir("C:\\TigerPlayer")

# Step 1: Check if v2.1.3 tag exists locally
print("Step 1: Checking local tags...")
result = subprocess.run(["git", "tag", "-l", "v2.1.3"], capture_output=True, text=True)
print(f"Local v2.1.3 tag exists: {bool(result.stdout.strip())}")

# Step 2: Check if v2.1.3 tag exists on remote
print("\nStep 2: Checking remote tags...")
result = subprocess.run(["git", "ls-remote", "origin", "v2.1.3"], capture_output=True, text=True)
print(f"Remote v2.1.3 tag exists: {bool(result.stdout.strip())}")
print(f"Output: {result.stdout}")

# Step 3: Push tag if it doesn't exist on remote
if not result.stdout.strip():
    print("\nStep 3: Pushing v2.1.3 tag to remote...")
    result = subprocess.run(["git", "push", "origin", "v2.1.3"], capture_output=True, text=True)
    print(f"Return code: {result.returncode}")
    print(f"Stdout: {result.stdout}")
    print(f"Stderr: {result.stderr}")
else:
    print("\nStep 3: Tag already exists on remote, skipping push")

# Step 4: Check GitHub release
print("\nStep 4: Checking GitHub release...")
result = subprocess.run(
    ["gh", "release", "view", "v2.1.3", "--json", "isDraft,tagName,assets"],
    capture_output=True,
    text=True
)
print(f"Return code: {result.returncode}")
print(f"Stdout: {result.stdout}")
print(f"Stderr: {result.stderr}")

# Step 5: Check workflow runs
print("\nStep 5: Checking GitHub Actions workflow runs...")
result = subprocess.run(
    ["gh", "run", "list", "--workflow", "release.yml", "--limit", "5"],
    capture_output=True,
    text=True
)
print(f"Workflow runs output:")
print(result.stdout if result.stdout else "No output")
print(result.stderr if result.stderr else "No errors")

print("\nDone!")

