#!/bin/sh
# Throwaway PostgreSQL 18 cluster for integration tests (Linux / macOS / Git Bash).
#
#   sh scripts/dev/test-cluster.sh init     # create, start, ums_test, roles
#   sh scripts/dev/test-cluster.sh start | stop | status
#   sh scripts/dev/test-cluster.sh roles    # re-run create_roles.sql
#   sh scripts/dev/test-cluster.sh env      # print the env vars for mvn verify
#
# Data lives in .tools/pg18-it (git-ignored), listens on localhost:55432 only, trust authentication.
# The role passwords below are fixed throwaway values for this local trust-auth cluster only;
# never use them anywhere else. Binaries: $UMS_PG_BIN, else /usr/pgsql-18/bin (Fedora), else PATH.
set -eu

command=${1:-status}
port=${UMS_TEST_PORT:-55432}
root=$(cd "$(dirname "$0")/../.." && pwd)
data="$root/.tools/pg18-it"
database=ums_test
PGOPTIONS='-c client_min_messages=warning'
export PGOPTIONS

if [ -n "${UMS_PG_BIN:-}" ]; then
  bin="$UMS_PG_BIN/"
elif [ -x /usr/pgsql-18/bin/pg_ctl ]; then
  bin=/usr/pgsql-18/bin/
else
  bin=""
fi

psql_admin() {
  "${bin}psql" -h localhost -p "$port" -U postgres -X "$@"
}

running() {
  [ -f "$data/PG_VERSION" ] && "${bin}pg_ctl" -D "$data" status >/dev/null 2>&1
}

start_cluster() {
  if running; then
    echo "Already running on port $port"
    return
  fi
  "${bin}pg_ctl" -D "$data" -l "$data/server.log" -w -t 60 \
    -o "-p $port -c listen_addresses=localhost" start >/dev/null
  echo "Started PostgreSQL 18 on localhost:$port"
}

apply_roles() {
  psql_admin -d "$database" -q -v ON_ERROR_STOP=1 \
    -v owner_pw=it-owner -v app_pw=it-app -v console_pw=it-console \
    -v listener_pw=it-listener -v backup_pw=it-backup \
    -f "$root/scripts/db/create_roles.sql"
  echo "Roles applied to $database"
}

show_env() {
  cat <<EOF
export UMS_TEST_DB_URL=jdbc:postgresql://localhost:$port/$database
export UMS_TEST_DB_USER=postgres
export UMS_TEST_DB_PASSWORD=postgres
export UMS_DB_PASSWORD=it-app
export UMS_CONSOLE_DB_PASSWORD=it-console
export UMS_LISTENER_DB_PASSWORD=it-listener
export UMS_OWNER_DB_PASSWORD=it-owner
export UMS_BACKUP_DB_PASSWORD=it-backup
EOF
}

case "$command" in
  init)
    if ! "${bin}initdb" --version | grep -q ' 18\.'; then
      echo "Expected PostgreSQL 18: $("${bin}initdb" --version)" >&2
      exit 1
    fi
    if [ ! -f "$data/PG_VERSION" ]; then
      mkdir -p "$(dirname "$data")"
      "${bin}initdb" -D "$data" -U postgres --auth=trust --encoding=UTF8 --locale=C >/dev/null
    fi
    start_cluster
    if [ "$(psql_admin -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '$database'")" != "1" ]; then
      psql_admin -d postgres -q -c "CREATE DATABASE $database ENCODING 'UTF8'"
    fi
    apply_roles
    show_env
    ;;
  start) start_cluster ;;
  stop)
    if running; then
      "${bin}pg_ctl" -D "$data" -m fast -w stop >/dev/null
      echo "Stopped"
    else
      echo "Not running"
    fi
    ;;
  status)
    if running; then echo "Running on localhost:$port ($data)"; else echo "Not running"; fi
    ;;
  roles) apply_roles ;;
  env) show_env ;;
  *)
    echo "usage: $0 {init|start|stop|status|roles|env}" >&2
    exit 2
    ;;
esac
