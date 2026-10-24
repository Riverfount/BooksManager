#!/usr/bin/env bash
# Daily backup of the database (RNF18): pg_dump in the custom format, a check of the file, retention, and a copy out of
# the server through rclone. Run it from cron (docs/implantacao.md, section 7). Nothing secret is in this file.
#
# Without BACKUP_RCLONE_REMOTE the backup stays on the server only, and says so in the log. Without BACKUP_PING_URL
# nobody is called when it fails: the log is the only trace.
# -E: the ERR trap also runs for a failure inside a function (compose, ping_url); without it that failure would be silent.
set -Eeuo pipefail
# shellcheck source=deploy/backup/lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

BACKUP_PING_URL=${BACKUP_PING_URL:-}
tmp=""

fail() {
  local code=$?
  log "ERROR: the backup failed (line $1, exit $code): $2"
  [ -z "$tmp" ] || rm -f -- "$tmp"
  ping_url "$BACKUP_PING_URL" /fail
  exit 1
}
trap 'fail $LINENO "$BASH_COMMAND"' ERR

# The backup carries the name, the document and the e-mail of the readers, minors included (LGPD): it only leaves the
# server encrypted, so the remote must be an rclone "crypt" remote.
check_remote_is_encrypted() {
  local name=${BACKUP_RCLONE_REMOTE%%:*}
  [ "$BACKUP_ALLOW_UNENCRYPTED" = true ] && { log "WARNING: sending the backup WITHOUT encryption"; return 0; }
  if ! rclone listremotes --long | grep -Eq "^${name}:[[:space:]]+crypt[[:space:]]*\$"; then
    log "ERROR: '$name' is not an rclone crypt remote: the backup would leave the server readable"
    return 1
  fi
}

umask 077
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
name="$BACKUP_PREFIX-$(date +%F-%H%M).dump"
file="$BACKUP_DIR/$name"
tmp="$file.partial"

log "dumping the database to $file"
# shellcheck disable=SC2016  # the variables are the ones of the container, not of this shell
compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > "$tmp"
[ -s "$tmp" ] || { log "ERROR: the dump is empty"; false; }
# A truncated custom dump cannot be listed: this catches a file that was cut short.
compose exec -T postgres pg_restore --list < "$tmp" > /dev/null
mv -- "$tmp" "$file"
tmp=""
chmod 600 -- "$file"
log "backup written: $(wc -c < "$file") bytes"

# Local retention: the newest BACKUP_KEEP_LOCAL files stay.
removed=0
# shellcheck disable=SC2012  # the names are the ones written above (prefix and date), never arbitrary
while IFS= read -r old; do
  rm -f -- "$old"
  removed=$((removed + 1))
done < <(ls -1t -- "$BACKUP_DIR/$BACKUP_PREFIX"-*.dump | tail -n +"$((BACKUP_KEEP_LOCAL + 1))")
log "local retention: kept the newest $BACKUP_KEEP_LOCAL, removed $removed"

if [ -n "$BACKUP_RCLONE_REMOTE" ]; then
  check_remote_is_encrypted
  log "copying to the remote"
  rclone copy -- "$file" "$BACKUP_RCLONE_REMOTE"
  rclone lsf --files-only -- "$BACKUP_RCLONE_REMOTE" | grep -Fxq -- "$name" \
    || { log "ERROR: $name is not on the remote after the copy"; false; }
  rclone delete --min-age "${BACKUP_REMOTE_KEEP_DAYS}d" --include "$BACKUP_PREFIX-*.dump" -- "$BACKUP_RCLONE_REMOTE"
  log "remote: $name is there; files older than $BACKUP_REMOTE_KEEP_DAYS days were removed"
else
  log "WARNING: BACKUP_RCLONE_REMOTE is empty, so the backup is only on this server and does not protect from losing it"
fi

ping_url "$BACKUP_PING_URL"
log "done"
