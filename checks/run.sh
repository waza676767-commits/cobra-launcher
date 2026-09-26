#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p checks/classes
find common/src/main/java -name '*.java' > checks/sources.txt
java -m jdk.compiler/com.sun.tools.javac.Main -d checks/classes @checks/sources.txt
java -m jdk.compiler/com.sun.tools.javac.Main -cp checks/classes -d checks/classes checks/Regression.java checks/FailRecorder.java
java -cp checks/classes Regression
java -cp checks/classes FailRecorder
