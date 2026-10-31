#!/usr/bin/env bash
# Routing test of the production Nginx (issue #348, RNF12, RNF13): starts the REAL template in front of two stand-ins, a
# fake "app" (the API) and a fake "web" (the front-end), and checks where each path goes, what the API receives, the
# limits of the login and of the public routes of the readers, and the security headers. Nothing of the application
# is needed, so it runs in the CI as it is. Usage: deploy/nginx/test-routing.sh [template]   (needs docker, curl, openssl)
set -euo pipefail

template="${1:-deploy/nginx/nginx.conf.template}"
case "$template" in /*) template_path="$template" ;; *) template_path="$PWD/$template" ;; esac
nginx_image="nginx:1.28-alpine"
id="$$"
network="nginx-routing-$id"
work="$(mktemp -d)"
failures=0

cleanup() {
  docker rm --force "edge-$id" "app-$id" "web-$id" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

check() {
  local description="$1"; shift
  if "$@" >/dev/null 2>&1; then
    echo "ok   - $description"
  else
    echo "FAIL - $description"
    failures=$((failures + 1))
  fi
}

# --- the stand-ins ------------------------------------------------------------------------------------------------
# The "API" answers what it received, and carries its OWN copies of two security headers, which the proxy must hide
# (the real API sends them too). The "front-end" answers its path, like the real image, which sends no security headers.
cat > "$work/app.conf" <<'CONF'
server {
    listen 8080;
    add_header Strict-Transport-Security "max-age=1" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    location / {
        default_type text/plain;
        return 200 "API|$http_host|$http_x_forwarded_proto|$http_x_forwarded_host|$http_x_forwarded_port|$uri\n";
    }
}
CONF
cat > "$work/web.conf" <<'CONF'
server {
    listen 8080;
    location / {
        default_type text/plain;
        add_header Cache-Control "no-cache";
        return 200 "WEB|$uri\n";
    }
}
CONF

# A throwaway certificate, in the layout the template reads (live/<DOMAIN>/fullchain.pem and privkey.pem).
mkdir -p "$work/certs/live/ci"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=ci \
  -keyout "$work/certs/live/ci/privkey.pem" -out "$work/certs/live/ci/fullchain.pem" 2>/dev/null

docker network create "$network" >/dev/null
docker run --detach --name "app-$id" --network "$network" --network-alias app \
  --volume "$work/app.conf:/etc/nginx/conf.d/default.conf:ro" "$nginx_image" >/dev/null
docker run --detach --name "web-$id" --network "$network" --network-alias web \
  --volume "$work/web.conf:/etc/nginx/conf.d/default.conf:ro" "$nginx_image" >/dev/null
docker run --detach --name "edge-$id" --network "$network" --publish 127.0.0.1::443 --publish 127.0.0.1::80 \
  --env DOMAIN=ci \
  --volume "$template_path:/etc/nginx/templates/default.conf.template:ro" \
  --volume "$work/certs:/etc/letsencrypt:ro" "$nginx_image" >/dev/null

https_port="$(docker port "edge-$id" 443/tcp | head -n 1 | sed 's/.*://')"
http_port="$(docker port "edge-$id" 80/tcp | head -n 1 | sed 's/.*://')"

# --- helpers: every request goes through the edge, with the Host of the domain --------------------------------
edge() { curl --silent --insecure --header 'Host: ci' "$@"; }
body() { edge --path-as-is "https://127.0.0.1:$https_port$1"; }
code() { edge --path-as-is --output /dev/null --write-out '%{http_code}' "$@"; }

for _ in $(seq 1 30); do
  [ "$(code "https://127.0.0.1:$https_port/" 2>/dev/null || true)" != "000" ] && break
  sleep 1
done
if ! docker ps --filter "name=edge-$id" --filter status=running --quiet | grep -q .; then
  echo "FAIL - the Nginx did not start with this template"
  docker logs "edge-$id" 2>&1 | tail -n 5
  exit 1
fi

# --- where each path goes -------------------------------------------------------------------------------------
for path in / /livros /usuarios '/convite?token=abc' '/redefinir-senha?token=abc' /meu/dados /apix /assets/index-abc.js /favicon.svg; do
  check "$path goes to the front-end" bash -c "curl -sk -H 'Host: ci' --path-as-is 'https://127.0.0.1:$https_port$path' | grep -q '^WEB|'"
done
for path in /api /api/v1/users/me /api/v1/auth/login /actuator/health /actuator/health/liveness /swagger-ui.html \
            /swagger-ui/index.html /v3/api-docs /v3/api-docs/swagger-config; do
  check "$path goes to the API" bash -c "curl -sk -H 'Host: ci' --path-as-is 'https://127.0.0.1:$https_port$path' | grep -q '^API|'"
done

# Nginx decides on the normalized path, so these cannot be used to get around the routing (nor the login limit below).
check "//api/... (a double slash) still goes to the API" bash -c "curl -sk -H 'Host: ci' --path-as-is 'https://127.0.0.1:$https_port//api/v1/auth/login' | grep -q '^API|'"
check "/%61pi/... (an encoded letter) still goes to the API" bash -c "curl -sk -H 'Host: ci' --path-as-is 'https://127.0.0.1:$https_port/%61pi/v1/users/me' | grep -q '^API|'"

# --- what the API receives: the Host and the forwarded headers must say the PUBLIC address ------------------------
# A browser sends an Origin; the API answers 403 "Invalid CORS request" when these do not match it (found while trying
# the front-end image behind a proxy that dropped the port: curl without Origin passed, the browser did not).
received="$(body /api/v1/users/me)"
check "the API receives Host=ci"                 test "$(echo "$received" | cut -d'|' -f2)" = "ci"
check "the API receives X-Forwarded-Proto=https" test "$(echo "$received" | cut -d'|' -f3)" = "https"
check "the API receives X-Forwarded-Host=ci"     test "$(echo "$received" | cut -d'|' -f4)" = "ci"
check "the API receives X-Forwarded-Port=443"    test "$(echo "$received" | cut -d'|' -f5)" = "443"

# --- security headers: one of each, the proxy's, on the API and on the front-end ------------------------------------
count_header() { edge --head --path-as-is "https://127.0.0.1:$https_port$2" | tr -d '\r' | grep -ic "^$1:" || true; }
for path in /api/v1/users/me /livros; do
  check "$path has one Strict-Transport-Security, the proxy's" test "$(count_header strict-transport-security "$path")" = "1"
  check "$path has one X-Frame-Options" test "$(count_header x-frame-options "$path")" = "1"
  check "$path has one X-Content-Type-Options" test "$(count_header x-content-type-options "$path")" = "1"
done
check "the proxy's X-Frame-Options (DENY) wins over the API's" bash -c "curl -skI -H 'Host: ci' 'https://127.0.0.1:$https_port/api/v1/users/me' | tr -d '\r' | grep -iq '^x-frame-options: DENY'"
check "the cache header of the front-end passes through" bash -c "curl -skI -H 'Host: ci' 'https://127.0.0.1:$https_port/livros' | tr -d '\r' | grep -iq '^cache-control: no-cache'"

# --- port 80 only redirects --------------------------------------------------------------------------------------
check "port 80 redirects to https (301)" test "$(curl -s -o /dev/null -w '%{http_code}' -H 'Host: ci' "http://127.0.0.1:$http_port/livros")" = "301"

# --- the limits: login and public routes of the readers (5 per minute, burst 5), and only there ---------------------
login_url="https://127.0.0.1:$https_port/api/v1/auth/login"
codes=""
for _ in $(seq 1 12); do codes="$codes $(code -X POST "$login_url")"; done
check "the first login attempts pass" test "$(echo "$codes" | awk '{print $1}')" = "200"
check "too many login attempts get 429" bash -c "echo '$codes' | grep -q 429"
check "the 429 is a ProblemDetail with Retry-After" bash -c "curl -skI -X POST -H 'Host: ci' '$login_url' | tr -d '\r' | grep -iq '^content-type: application/problem+json' && curl -skI -X POST -H 'Host: ci' '$login_url' | tr -d '\r' | grep -iq '^retry-after:'"
check "the 429 body says Too Many Requests" bash -c "curl -sk -X POST -H 'Host: ci' '$login_url' | grep -q 'Too Many Requests'"
check "the limit also counts the //api/... way" test "$(code -X POST "https://127.0.0.1:$https_port//api/v1/auth/login")" = "429"
check "the limit also counts the /%61pi/... way" test "$(code -X POST "https://127.0.0.1:$https_port/%61pi/v1/auth/login")" = "429"

reader_codes=""
for _ in $(seq 1 12); do reader_codes="$reader_codes $(code -X POST "https://127.0.0.1:$https_port/api/v1/auth/forgot-password")"; done
check "too many forgot-password requests get 429" bash -c "echo '$reader_codes' | grep -q 429"

# The front-end and the other API routes are never limited.
front_codes=""
for _ in $(seq 1 30); do front_codes="$front_codes $(code "https://127.0.0.1:$https_port/livros")"; done
check "the front-end is never limited" bash -c "! echo '$front_codes' | grep -q 429"
check "a POST to the front-end is never limited" bash -c "for _ in \$(seq 1 12); do test \"\$(curl -sk -o /dev/null -w '%{http_code}' -X POST -H 'Host: ci' 'https://127.0.0.1:$https_port/livros')\" != 429 || exit 1; done"
check "the other API routes are never limited" bash -c "for _ in \$(seq 1 12); do test \"\$(curl -sk -o /dev/null -w '%{http_code}' -H 'Host: ci' 'https://127.0.0.1:$https_port/api/v1/users/me')\" != 429 || exit 1; done"

# --- an update recreates a container, and it may get ANOTHER address: the proxy must follow it ----------------------------
# A name that Nginx resolves only once, at start, keeps the old address: the front-end (or the API) would answer 504
# until the Nginx restarted, in the middle of an update that "only changes what changed". The container is moved to a
# new address here, the way a recreation can. (A /16 network, as the Docker default; otherwise this is skipped.)
subnet="$(docker network inspect --format '{{(index .IPAM.Config 0).Subnet}}' "$network")"
if [[ "$subnet" == */16 ]]; then
  new_prefix="$(echo "${subnet%/*}" | awk -F. '{print $1"."$2}')"
  moved=0
  for who in web app; do
    moved=$((moved + 1))
    docker network disconnect "$network" "$who-$id"
    docker network connect --ip "$new_prefix.77.$moved" --alias "$who" "$network" "$who-$id"
  done
  follow() {  # $1 = path, $2 = what the stand-in answers
    for _ in $(seq 1 8); do
      curl --silent --insecure --max-time 8 --header 'Host: ci' "https://127.0.0.1:$https_port$1" | grep -q "^$2|" && return 0
      sleep 1
    done
    return 1
  }
  check "the proxy follows the front-end to a new address" follow /livros WEB
  check "the proxy follows the API to a new address" follow /api/v1/users/me API
else
  echo "skip - the follow-the-new-address check needs a /16 network (this one is $subnet)"
fi

if [ "$failures" -ne 0 ]; then
  echo "$failures check(s) failed"
  exit 1
fi
echo "all checks passed"
