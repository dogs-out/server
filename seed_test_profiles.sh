#!/bin/bash
# Seed fake profiles for swipe testing
# Usage: bash seed_test_profiles.sh

BASE="http://localhost:8080"
DB="dogsout"
DB_USER="moritzdavinghausen"
PASSWORD="DogsOut123!"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; }

# Register → verify email in DB → login → return token
make_user() {
  local email="$1" name="$2" dob="$3"

  # Register
  curl -s -X POST "$BASE/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$PASSWORD\",\"name\":\"$name\"}" > /dev/null

  # Bypass email verification
  psql -U "$DB_USER" -d "$DB" -c \
    "UPDATE users SET email_verified=true, is_active=true WHERE email='$email';" -q

  # Login → token
  TOKEN=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$PASSWORD\"}" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

  echo "$TOKEN"
}

update_profile() {
  local token="$1" bio="$2" lat="$3" lon="$4" dob="$5" lifestyle="$6" personality="$7" rel="$8"
  curl -s -X PUT "$BASE/users/me" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "{
      \"bio\":\"$bio\",
      \"dateOfBirth\":\"$dob\",
      \"latitude\":$lat,
      \"longitude\":$lon,
      \"lifestyleTags\":[\"$lifestyle\"],
      \"personalityTags\":[\"$personality\"],
      \"relationshipStatus\":\"$rel\"
    }" > /dev/null
}

add_dog() {
  local token="$1" name="$2" breed="$3" dob="$4" bio="$5" energy="$6" social="$7" tags="$8"
  curl -s -X POST "$BASE/dogs" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "{
      \"name\":\"$name\",
      \"breed\":\"$breed\",
      \"dateOfBirth\":\"$dob\",
      \"bio\":\"$bio\",
      \"energyLevel\":$energy,
      \"socialBehavior\":\"$social\",
      \"tags\":[\"$tags\"]
    }" > /dev/null
}

echo ""
echo "🐾 Seeding test profiles..."
echo ""

# ── 1. Sarah ──────────────────────────────────────────────────────────────────
T=$(make_user "sarah.test@dogsout.dev" "Sarah Müller" "1995-03-12")
update_profile "$T" "Dog mum by day, coffee enthusiast by night. Luna and I are always up for a park meetup!" \
  47.3769 8.5417 "1995-03-12" "Early bird walks" "Dog mom/dad energy" "Single"
add_dog "$T" "Luna" "Golden Retriever" "2021-05-20" \
  "Obsessed with fetch and will bring the ball back approximately 400 times." 4 "Instant best friends" "Fetch obsessed"
ok "Sarah + Luna"

# ── 2. Max ────────────────────────────────────────────────────────────────────
T=$(make_user "max.test@dogsout.dev" "Max Bauer" "1992-07-28")
update_profile "$T" "Software dev who escapes the screen with long hikes. Milo keeps me sane." \
  47.3850 8.5320 "1992-07-28" "Hiking buddy wanted" "Treat negotiator" "Single"
add_dog "$T" "Milo" "Border Collie" "2022-02-14" \
  "Needs a job at all times. Currently employed as head of squirrel surveillance." 5 "Cautious but warms up" "Zoomies champion"
ok "Max + Milo"

# ── 3. Julia ──────────────────────────────────────────────────────────────────
T=$(make_user "julia.test@dogsout.dev" "Julia Schmid" "1997-11-05")
update_profile "$T" "Two dogs, twice the chaos, ten times the love. We live near Zürichsee." \
  47.3600 8.5500 "1997-11-05" "Weekend warrior" "Will talk about my dog for hours" "In a relationship"
add_dog "$T" "Bella" "Labrador Retriever" "2019-08-10" \
  "Senior lady who still thinks she's a puppy. Will steal your snacks given half a chance." 2 "Instant best friends" "Cuddler not a fighter"
add_dog "$T" "Rocky" "Siberian Husky" "2022-11-30" \
  "Dramatic. Loud. Absolutely convinced he's a wolf. Best boy." 5 "Prefers to observe first" "Velcro dog"
ok "Julia + Bella & Rocky"

# ── 4. Tom ────────────────────────────────────────────────────────────────────
T=$(make_user "tom.test@dogsout.dev" "Tom Keller" "1990-04-17")
update_profile "$T" "Zürich local, big fan of outdoor coffee terraces and dogs who pretend to be lap dogs." \
  47.3920 8.5180 "1990-04-17" "Café-with-dog person" "Responsible pup parent" "Married"
add_dog "$T" "Bruno" "French Bulldog" "2023-01-08" \
  "Snores like a chainsaw, snuggles like a pro. Size of a loaf of bread, personality of a lion." 2 "Instant best friends" "Couch potato"
ok "Tom + Bruno"

# ── 5. Anna ───────────────────────────────────────────────────────────────────
T=$(make_user "anna.test@dogsout.dev" "Anna Weber" "1998-09-22")
update_profile "$T" "Running every morning rain or shine — Coco sets the pace. Looking for park friends!" \
  47.3700 8.5600 "1998-09-22" "Early bird walks" "Professional ball-thrower" "Single"
add_dog "$T" "Coco" "Miniature Poodle" "2020-06-15" \
  "Smartest dog in any room. Knows 20 tricks and is extremely smug about it." 4 "Instant best friends" "Dog park regular"
ok "Anna + Coco"

# ── 6. Luca ───────────────────────────────────────────────────────────────────
T=$(make_user "luca.test@dogsout.dev" "Luca Rossi" "1993-12-03")
update_profile "$T" "Italian in Zürich. Thor and I explore a new trail every weekend." \
  47.4000 8.5450 "1993-12-03" "Hiking buddy wanted" "Dog mom/dad energy" "Single"
add_dog "$T" "Thor" "German Shepherd" "2018-03-25" \
  "Majestic. Loyal. Takes his job as protector of the household very seriously." 3 "Cautious but warms up" "Gentle giant"
ok "Luca + Thor"

# ── 7. Nina ───────────────────────────────────────────────────────────────────
T=$(make_user "nina.test@dogsout.dev" "Nina Fischer" "1996-06-14")
update_profile "$T" "Lakeside walks are our thing. Daisy and I are basically the same person." \
  47.3550 8.5350 "1996-06-14" "Beach walker" "Treat negotiator" "Single"
add_dog "$T" "Daisy" "Cavalier King Charles Spaniel" "2021-09-01" \
  "Perpetually happy. Has never had a bad day. Absolute sunshine in dog form." 3 "Instant best friends" "Shy but friendly"
ok "Nina + Daisy"

echo ""
echo "✅ Done! 7 profiles created. Login with any email above, password: $PASSWORD"
echo ""
