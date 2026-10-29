#!/bin/bash
cd /c/TigerPlayer
git tag v2.1.3
git push origin v2.1.3
echo "v2.1.3 tag created and pushed"
git tag -l | grep v2.1.3

