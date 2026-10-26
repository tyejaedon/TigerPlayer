#!/usr/bin/env python3
"""
Properly create v2.1.3 tag and push it
"""
import subprocess
import os

os.chdir("C:\\TigerPlayer")

# First, get the correct commit hash for HEAD
print("Getting HEAD commit...")
result = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True)
head_commit = result.stdout.strip()
print(f"HEAD commit: {head_commit}")

# Delete the incorrectly created tag if it exists
print("\nRemoving any existing v2.1.3 tag...")
subprocess.run(["git", "tag", "-d", "v2.1.3"], capture_output=True)

# Create tag properly
print("Creating v2.1.3 tag...")
result = subprocess.run(["git", "tag", "v2.1.3"], capture_output=True, text=True)
print(f"Tag creation result: returncode={result.returncode}")

# Verify tag was created
print("\nVerifying tag...")
result = subprocess.run(["git", "tag", "-l", "v2.1.3"], capture_output=True, text=True)
if result.stdout.strip():
    print(f"SUCCESS: Tag created - {result.stdout.strip()}")
else:
    print("FAILED: Tag not created")

# Push tag
print("\nPushing tag to GitHub...")
result = subprocess.run(["git", "push", "origin", "v2.1.3"], capture_output=True, text=True)
print(f"Return code: {result.returncode}")
if result.returncode == 0:
    print("SUCCESS: Tag pushed!")
else:
    print("Output:", result.stdout)
    print("Error:", result.stderr)

print("\nDone!")

