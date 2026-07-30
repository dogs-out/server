#!/bin/bash
# Seed dogsitting test profiles: sitters, seekers, and one account with both
# toggles on (for the Requests/Jobs switcher).
#
# Dry run (default, touches nothing):
#   bash seed_sitter_profiles.sh
# Apply:
#   TARGET=local bash seed_sitter_profiles.sh --apply
#   TARGET=prod  bash seed_sitter_profiles.sh --apply
# Remove everything it created:
#   TARGET=prod bash seed_sitter_profiles.sh --cleanup
#
# Every account uses an @dogsout.dev address so cleanup can match on the domain
# and never touch a real user.

set -uo pipefail

TARGET="${TARGET:-prod}"
MODE="${1:-dry}"
PASSWORD="DogsOut123!"
TEST_DOMAIN="dogsout.dev"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓ $1${NC}"; }
warn() { echo -e "${YELLOW}! $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; }

if [[ "$TARGET" == "prod" ]]; then
  BASE="https://api.dogsout.app"
  DB_URL="$(railway run --service Postgres -- printenv DATABASE_PUBLIC_URL 2>/dev/null | tr -d '\r\n')"
  [[ -z "$DB_URL" ]] && { fail "Could not read DATABASE_PUBLIC_URL from Railway"; exit 1; }
else
  BASE="http://localhost:8080"
  DB_URL="postgresql://moritzdavinghausen@localhost/dogsout"
fi

sql() { psql "$DB_URL" -tA -c "$1"; }

# ─── Profile data ─────────────────────────────────────────────────────────────
# email|name|dob|lat|lon|hasDog|isSitter|lookingForSitter|expYears|weekdays|sitterTags|bio
PROFILES=(
"sitter.lena@${TEST_DOMAIN}|Lena Frei|1994-02-11|47.3769|8.5417|true|true|false|5|Monday,Tuesday,Wednesday,Thursday,Friday|Certified sitter,Has a yard|Grew up with three dogs and have been sitting for neighbours for years. Happy to take yours on my afternoon walks."
"sitter.jonas@${TEST_DOMAIN}|Jonas Widmer|1999-08-03|47.3850|8.5320|false|true|false|3|Saturday,Sunday|Puppy experience,Overnight stays OK|No dog of my own yet (flat is too small) but I miss having one around. Free most weekends."
"sitter.mira@${TEST_DOMAIN}|Mira Sutter|1988-05-27|47.3600|8.5500|true|true|false|8|Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday|Dog trainer,Senior dog experience,Can give medication|Professional trainer. Comfortable with anxious dogs, seniors, and anything that needs meds on a schedule."
"sitter.david@${TEST_DOMAIN}|David Kunz|1996-11-19|47.3920|8.5180|true|true|false|1|Saturday,Sunday|Multiple dogs OK|New to sitting but grew up on a farm with a pack of them. Two at once is no problem."
"seeker.claudia@${TEST_DOMAIN}|Claudia Meier|1991-04-08|47.3700|8.5600|true|false|true|0||Travel a lot for work and need someone reliable for Emma every few weeks."
"seeker.pascal@${TEST_DOMAIN}|Pascal Roth|1993-09-30|47.4000|8.5450|true|false|true|0||Long shifts at the hospital. Looking for a midday walker for Nala."
"seeker.sofia@${TEST_DOMAIN}|Sofia Brunner|1997-01-22|47.3550|8.5350|true|false|true|0||Ziggy is a handful and I need backup for the occasional weekend away."
"seeker.marco@${TEST_DOMAIN}|Marco Iten|1990-07-14|47.3810|8.5290|true|false|true|0||Pepper hates being alone. Looking for someone nearby for short-notice days."
"both.elena@${TEST_DOMAIN}|Elena Vogt|1995-06-05|47.3745|8.5390|true|true|true|4|Wednesday,Thursday,Friday|Has a yard,Puppy experience|Happy to sit for others, and I need a sitter myself when I'm on the road. Both ways!"
)

# email|dogName|breed|dob|bio|energy|social|tag
DOGS=(
"sitter.lena@${TEST_DOMAIN}|Kira|Australian Shepherd|2020-03-14|Endless energy, herds everything including people.|5|Instant best friends|Zoomies champion"
"sitter.mira@${TEST_DOMAIN}|Otto|Beagle|2017-10-02|Nose first, brain second. Follows a scent anywhere.|3|Cautious but warms up|Dog park regular"
"sitter.david@${TEST_DOMAIN}|Rex|Boxer|2021-12-11|Thinks he is a lapdog. He is not a lapdog.|4|Instant best friends|Velcro dog"
"seeker.claudia@${TEST_DOMAIN}|Emma|Shiba Inu|2019-06-18|Independent, opinionated, and extremely photogenic.|3|Prefers to observe first|Shy but friendly"
"seeker.pascal@${TEST_DOMAIN}|Nala|Vizsla|2022-04-05|Needs two hours a day or she redecorates the flat.|5|Instant best friends|Fetch obsessed"
"seeker.sofia@${TEST_DOMAIN}|Ziggy|Jack Russell Terrier|2021-08-23|Small, loud, convinced he runs the building.|5|Cautious but warms up|Zoomies champion"
"seeker.marco@${TEST_DOMAIN}|Pepper|Cocker Spaniel|2018-02-09|Sweetest dog alive, cries if you leave the room.|2|Instant best friends|Cuddler not a fighter"
"both.elena@${TEST_DOMAIN}|Sammy|Labrador Retriever|2020-09-27|Will eat anything not nailed down. Otherwise perfect.|4|Instant best friends|Dog park regular"
)

# ─── Cleanup ──────────────────────────────────────────────────────────────────
if [[ "$MODE" == "--cleanup" ]]; then
  echo "Removing @${TEST_DOMAIN} accounts from ${TARGET} (${BASE})…"
  before=$(sql "SELECT count(*) FROM users WHERE email LIKE '%@${TEST_DOMAIN}';")
  sql "DELETE FROM users WHERE email LIKE '%@${TEST_DOMAIN}';" > /dev/null
  after=$(sql "SELECT count(*) FROM users WHERE email LIKE '%@${TEST_DOMAIN}';")
  ok "Deleted ${before} test accounts (${after} remaining)"
  exit 0
fi

# ─── Dry run ──────────────────────────────────────────────────────────────────
if [[ "$MODE" != "--apply" ]]; then
  echo ""
  echo "DRY RUN — nothing will be created. Target: ${TARGET} (${BASE})"
  echo ""
  for row in "${PROFILES[@]}"; do
    IFS='|' read -r email name _ _ _ hasDog isSitter seeking _ _ _ _ <<< "$row"
    roles=""
    [[ "$isSitter" == "true" ]] && roles="sitter"
    [[ "$seeking"  == "true" ]] && roles="${roles:+$roles + }seeker"
    printf "  %-34s %-16s %s%s\n" "$email" "$name" "$roles" \
      "$([[ "$hasDog" == "false" ]] && echo " (no dog)")"
  done
  echo ""
  echo "  ${#PROFILES[@]} accounts, ${#DOGS[@]} dogs, all @${TEST_DOMAIN}, password: ${PASSWORD}"
  echo "  Re-run with --apply to create them, --cleanup to remove them."
  echo ""
  exit 0
fi

# ─── Apply ────────────────────────────────────────────────────────────────────
echo ""
echo "Seeding ${#PROFILES[@]} dogsitting profiles into ${TARGET} (${BASE})…"
echo ""

# No associative array here on purpose: macOS ships bash 3.2, which has no
# `declare -A`. Dogs are created inside the same loop, while the token is in scope.
for row in "${PROFILES[@]}"; do
  IFS='|' read -r email name dob lat lon hasDog isSitter seeking exp weekdays tags bio <<< "$row"

  curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$PASSWORD\",\"name\":\"$name\"}" > /dev/null

  sql "UPDATE users SET email_verified=true, is_active=true WHERE email='$email';" > /dev/null

  token=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$PASSWORD\"}" \
    | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

  if [[ -z "$token" ]]; then fail "$email — login failed"; continue; fi

  wd_json=$(python3 -c "import sys,json;print(json.dumps([w for w in sys.argv[1].split(',') if w]))" "$weekdays")
  tg_json=$(python3 -c "import sys,json;print(json.dumps([t for t in sys.argv[1].split(',') if t]))" "$tags")

  curl -s -X PUT "$BASE/users/me" -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "{\"bio\":\"$bio\",\"dateOfBirth\":\"$dob\",\"latitude\":$lat,\"longitude\":$lon,
         \"hasDog\":$hasDog,\"isSitter\":$isSitter,\"lookingForSitter\":$seeking,
         \"sitterExperienceYears\":$exp,\"sitterWeekdays\":$wd_json,\"sitterTags\":$tg_json}" > /dev/null

  roles=""
  [[ "$isSitter" == "true" ]] && roles="sitter"
  [[ "$seeking"  == "true" ]] && roles="${roles:+$roles+}seeker"
  ok "$name ($roles)"

  for drow in "${DOGS[@]}"; do
    IFS='|' read -r demail dogName breed ddob dbio energy social tag <<< "$drow"
    [[ "$demail" != "$email" ]] && continue
    curl -s -X POST "$BASE/dogs" -H "Content-Type: application/json" \
      -H "Authorization: Bearer $token" \
      -d "{\"name\":\"$dogName\",\"breed\":\"$breed\",\"dateOfBirth\":\"$ddob\",\"bio\":\"$dbio\",
           \"energyLevel\":$energy,\"socialBehavior\":\"$social\",\"tags\":[\"$tag\"]}" > /dev/null
    ok "  └ $dogName"
  done
done

echo ""
sql "SELECT 'sitters: ' || count(*) FILTER (WHERE is_sitter) || ', seekers: ' || count(*) FILTER (WHERE looking_for_sitter) FROM users;"
echo "Done. Password for all test accounts: ${PASSWORD}"
echo "Remove them later with: TARGET=${TARGET} bash seed_sitter_profiles.sh --cleanup"
echo ""
