#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
LOCAL_TOOLS_DIR="$PROJECT_DIR/.local-tools"

export JAVA_HOME="$LOCAL_TOOLS_DIR/jdk-21"
export GRADLE_USER_HOME="$LOCAL_TOOLS_DIR/gradle-home"
export PATH="$JAVA_HOME/bin:/usr/bin:/bin"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "Java 21 is missing: $JAVA_HOME/bin/java" >&2
    exit 1
fi

exec bash "$PROJECT_DIR/gradlew" "$@"
