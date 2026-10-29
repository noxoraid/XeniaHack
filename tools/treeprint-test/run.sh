#!/bin/sh
set -e
cd "$(dirname "$0")"
g++ -std=c++17 -O1 -g -Wall -Wextra -fsanitize=address,undefined -fno-omit-frame-pointer \
    -I../../app/src/main/cpp main.cpp ../../app/src/main/cpp/treeprint.cpp -o /tmp/treeprint_test
/tmp/treeprint_test
g++ -std=c++17 -fsyntax-only -Wall -Wextra -Istub -I../../app/src/main/cpp ../../app/src/main/cpp/treeprint_jni.cpp
echo "jni syntax ok"
