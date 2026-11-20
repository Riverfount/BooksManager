#!/usr/bin/env bash
# Seeds demo data: 7 readers (one is a minor), 10 books with 11 copies, 4 loans (two of them overdue), one late
# return that leaves a pending fine and a suspension, and one blocked reader. Optionally an operator too.
# The admin comes from APP_ADMIN_* at startup; this script only logs in as admin.
#
# Demo only: never run it against production. It refuses to run twice (it stops if the catalog is not empty).
#
# Everything goes through the API except the overdue dates: the API only lends from today, so the script moves
# the dates of two loans back straight in the DISPOSABLE Testcontainers database (docker exec + psql), and then
# returns one of them late through the API. For that it needs Docker and exactly one Testcontainers PostgreSQL
# running (or PG_CONTAINER=<name or id>); it never touches the compose.yaml database.
#
# Usage:
#   ADMIN_PASSWORD=... scripts/demo-seed.sh
# Optional: API_URL (default http://localhost:8080), ADMIN_LOGIN (default admin), PG_CONTAINER,
#   OPERATOR_PASSWORD (creates the operator OPERATOR_LOGIN, default "operador"; leave it out when the API already
#   started with APP_OPERATOR_*).
set -euo pipefail

API=${API_URL:-http://localhost:8080}/api/v1
ADMIN_LOGIN=${ADMIN_LOGIN:-admin}
OPERATOR_LOGIN=${OPERATOR_LOGIN:-operador}
: "${ADMIN_PASSWORD:?set ADMIN_PASSWORD (the APP_ADMIN_PASSWORD the API started with)}"
JSON='Content-Type: application/json'

py() { python3 -I -c "$@"; }
field() { py "import sys,json;print(json.load(sys.stdin)$1)"; }

login_body=$(py "import json,sys;print(json.dumps({'login':sys.argv[1],'password':sys.argv[2]}))" "$ADMIN_LOGIN" "$ADMIN_PASSWORD")
TOKEN=$(curl -fsS -X POST "$API/auth/login" -H "$JSON" -d "$login_body" | field "['accessToken']")
AUTH="Authorization: Bearer $TOKEN"

post() { curl -fsS -X POST "$API/$1" -H "$JSON" -H "$AUTH" -d "$2"; }
id_of() { field "['id']"; }

total=$(curl -fsS "$API/books?size=1" -H "$AUTH" | field "['totalItems']")
if [ "$total" != "0" ]; then
  echo "Catalog already has books ($total): refusing to seed twice." >&2
  exit 1
fi

# ISBN-13 from 12 digits, check digit computed.
isbn() { py "import sys;b=sys.argv[1];s=sum(int(c)*(1 if i%2==0 else 3) for i,c in enumerate(b));print(b+str((10-s%10)%10))" "$1"; }
# Valid CPF from 9 digits, check digits computed, formatted.
cpf() {
  py "
import sys
d=[int(c) for c in sys.argv[1]]
for n in (9,10):
    s=sum(v*w for v,w in zip(d,range(n+1,1,-1)))
    d.append((s*10%11)%10)
t=''.join(map(str,d));print(f'{t[:3]}.{t[3:6]}.{t[6:9]}-{t[9:]}')" "$1"
}

# The overdue step edits the database, so find (and check) its container before writing anything.
PG=${PG_CONTAINER:-}
if [ -z "$PG" ]; then
  mapfile -t found < <(docker ps --filter ancestor=postgres:17 --filter label=org.testcontainers=true --format '{{.ID}}')
  if [ "${#found[@]}" -ne 1 ]; then
    echo "Expected exactly one Testcontainers PostgreSQL, found ${#found[@]}: set PG_CONTAINER=<name or id>." >&2
    exit 1
  fi
  PG=${found[0]}
fi

if [ -n "${OPERATOR_PASSWORD:-}" ]; then
  echo "operator..."
  post users "$(py "import json,sys;print(json.dumps({'login':sys.argv[1],'password':sys.argv[2],'role':'OPERATOR','displayName':'Operador Demo'}))" "$OPERATOR_LOGIN" "$OPERATOR_PASSWORD")" >/dev/null
fi

echo "categories and authors..."
cat_id() { post categories "{\"name\":\"$1\"}" | id_of; }
author_id() { post authors "{\"name\":\"$1\"}" | id_of; }
C_ROMANCE=$(cat_id "Romance"); C_SCIFI=$(cat_id "Ficção Científica"); C_POESIA=$(cat_id "Poesia")
C_FANTASIA=$(cat_id "Fantasia"); C_HISTORIA=$(cat_id "História")
A_MACHADO=$(author_id "Machado de Assis"); A_CLARICE=$(author_id "Clarice Lispector")
A_ASIMOV=$(author_id "Isaac Asimov"); A_DRUMMOND=$(author_id "Carlos Drummond de Andrade")
A_TOLKIEN=$(author_id "J.R.R. Tolkien"); A_ORWELL=$(author_id "George Orwell")
A_JORGE=$(author_id "Jorge Amado"); A_HOLANDA=$(author_id "Sérgio Buarque de Holanda")

echo "books and copies..."
book() { # prefix12 title publisher year category author
  post books "$(py "
import json,sys
print(json.dumps({'isbn':sys.argv[1],'title':sys.argv[2],'publisher':sys.argv[3],'publicationYear':int(sys.argv[4]),
                  'categoryId':int(sys.argv[5]),'authorIds':[int(sys.argv[6])]}))" \
    "$(isbn "$1")" "$2" "$3" "$4" "$5" "$6")" | id_of
}
copy() { post copies "{\"bookId\":$1,\"assetCode\":\"$2\"}" | id_of; }

B1=$(book 978853330227 "Dom Casmurro" "Garnier" 1899 "$C_ROMANCE" "$A_MACHADO")
B2=$(book 978853330228 "Memórias Póstumas de Brás Cubas" "Garnier" 1881 "$C_ROMANCE" "$A_MACHADO")
B3=$(book 978853591484 "A Hora da Estrela" "Rocco" 1977 "$C_ROMANCE" "$A_CLARICE")
B4=$(book 978856560757 "Eu, Robô" "Aleph" 1950 "$C_SCIFI" "$A_ASIMOV")
B5=$(book 978856560758 "Fundação" "Aleph" 1951 "$C_SCIFI" "$A_ASIMOV")
B6=$(book 978852540001 "A Rosa do Povo" "Companhia das Letras" 1945 "$C_POESIA" "$A_DRUMMOND")
B7=$(book 978854510001 "O Senhor dos Anéis" "Martins Fontes" 1954 "$C_FANTASIA" "$A_TOLKIEN")
B8=$(book 978853590001 "1984" "Companhia das Letras" 1949 "$C_SCIFI" "$A_ORWELL")
B9=$(book 978853590002 "Capitães da Areia" "Companhia das Letras" 1937 "$C_ROMANCE" "$A_JORGE")
B10=$(book 978852590001 "Raízes do Brasil" "Companhia das Letras" 1936 "$C_HISTORIA" "$A_HOLANDA")

n=0
declare -A FIRST_COPY
for b in "$B1" "$B2" "$B3" "$B4" "$B5" "$B6" "$B7" "$B8" "$B9" "$B10"; do
  # two copies of the first book (so one stays available when the other is lent), one of each other
  copies=1; [ "$b" = "$B1" ] && copies=2
  for _ in $(seq "$copies"); do
    n=$((n + 1))
    cid=$(copy "$b" "$(printf 'DEMO-%03d' "$n")")
    [ -n "${FIRST_COPY[$b]:-}" ] || FIRST_COPY[$b]=$cid
  done
done

echo "readers..."
reader() { # name cpf9 email birthDate [guardianName guardianEmail]
  post readers "$(py "
import json,sys
a=sys.argv
body={'name':a[1],'document':a[2],'email':a[3],'birthDate':a[4]}
if len(a)>5: body['guardian']={'name':a[5],'email':a[6]}
print(json.dumps(body))" "$1" "$(cpf "$2")" "$3" "$4" ${5:+"$5" "$6"})" | id_of
}
R1=$(reader "Ana Souza" 123456789 ana.souza@example.com 1990-05-10)
R2=$(reader "Bia Lima" 234567891 bia.lima@example.com 1991-03-22)
R3=$(reader "Carlos Pereira" 345678912 carlos.pereira@example.com 1988-11-02)
R4=$(reader "Daniela Rocha" 456789123 daniela.rocha@example.com 1995-07-30)
reader "Eduardo Alves" 567891234 eduardo.alves@example.com 1979-01-15 >/dev/null
reader "Fernanda Costa" 678912345 fernanda.costa@example.com 2001-09-09 >/dev/null
reader "Gabriel Nunes" 789123456 gabriel.nunes@example.com 2011-04-18 "Helena Nunes" helena.nunes@example.com >/dev/null

echo "loans, overdue dates, late return and one block..."
lend() { post loans "{\"copyId\":${FIRST_COPY[$1]},\"readerId\":$2}" | id_of; }
L1=$(lend "$B1" "$R1")   # Ana: Dom Casmurro, will be overdue
L2=$(lend "$B4" "$R2")   # Bia: Eu, Robô, returned late (fine + suspension)
L3=$(lend "$B7" "$R3")   # Carlos: O Senhor dos Anéis, overdue, and the reader is blocked
lend "$B8" "$R4" >/dev/null   # Daniela: 1984, on time (nothing else uses this loan)

sql() { docker exec "$PG" psql -U test -d test -tA -c "$1"; }
# Safety: the container must hold the same loan the API just created, or it is another database.
api_due=$(curl -fsS "$API/loans/$L1" -H "$AUTH" | field "['dueDate']")
db_due=$(sql "select due_date from loan where id = $L1")
if [ "$api_due" != "$db_due" ]; then
  echo "Container $PG does not hold this API's database (loan $L1 due $api_due vs '$db_due'): set PG_CONTAINER." >&2
  exit 1
fi

curl -fsS -o /dev/null -X PATCH "$API/library-configuration" -H "$JSON" -H "$AUTH" -d '{"penaltyPolicy":"BOTH"}'
sql "update loan set pickup_date = current_date - 24, due_date = current_date - 10 where id = $L1" >/dev/null
sql "update loan set pickup_date = current_date - 19, due_date = current_date - 5 where id = $L3" >/dev/null
sql "update loan set pickup_date = current_date - 30, due_date = current_date - 16 where id = $L2" >/dev/null
curl -fsS -o /dev/null -X POST "$API/loans/$L2/return" -H "$JSON" -H "$AUTH"
curl -fsS -o /dev/null -X POST "$API/readers/$R3/block" -H "$AUTH"

echo "seed ok: 7 readers, 10 books, 11 copies; active loans: Ana (overdue), Carlos (overdue, blocked), Daniela;"
echo "Bia returned late: pending fine and suspension (policy BOTH)."
