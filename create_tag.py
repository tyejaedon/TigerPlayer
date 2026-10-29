import subprocess
import os
import sys

os.chdir("C:\\TigerPlayer")

print("Current directory:", os.getcwd())

# Create tag
print("Creating tag v2.1.3...")
result = subprocess.run(["git", "tag", "v2.1.3"], capture_output=True, text=True)
print("Tag creation result:")
print("stdout:", result.stdout)
print("stderr:", result.stderr)
print("returncode:", result.returncode)

# Push tag
print("\nPushing tag to origin...")
result = subprocess.run(["git", "push", "origin", "v2.1.3"], capture_output=True, text=True)
print("Push result:")
print("stdout:", result.stdout)
print("stderr:", result.stderr)
print("returncode:", result.returncode)

# Verify tag exists
print("\nVerifying tag exists...")
result = subprocess.run(["git", "tag", "-l", "v2.1.3"], capture_output=True, text=True)
print("Tag verification:")
print("stdout:", result.stdout)
print("returncode:", result.returncode)

print("\nDone!")

import subprocess
import os
import sys

os.chdir("C:\\TigerPlayer")

print("Current directory:", os.getcwd())

# Create tag
print("Creating tag v2.1.3...")
result = subprocess.run(["git", "tag", "v2.1.3"], capture_output=True, text=True)
print("Tag creation result:")
print("stdout:", result.stdout)
print("stderr:", result.stderr)
print("returncode:", result.returncode)

# Push tag
print("\nPushing tag to origin...")
result = subprocess.run(["git", "push", "origin", "v2.1.3"], capture_output=True, text=True)
print("Push result:")
print("stdout:", result.stdout)
print("stderr:", result.stderr)
print("returncode:", result.returncode)

# Verify tag exists
print("\nVerifying tag exists...")
result = subprocess.run(["git", "tag", "-l", "v2.1.3"], capture_output=True, text=True)
print("Tag verification:")
print("stdout:", result.stdout)
print("returncode:", result.returncode)

print("\nDone!")
