import subprocess
import sys

try:
    # Push tag v2.1.3 to origin
    result = subprocess.run(
        ["git", "push", "origin", "v2.1.3"],
        cwd="C:\\TigerPlayer",
        capture_output=True,
        text=True,
        timeout=30
    )

    with open("C:\\TigerPlayer\\push_tag_result.txt", "w") as f:
        f.write("=== Push v2.1.3 Tag Result ===\n")
        f.write(f"Return Code: {result.returncode}\n")
        f.write(f"Stdout:\n{result.stdout}\n")
        f.write(f"Stderr:\n{result.stderr}\n")

    print("Push command executed - see push_tag_result.txt")
    sys.exit(result.returncode)

except Exception as e:
    with open("C:\\TigerPlayer\\push_tag_error.txt", "w") as f:
        f.write(f"Error: {str(e)}\n")
    print(f"Error: {e}")
    sys.exit(1)

