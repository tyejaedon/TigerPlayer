import subprocess
import os

os.chdir("C:\\TigerPlayer")

# Run the check script and capture output
result = subprocess.run(
    ["python", "check_and_push_tag.py"],
    capture_output=True,
    text=True,
    timeout=60
)

# Write output to file
with open("C:\\TigerPlayer\\check_tag_output.log", "w") as f:
    f.write("=== Check and Push Tag Output ===\n")
    f.write(f"Return Code: {result.returncode}\n\n")
    f.write("=== STDOUT ===\n")
    f.write(result.stdout)
    f.write("\n=== STDERR ===\n")
    f.write(result.stderr)
    f.write("\n=== END ===\n")

print("Output written to check_tag_output.log")

