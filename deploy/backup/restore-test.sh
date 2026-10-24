#!/usr/bin/env bash
# Monthly restoration test (RNF18): restores the newest backup into a temporary database, checks the basics, and drops
# the temporary database. It never touches the real database. Run it from cron (docs/implantacao.md, section 7).
#
#   restore-test.sh [backup-file]      without an argument, the newest backup in the backup folder is used
#
# The result goes to the log of the backup folder and, when BACKUP_RESTORE_PING_URL is set, to the monitoring service.
# -E: the ERR trap also runs for a failure inside a function (compose, ping_url); without it that failure would be silent.
set -Eeuo pipefail
# shellcheck source=deploy/backup/lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

BACKUP_RESTORE_PING_URL=${BACKUP_RESTORE_PING_URL:-}
BACKUP_MAX_AGE_HOURS=${BACKUP_MAX_AGE_HOURS:-48}
RESULT_LOG="$BACKUP_DIR/restore-test.log"
tmp_db="restore_test_$(date +%s)"
created=false

psql_tmp() { compose exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d $tmp_db -tAc \"\$1\"" -- "$1"; }

cleanup() {
  if [ "$created" = true ]; then
    compose exec -T postgres sh -c "dropdb -U \"\$POSTGRES_USER\" --if-exists --force $tmp_db" > /dev/null 2>&1 \
      || log "WARNING: could not drop the temporary database $tmp_db"
  fi
}

record() {
  mkdir -p "$BACKUP_DIR"
  chmod 700 "$BACKUP_DIR"
  printf '%s %s\n' "$(date '+%F %T')" "$*" >> "$RESULT_LOG"
}

fail() {
  local code=$?
  log "ERROR: the restoration test failed (line $1, exit $code): $2"
  record "FAILED (line $1): $2"
  ping_url "$BACKUP_RESTORE_PING_URL" /fail
  exit 1
}
trap 'fail $LINENO "$BASH_COMMAND"' ERR
trap cleanup EXIT

umask 077
if [ $# -ge 1 ]; then
  file=$1
else
  # shellcheck disable=SC2012  # the names are the ones written by backup.sh, never arbitrary
  file=$(ls -1t -- "$BACKUP_DIR/$BACKUP_PREFIX"-*.dump 2> /dev/null | head -n 1 || true)
  [ -n "$file" ] || { log "ERROR: no backup in $BACKUP_DIR"; false; }
  # The daily backup must really be running: a month-old file as the "newest" means it stopped.
  if [ -z "$(find "$file" -mmin "-$((BACKUP_MAX_AGE_HOURS * 60))" -print)" ]; then
    log "ERROR: the newest backup ($file) is older than $BACKUP_MAX_AGE_HOURS hours: the daily backup is not running"
    false
  fi
fi
[ -s "$file" ] || { log "ERROR: $file is missing or empty"; false; }

log "restoring $file into the temporary database $tmp_db"
compose exec -T postgres sh -c "createdb -U \"\$POSTGRES_USER\" $tmp_db"
created=true
compose exec -T postgres sh -c "pg_restore -U \"\$POSTGRES_USER\" -d $tmp_db --no-owner --exit-on-error" < "$file"

# The migrations of Flyway: there must be some, and none may have failed.
applied=$(psql_tmp "select count(*) from flyway_schema_history where success")
failed=$(psql_tmp "select count(*) from flyway_schema_history where not success")
latest=$(psql_tmp "select max(installed_rank) from flyway_schema_history")
[ "$applied" -gt 0 ] || { log "ERROR: no migration applied in the backup"; false; }
[ "$failed" -eq 0 ] || { log "ERROR: $failed failed migration(s) in the backup"; false; }

# The main tables exist and can be read; there is always at least the first administrator.
summary="migrations=$applied (last rank $latest)"
for table in app_user reader book copy loan fine reservation library_configuration; do
  count=$(psql_tmp "select count(*) from $table")
  summary="$summary $table=$count"
  if [ "$table" = app_user ] && [ "$count" -eq 0 ]; then
    log "ERROR: the backup has no user at all"
    false
  fi
done

log "restoration test OK: $summary"
record "OK $(basename -- "$file") $summary"
ping_url "$BACKUP_RESTORE_PING_URL"
