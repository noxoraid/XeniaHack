#!/bin/sh
set -e
cd "$(dirname "$0")"
g++ -std=c++17 -g -O1 -Wall -Wextra -fsanitize=address,undefined -fno-sanitize-recover=all -I. -I../../app/src/main/cpp main.cpp ../../app/src/main/cpp/gitblob.cpp -o /tmp/gitblob-test
/tmp/gitblob-test
