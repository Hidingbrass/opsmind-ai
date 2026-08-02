#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    printf 'ERROR: required command not found: %s\n' "${command_name}" >&2
    return 127
  fi
}

run_step() {
  local label="$1"
  shift

  printf '\n==> %s\n' "${label}"
  if "$@"; then
    printf 'PASS: %s\n' "${label}"
  else
    local status=$?
    printf 'FAIL: %s (exit %s)\n' "${label}" "${status}" >&2
    return "${status}"
  fi
}

validate_compose() {
  docker compose \
    --project-directory "${PROJECT_ROOT}" \
    -f "${PROJECT_ROOT}/docker-compose.yml" \
    config --quiet
}

run_java_tests() {
  mvn \
    --batch-mode \
    --no-transfer-progress \
    --file "${PROJECT_ROOT}/backend-springboot/pom.xml" \
    test
}

run_python_tests() {
  local python_bin="python3"
  if [[ -x "${PROJECT_ROOT}/ai-agent-service/.venv/bin/python" ]]; then
    python_bin="${PROJECT_ROOT}/ai-agent-service/.venv/bin/python"
  fi

  (
    cd "${PROJECT_ROOT}/ai-agent-service"
    "${python_bin}" -m unittest discover -s tests -p 'test_*.py' -v
  )
}

build_frontend() {
  (
    cd "${PROJECT_ROOT}/frontend-dashboard"
    npm ci
    npm run build
  )
}

main() {
  require_command docker
  require_command mvn
  require_command npm

  if [[ ! -x "${PROJECT_ROOT}/ai-agent-service/.venv/bin/python" ]]; then
    require_command python3
  fi

  docker compose version >/dev/null

  printf 'OpsMind verification root: %s\n' "${PROJECT_ROOT}"
  run_step '[1/4] Docker Compose static validation' validate_compose
  run_step '[2/4] Java Maven tests' run_java_tests
  run_step '[3/4] Python unit tests' run_python_tests
  run_step '[4/4] React production build' build_frontend
  printf '\nAll OpsMind verification steps passed.\n'
}

main "$@"
