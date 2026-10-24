# shellcheck shell=bash
# Shared by backup.sh and restore-test.sh (RNF18). Sourced, never run on its own.
#
# The configuration comes from the environment or from .env.backup (see .env.backup-sample); nothing secret is in
# these scripts. Every path is relative to the root of the repository, so the scripts work from any directory (cron).

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT" || exit 1

# What is already in the environment wins over the file.
BACKUP_ENV_FILE=${BACKUP_ENV_FILE:-.env.backup}
if [ -f "$BACKUP_ENV_FILE" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|'#'*) continue ;; esac
    key=${line%%=*}
    [ -n "${!key+x}" ] || export "$key=${line#*=}"
  done < "$BACKUP_ENV_FILE"
fi

ENV_FILE=${BACKUP_COMPOSE_ENV_FILE:-.env.prod}
COMPOSE_FILE=${BACKUP_COMPOSE_FILE:-compose.prod.yaml}
BACKUP_DIR=${BACKUP_DIR:-backups}
BACKUP_PREFIX=${BACKUP_PREFIX:-booksmanager}
BACKUP_KEEP_LOCAL=${BACKUP_KEEP_LOCAL:-14}
BACKUP_REMOTE_KEEP_DAYS=${BACKUP_REMOTE_KEEP_DAYS:-90}
BACKUP_RCLONE_REMOTE=${BACKUP_RCLONE_REMOTE:-}
BACKUP_ALLOW_UNENCRYPTED=${BACKUP_ALLOW_UNENCRYPTED:-false}

# To stderr: the dump goes to a file through the standard output, and a failure inside that command would write its message there.
log() { printf '%s %s\n' "$(date '+%F %T')" "$*" >&2; }

# The Compose project name can be changed (BACKUP_COMPOSE_PROJECT), for a test with a stack of its own.
compose() {
  if [ -n "${BACKUP_COMPOSE_PROJECT:-}" ]; then
    docker compose -p "$BACKUP_COMPOSE_PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  else
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  fi
}

# Calls a monitoring URL (a Healthchecks.io style ping): the URL for success, the URL plus /fail for failure. A ping
# that cannot be sent is only a warning: it never turns a good backup into a failed one.
ping_url() {
  local base=$1 suffix=${2:-}
  [ -n "$base" ] || return 0
  curl --fail --silent --show-error --max-time 10 --retry 3 --output /dev/null "${base}${suffix}" \
    || log "WARNING: could not call the monitoring URL"
}
