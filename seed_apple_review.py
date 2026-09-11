#!/usr/bin/env python3
"""
Populate the App Review demo account's surroundings, at Apple Park.

App Review rejected build 12 under guideline 2.1(a) because the demo account
showed empty Discover / Dogsitting / Chats / Playdates tabs. Reviewers test from
Cupertino, so every profile in the (Swiss) production data sits ~9000 km away and
is filtered out by the 50 km distance cap.

This creates a small ring of demo profiles around Apple Park (37.3349, -122.0090),
moves the demo account there, and pre-populates every tab the reviewer named:

  Discover    6+ owners with dogs, all inside ~12 km, none swiped yet.
              Each has already liked the demo account, so a right swipe on any
              of them produces the match overlay immediately.
  Dogsitting  3 sitters and 2 owners looking for a sitter; the demo account gets
              both dogsitting roles switched on so both lists are browsable.
  Chats       3 conversations with messages already in them (2 from matches,
              1 opened from the Dogsitting tab) + 1 playdate group chat.
  Playdates   2 public playdates at real Bay Area parks, one already joined.

Every seeded account uses an @dogsout.dev address, an illustrated (not
photographic) avatar, and an obviously fictional name — they are demo data, not
impersonations of real people.

Usage
    python3 seed_apple_review.py                 # dry run — prints the plan, writes nothing
    python3 seed_apple_review.py --apply         # create/refresh everything
    python3 seed_apple_review.py --verify        # read-only: what each tab shows the reviewer
    python3 seed_apple_review.py --cleanup       # delete the seeded @dogsout.dev accounts

    TARGET=local python3 seed_apple_review.py --apply     # against localhost:8080

Notes
  * Re-running --apply is safe: accounts, dogs, photos, messages and playdates are
    all created only if missing. Seeded playdates whose date has passed are moved
    forward, so run it again if review drags on.
  * prod needs `railway` + `psql` on PATH: registration sends a verification email
    and login refuses unverified accounts, so the script flips email_verified
    directly, exactly like seed_sitter_profiles.sh does.
  * The demo account itself is never deleted by --cleanup.
"""

import argparse
import io
import json
import math
import os
import subprocess
import sys
from datetime import datetime, timedelta, timezone

try:
    import requests
except ImportError:  # pragma: no cover
    sys.exit("pip install requests")

from PIL import Image, ImageDraw, ImageFont

# ─── Configuration ────────────────────────────────────────────────────────────

TARGET = os.environ.get("TARGET", "prod")
BASE = "https://api.dogsout.app" if TARGET == "prod" else "http://localhost:8080"
LOCAL_DB = "postgresql:///dogsout"  # unix socket, peer auth

PASSWORD = "DogsOut123!"
TEST_DOMAIN = "dogsout.dev"

# The demo accounts named in App Store Connect → App Review Information. Both are
# seeded when both exist, so the reviewer sees the same thing either way.
DEMO_EMAILS = ["demoaccount@dogsout.app", "applereview@dogsout.app"]
DEMO_PASSWORD = "DogsOut123!"

APPLE_PARK = (37.3349, -122.0090)

BRAND = (46, 158, 107)      # #2E9E6B
BRAND_LIGHT = (238, 251, 243)

GREEN = "\033[0;32m"; RED = "\033[0;31m"; YELLOW = "\033[0;33m"; DIM = "\033[2m"; NC = "\033[0m"


def ok(msg):   print(f"{GREEN}✓{NC} {msg}")
def warn(msg): print(f"{YELLOW}!{NC} {msg}")
def fail(msg): print(f"{RED}✗{NC} {msg}")
def dim(msg):  print(f"{DIM}{msg}{NC}")


# ─── Demo profiles ────────────────────────────────────────────────────────────
# role: discover | chat | sitter | seeker  (all of them also show up in Discover
# when they own a dog — the roles only say what the script wires up afterwards)

PROFILES = [
    # ─ Discover: liked the demo account already, so a right swipe matches instantly
    dict(key="maya", role="discover", name="Maya Alvarez", dob="1993-05-14",
         loc=(37.3300, -122.0140), bio="Product designer, weekend hiker. Biscuit and I do the Rancho loop most Saturdays and are always up for company.",
         lifestyle=["Early bird walks", "Hiking buddy wanted"], personality=["Dog mom/dad energy"],
         dog=dict(name="Biscuit", breed="Golden Retriever", dob="2021-03-02",
                  bio="Believes every human he meets is there specifically to see him.",
                  energy=4, social="Instant best friends", loves=["Fetch / ball games", "Swimming"],
                  off_leash="Yes, fully reliable", kids=5, tags=["Fetch obsessed", "Dog park regular"])),
    dict(key="chris", role="discover", name="Chris Nakamura", dob="1990-11-02",
         loc=(37.3400, -122.0000), bio="Work in Cupertino, walk in Cupertino. Momo is small, opinionated, and runs the household.",
         lifestyle=["Café-with-dog person"], personality=["Treat negotiator"],
         dog=dict(name="Momo", breed="Shiba Inu", dob="2020-07-19",
                  bio="Independent contractor. Will consider your request to pet her.",
                  energy=3, social="Prefers to observe first", loves=["Sniffing everything", "Treats"],
                  off_leash="Only in fenced areas", kids=3, tags=["Independent", "One dog at a time"])),
    dict(key="priya", role="discover", name="Priya Raman", dob="1995-02-27",
         loc=(37.3541, -121.9552), bio="Santa Clara. Laddu has a nose that overrides his brain, so we walk a lot and get nowhere.",
         lifestyle=["Night owl walks"], personality=["Will talk about my dog for hours"],
         dog=dict(name="Laddu", breed="Beagle", dob="2019-09-05",
                  bio="Follows a scent until the leash runs out. Excellent at looking innocent.",
                  energy=3, social="Cautious but warms up", loves=["Sniffing everything", "Napping"],
                  off_leash="No, always on leash", kids=4, tags=["Sniff-first explorer", "Goofy"])),
    dict(key="diego", role="discover", name="Diego Ferreira", dob="1988-08-08",
         loc=(37.3861, -122.0839), bio="Mountain View. Nico needs a job or he invents one, usually herding the neighbours.",
         lifestyle=["Weekend warrior", "Early bird walks"], personality=["Professional ball-thrower"],
         dog=dict(name="Nico", breed="Border Collie", dob="2022-01-30",
                  bio="Two hours of exercise a day or the sofa becomes a project.",
                  energy=5, social="Instant best friends", loves=["Fetch / ball games", "Running"],
                  off_leash="Yes, fully reliable", kids=4, tags=["Zoomies champion", "Chaser"])),
    dict(key="hannah", role="discover", name="Hannah Fischer", dob="1996-06-21",
         loc=(37.3852, -122.1141), bio="Los Altos, originally from Bern. Bruno is 45 kg of pure sentiment.",
         lifestyle=["Beach walker"], personality=["Responsible pup parent"],
         dog=dict(name="Bruno", breed="Bernese Mountain Dog", dob="2018-04-11",
                  bio="Moves at his own pace. That pace is slow, and it is not negotiable.",
                  energy=2, social="Instant best friends", loves=["Cuddles", "Napping"],
                  off_leash="Working on it", kids=5, tags=["Gentle giant", "Couch potato"])),
    dict(key="tom", role="discover", name="Tom Whitaker", dob="1992-12-09",
         loc=(37.2872, -121.9500), bio="Campbell. Scout is a rescue and has come a long way — slow introductions, please.",
         lifestyle=["Weekend warrior"], personality=["Dog mom/dad energy"],
         dog=dict(name="Scout", breed="Australian Shepherd", dob="2021-10-17",
                  bio="Nervous at first, then your best friend for life.",
                  energy=4, social="Cautious but warms up", loves=["Running", "Treats"],
                  off_leash="Only in fenced areas", kids=3, tags=["Shy", "Wrestler"])),

    # ─ Chats: matched with the demo account, with a conversation already in place
    dict(key="sarah", role="chat", name="Sarah Kim", dob="1994-03-18",
         loc=(37.3235, -122.0450), bio="Cupertino. Yuki is a corgi, which means she is mostly ears and opinions.",
         lifestyle=["Early bird walks", "Café-with-dog person"], personality=["Treat negotiator"],
         dog=dict(name="Yuki", breed="Pembroke Welsh Corgi", dob="2021-06-08",
                  bio="Short legs, long stride, absolutely no concept of personal space.",
                  energy=4, social="Instant best friends", loves=["Treats", "Other dogs"],
                  off_leash="Only in fenced areas", kids=5, tags=["Goofy", "Life of the party"])),
    dict(key="leo", role="chat", name="Leo Moretti", dob="1991-07-25",
         loc=(37.3688, -122.0363), bio="Sunnyvale. Pippa and I are looking for a regular morning walking group.",
         lifestyle=["Early bird walks"], personality=["Will talk about my dog for hours"],
         dog=dict(name="Pippa", breed="Standard Poodle", dob="2019-11-23",
                  bio="Smarter than me before coffee. Learns tricks faster than I can invent them.",
                  energy=3, social="Instant best friends", loves=["Swimming", "Fetch / ball games"],
                  off_leash="Yes, fully reliable", kids=4, tags=["Loves to swim", "Dog park regular"])),

    # ─ Dogsitting: sitters
    dict(key="grace", role="sitter", name="Grace Okafor", dob="1989-10-30",
         loc=(37.3320, -122.0980), bio="Sitting for neighbours around Cupertino for six years. Fenced yard, flexible on weekdays.",
         sitter=True, sitter_years=6, weekdays=["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"],
         sitter_tags=["Certified sitter", "Has a yard"],
         lifestyle=["Early bird walks"], personality=["Responsible pup parent"],
         dog=dict(name="Kofi", breed="Labrador Retriever", dob="2020-02-14",
                  bio="Host dog. Greets every guest dog like a long-lost relative.",
                  energy=4, social="Instant best friends", loves=["Swimming", "Other dogs"],
                  off_leash="Yes, fully reliable", kids=5, tags=["Life of the party", "Good with puppies"])),
    dict(key="ethan", role="sitter", name="Ethan Brooks", dob="1998-01-12",
         loc=(37.3719, -122.0328), bio="No dog of my own yet — the flat is too small — so I sit for other people's on weekends. Overnights are fine.",
         sitter=True, has_dog=False, sitter_years=3, weekdays=["Saturday", "Sunday"],
         sitter_tags=["Puppy experience", "Overnight stays OK"],
         lifestyle=["Night owl walks"], personality=["Treat negotiator"]),
    dict(key="nina", role="sitter", name="Nina Patel", dob="1986-04-03",
         loc=(37.3120, -122.0300), bio="Professional trainer. Comfortable with anxious dogs, seniors, and anything on a medication schedule.",
         sitter=True, sitter_years=8,
         weekdays=["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"],
         sitter_tags=["Dog trainer", "Senior dog experience", "Can give medication"],
         lifestyle=["Early bird walks"], personality=["Responsible pup parent"],
         dog=dict(name="Ollie", breed="Beagle", dob="2016-05-21",
                  bio="Twelve going on three. Retired from zoomies, still fully committed to snacks.",
                  energy=2, social="Cautious but warms up", loves=["Napping", "Belly rubs"],
                  off_leash="No, always on leash", kids=4, tags=["Couch potato", "Good with cats"])),

    # ─ Dogsitting: owners looking for a sitter
    dict(key="marcus", role="seeker", name="Marcus Lee", dob="1993-09-16",
         loc=(37.3450, -122.0250), bio="Long shifts at the hospital. Looking for someone reliable for a midday walk with Rosie.",
         seeker=True, lifestyle=["Weekend warrior"], personality=["Dog mom/dad energy"],
         dog=dict(name="Rosie", breed="Vizsla", dob="2022-04-05",
                  bio="Needs two hours a day or she redecorates the flat.",
                  energy=5, social="Instant best friends", loves=["Running", "Fetch / ball games"],
                  off_leash="Working on it", kids=4, tags=["Zoomies champion", "Velcro dog"])),
    dict(key="ana", role="seeker", name="Ana Sousa", dob="1997-05-08",
         loc=(37.3600, -122.0500), bio="Travelling for work every few weeks and need backup for Tofu while I'm away.",
         seeker=True, lifestyle=["Café-with-dog person"], personality=["Will talk about my dog for hours"],
         dog=dict(name="Tofu", breed="Jack Russell Terrier", dob="2021-08-23",
                  bio="Small, loud, convinced he runs the building.",
                  energy=5, social="Cautious but warms up", loves=["Fetch / ball games", "Treats"],
                  off_leash="Only in fenced areas", kids=3, tags=["Drama queen", "Chaser"])),
]

# Conversations already waiting in Chats. "them" = the seeded account, "me" = demo.
CONVERSATIONS = {
    "sarah": [
        ("them", "Hi! Yuki spotted your dog at Memorial Park yesterday and lost her mind 😄"),
        ("me", "Ha! She's very hard to miss. We're there most mornings around 8."),
        ("them", "Same, we do the loop by the playground. Want to walk together Saturday?"),
        ("me", "Saturday works. 9am by the main entrance?"),
        ("them", "Perfect, see you there 🐾"),
    ],
    "leo": [
        ("them", "Hey! Fellow early riser here — Pippa and I walk the Stevens Creek trail before work."),
        ("me", "That's a great trail. How early is early?"),
        ("them", "Out the door by 6:45. Pippa doesn't accept excuses."),
        ("me", "Respect. Count us in one of these mornings."),
    ],
    "grace": [
        ("them", "Hi! I saw you're looking for a sitter — I'm just up the road in Cupertino with a fenced yard."),
        ("me", "That's great, thanks for reaching out. I mostly need weekday afternoons."),
        ("them", "Weekdays are exactly when I'm free. Want to meet at the park first so the dogs can say hello?"),
        ("me", "Yes please — that's how it should work. Thursday afternoon?"),
        ("them", "Thursday it is. See you both then!"),
    ],
}

PLAYDATES = [
    dict(key="pd-memorial", host="maya", title="Morning zoomies at Memorial Park",
         description="Casual off-leash meetup by the dog run. All sizes welcome, we usually stay about an hour and get coffee after.",
         park="Cupertino Memorial Park", address="21121 Stevens Creek Blvd, Cupertino, CA",
         loc=(37.3235, -122.0450), days_out=6, hour=9, minute=30, max_participants=8,
         joiners=["chris", "sarah"], demo_joins=True,
         chat=[("maya", "Weather looks perfect for Saturday ☀️"),
               ("chris", "Momo is in. She'll judge everyone from the bench for ten minutes first, as usual."),
               ("sarah", "Yuki and I will be there. Bringing the good treats."),
               ("maya", "See you all by the dog run at 9:30 🐾")]),
    dict(key="pd-rancho", host="diego", title="Sunset walk at Rancho San Antonio",
         description="Easy 5 km loop at an unhurried pace, leashes on. Good for dogs that prefer smaller groups.",
         park="Rancho San Antonio Preserve", address="22500 Cristo Rey Dr, Cupertino, CA",
         loc=(37.3320, -122.0980), days_out=13, hour=17, minute=30, max_participants=6,
         joiners=["hannah"], demo_joins=False, chat=[]),
]


# ─── Small HTTP helper ────────────────────────────────────────────────────────

class Api:
    def __init__(self, base):
        self.base = base
        self.s = requests.Session()

    def _req(self, method, path, token=None, **kw):
        headers = kw.pop("headers", {})
        if token:
            headers["Authorization"] = f"Bearer {token}"
        return self.s.request(method, self.base + path, headers=headers, timeout=60, **kw)

    def get(self, path, token=None):
        return self._req("GET", path, token)

    def post(self, path, body=None, token=None, files=None):
        if files:
            return self._req("POST", path, token, files=files)
        return self._req("POST", path, token, json=body)

    def put(self, path, body=None, token=None):
        return self._req("PUT", path, token, json=body)

    def delete(self, path, token=None):
        return self._req("DELETE", path, token)


api = Api(BASE)


def jbody(resp):
    try:
        return resp.json()
    except ValueError:
        return {}


def err(resp):
    body = jbody(resp)
    return body.get("message") or body.get("error") or resp.text[:200]


# ─── Database (only to flip email verification, as the API cannot) ────────────

_db_url = None


def db_url():
    global _db_url
    if _db_url:
        return _db_url
    if TARGET != "prod":
        _db_url = LOCAL_DB
        return _db_url
    out = subprocess.run(
        ["railway", "run", "--service", "Postgres", "--", "printenv", "DATABASE_PUBLIC_URL"],
        capture_output=True, text=True, cwd=os.path.dirname(os.path.abspath(__file__)))
    url = out.stdout.strip()
    if not url.startswith("postgres"):
        sys.exit("Could not read DATABASE_PUBLIC_URL from Railway — is `railway link` set up?")
    _db_url = url
    return _db_url


def sql(statement):
    out = subprocess.run(["psql", db_url(), "-tA", "-c", statement], capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip())
    return out.stdout.strip()


# ─── Illustrated avatars ──────────────────────────────────────────────────────

FONT_CANDIDATES = [
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial.ttf",
    "/Library/Fonts/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def _font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def _tint(seed, light=False):
    """A stable brand-family colour per profile, so avatars are distinguishable."""
    hues = [(46, 158, 107), (58, 138, 171), (191, 137, 66), (147, 108, 176),
            (198, 93, 93), (86, 142, 63), (72, 122, 158), (176, 122, 84)]
    base = hues[sum(ord(c) for c in seed) % len(hues)]
    if light:
        return tuple(int(c + (255 - c) * 0.86) for c in base)
    return base


def _paw(draw, cx, cy, r, colour):
    draw.ellipse([cx - r, cy - r * 0.85, cx + r, cy + r * 1.05], fill=colour)
    for i, (dx, dy, rr) in enumerate([(-1.35, -1.35, 0.44), (-0.5, -1.75, 0.46),
                                      (0.5, -1.75, 0.46), (1.35, -1.35, 0.44)]):
        draw.ellipse([cx + dx * r - rr * r, cy + dy * r - rr * r,
                      cx + dx * r + rr * r, cy + dy * r + rr * r], fill=colour)


def _centred_text(d, width, text, cy, max_width, start_size, colour):
    """Draw text centred on cy, shrinking until it fits max_width."""
    size = start_size
    font = _font(size)
    while size > 12:
        box = d.textbbox((0, 0), text, font=font)
        if box[2] - box[0] <= max_width:
            break
        size = int(size * 0.9)
        font = _font(size)
    box = d.textbbox((0, 0), text, font=font)
    d.text(((width - (box[2] - box[0])) / 2 - box[0], cy - (box[3] - box[1]) / 2 - box[1]),
           text, font=font, fill=colour)


def avatar_png(seed, label, kind):
    """An obviously illustrated avatar: brand-tinted ground, initials, paw mark.

    Deliberately not a photograph of a person or a dog — this is demo data, and a
    stock headshot would be presenting a real face as a fictional profile.
    """
    size = 1080
    img = Image.new("RGB", (size, size), _tint(seed, light=True))
    d = ImageDraw.Draw(img)
    accent = _tint(seed)
    soft = tuple(int(c + (255 - c) * 0.75) for c in accent)

    d.ellipse([-size * 0.28, size * 0.52, size * 0.48, size * 1.3], fill=soft)
    d.ellipse([size * 0.66, -size * 0.2, size * 1.32, size * 0.46], fill=soft)

    # Badge the mark sits in, so the composition reads the same at thumbnail size
    r = size * 0.29
    cx, cy = size * 0.5, size * 0.42
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=tuple(int(c + (255 - c) * 0.88) for c in accent))

    if kind == "dog":
        _paw(d, cx, cy + size * 0.03, size * 0.13, accent)
        _centred_text(d, size, label, size * 0.83, size * 0.86, int(size * 0.11), accent)
    else:
        initials = "".join(w[0] for w in label.split()[:2]).upper() or "D"
        _centred_text(d, size, initials, cy, r * 1.5, int(size * 0.26), accent)
        _paw(d, size * 0.84, size * 0.85, size * 0.07, soft)

    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


# ─── Distance (mirrors GeoUtil, only used for the printed plan) ───────────────

def distance_km(a, b):
    lat1, lon1 = a; lat2, lon2 = b
    r = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    h = (math.sin(dlat / 2) ** 2
         + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2) ** 2)
    return 2 * r * math.asin(math.sqrt(h))


# ─── Account plumbing ─────────────────────────────────────────────────────────

def email_for(profile):
    slug = profile["name"].lower().replace(" ", ".")
    for a, b in (("é", "e"), ("è", "e"), ("ç", "c"), ("ñ", "n"), ("'", "")):
        slug = slug.replace(a, b)
    return f"{slug}@{TEST_DOMAIN}"


def login(email, password):
    r = api.post("/auth/login", {"email": email, "password": password})
    if r.status_code == 200:
        return jbody(r)["token"]
    return None


def ensure_account(profile):
    """Register (if new), verify by hand, log in. Returns (token, user_id)."""
    email = email_for(profile)
    token = login(email, PASSWORD)
    if not token:
        r = api.post("/auth/register", {"email": email, "name": profile["name"], "password": PASSWORD})
        if r.status_code not in (200, 201):
            body = err(r)
            if "already" not in body.lower():
                fail(f"{email}: register failed — {body}")
                return None, None
        try:
            sql(f"UPDATE users SET email_verified=true, is_active=true WHERE email='{email}';")
        except RuntimeError as e:
            fail(f"{email}: could not flip email_verified — {e}")
            return None, None
        token = login(email, PASSWORD)
        if not token:
            fail(f"{email}: login failed after registering")
            return None, None
    me = jbody(api.get("/users/me", token))
    return token, me.get("id")


def apply_profile(profile, token):
    payload = {
        "name": profile["name"],
        "bio": profile["bio"],
        "dateOfBirth": profile["dob"],
        "latitude": profile["loc"][0],
        "longitude": profile["loc"][1],
        "lifestyleTags": profile.get("lifestyle", []),
        "personalityTags": profile.get("personality", []),
        "hasDog": profile.get("has_dog", "dog" in profile),
        "isSitter": bool(profile.get("sitter")),
        "lookingForSitter": bool(profile.get("seeker")),
        "sitterWeekdays": profile.get("weekdays", []),
        "sitterExperienceYears": profile.get("sitter_years", 0),
        "sitterTags": profile.get("sitter_tags", []),
        "maxDistanceKm": 50,
    }
    r = api.put("/users/me", payload, token)
    if r.status_code != 200:
        fail(f"{profile['name']}: profile update failed — {err(r)}")
        return False
    return True


def ensure_user_photo(profile, token):
    me = jbody(api.get("/users/me", token))
    if me.get("photos"):
        return
    png = avatar_png(profile["key"], profile["name"], "user")
    r = api.post("/users/me/photos", token=token,
                 files={"file": (f"{profile['key']}.png", png, "image/png")})
    if r.status_code not in (200, 201):
        warn(f"{profile['name']}: photo upload failed — {err(r)}")


def ensure_dog(profile, token):
    spec = profile.get("dog")
    if not spec:
        return None
    existing = jbody(api.get("/dogs/me", token))
    for dog in existing if isinstance(existing, list) else []:
        if dog.get("name") == spec["name"]:
            ensure_dog_photo(profile, spec, dog["id"], dog, token)
            return dog["id"]
    r = api.post("/dogs", {
        "name": spec["name"], "breed": spec["breed"], "dateOfBirth": spec["dob"],
        "bio": spec["bio"], "energyLevel": spec["energy"], "socialBehavior": spec["social"],
        "loves": spec.get("loves", []), "offLeash": spec.get("off_leash"),
        "kidsComfort": spec.get("kids"), "tags": spec.get("tags", []),
    }, token)
    if r.status_code not in (200, 201):
        fail(f"{profile['name']}: dog {spec['name']} failed — {err(r)}")
        return None
    dog = jbody(r)
    ensure_dog_photo(profile, spec, dog["id"], dog, token)
    return dog["id"]


def ensure_dog_photo(profile, spec, dog_id, dog_json, token):
    if dog_json.get("photos"):
        return
    png = avatar_png(profile["key"] + spec["name"], spec["name"], "dog")
    r = api.post(f"/dogs/{dog_id}/photos", token=token,
                 files={"file": (f"{spec['name'].lower()}.png", png, "image/png")})
    if r.status_code not in (200, 201):
        warn(f"{spec['name']}: photo upload failed — {err(r)}")


# ─── Demo account ─────────────────────────────────────────────────────────────

DEMO_BIO = ("Review account for Dogs Out, based at Apple Park while testing. Buddy is a "
            "golden retriever who has never met a stranger he didn't like.")

DEMO_DOG = dict(name="Luna", breed="Labrador Retriever", dob="2021-05-04",
                bio="Demo dog for the review account. Loves water, tennis balls, and strangers.",
                energy=4, social="Instant best friends", loves=["Swimming", "Fetch / ball games"],
                off_leash="Yes, fully reliable", kids=5, tags=["Fetch obsessed", "Dog park regular"])


def setup_demo(email):
    token = login(email, DEMO_PASSWORD)
    if not token:
        return None, None
    me = jbody(api.get("/users/me", token))
    payload = {
        "latitude": APPLE_PARK[0], "longitude": APPLE_PARK[1],
        "hasDog": True, "isSitter": True, "lookingForSitter": True,
        "sitterWeekdays": ["Saturday", "Sunday"],
        "sitterExperienceYears": 2,
        "sitterTags": ["Has a yard", "Puppy experience"],
        "maxDistanceKm": 50,
        # 0 / -1 clear a filter server-side — undoes any narrowing a previous
        # reviewer left behind in Discover's filter sheet.
        "minAge": 0, "maxAge": 0, "minDogAge": -1, "maxDogAge": 0,
    }
    if not me.get("dateOfBirth"):
        payload["dateOfBirth"] = "1992-04-16"
    # Always reset the bio: it is the reviewer's own profile text, and a leftover
    # "exploring Zürich" bio reads oddly next to a Cupertino feed.
    payload["bio"] = DEMO_BIO
    r = api.put("/users/me", payload, token)
    if r.status_code != 200:
        fail(f"{email}: profile update failed — {err(r)}")
        return None, None

    dogs = jbody(api.get("/dogs/me", token))
    if not dogs:
        d = api.post("/dogs", {
            "name": DEMO_DOG["name"], "breed": DEMO_DOG["breed"], "dateOfBirth": DEMO_DOG["dob"],
            "bio": DEMO_DOG["bio"], "energyLevel": DEMO_DOG["energy"],
            "socialBehavior": DEMO_DOG["social"], "loves": DEMO_DOG["loves"],
            "offLeash": DEMO_DOG["off_leash"], "kidsComfort": DEMO_DOG["kids"],
            "tags": DEMO_DOG["tags"],
        }, token)
        if d.status_code in (200, 201):
            dog = jbody(d)
            api.post(f"/dogs/{dog['id']}/photos", token=token,
                     files={"file": ("luna.png", avatar_png("demoluna", "Luna", "dog"), "image/png")})
            ok(f"{email}: added demo dog Luna")
        else:
            warn(f"{email}: could not add a dog — {err(d)}")

    me = jbody(api.get("/users/me", token))
    if not me.get("photos"):
        api.post("/users/me/photos", token=token,
                 files={"file": ("demo.png", avatar_png("demo", me.get("name", "Demo"), "user"), "image/png")})
    return token, me.get("id")


# ─── Wiring: likes, matches, conversations, playdates ─────────────────────────

def like(token, target_id):
    r = api.post("/matches/swipe", {"targetUserId": target_id, "action": "LIKE"}, token)
    if r.status_code not in (200, 201):
        return None
    return jbody(r)


def seed_conversation(profile, their_token, their_id, demo_token, demo_id):
    """Mutual like → MATCHED, then fill the thread. Sitters use /sitters/contact."""
    if profile.get("sitter") or profile.get("seeker"):
        r = api.post("/sitters/contact", {"targetUserId": demo_id}, their_token)
        if r.status_code not in (200, 201):
            fail(f"{profile['name']}: sitter contact failed — {err(r)}")
            return
        match_id = jbody(r)["matchId"]
    else:
        like(their_token, demo_id)
        res = like(demo_token, their_id)
        if not res or not res.get("match"):
            fail(f"{profile['name']}: no mutual match after swiping")
            return
        match_id = res["matchId"]

    existing = jbody(api.get(f"/chats/{match_id}/messages", demo_token))
    if isinstance(existing, list) and existing:
        dim(f"    conversation with {profile['name']} already has {len(existing)} messages")
        return
    for who, text in CONVERSATIONS.get(profile["key"], []):
        token = their_token if who == "them" else demo_token
        r = api.post(f"/chats/{match_id}/messages", {"content": text}, token)
        if r.status_code not in (200, 201):
            warn(f"{profile['name']}: message failed — {err(r)}")
            break
    ok(f"  chat with {profile['name']} ({len(CONVERSATIONS.get(profile['key'], []))} messages)")


def playdate_starts_at(spec):
    """The wall-clock time is what matters to a reviewer in Cupertino, so build the
    instant from Pacific local time rather than shifting UTC by a fixed offset."""
    try:
        from zoneinfo import ZoneInfo
        tz = ZoneInfo("America/Los_Angeles")
    except Exception:  # pragma: no cover — zoneinfo missing, PDT is close enough
        tz = timezone(timedelta(hours=-7))
    local = (datetime.now(tz) + timedelta(days=spec["days_out"])).replace(
        hour=spec["hour"], minute=spec["minute"], second=0, microsecond=0)
    return local.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def seed_playdate(spec, tokens, ids, demo_token):
    host_token = tokens[spec["host"]]
    starts_at = playdate_starts_at(spec)

    feed = jbody(api.get("/playdates", host_token))
    found = next((p for p in feed if p.get("title") == spec["title"]), None) if isinstance(feed, list) else None

    if found:
        # Keep a seeded playdate in the future — review can take days.
        when = datetime.fromisoformat(found["startsAt"].replace("Z", "+00:00"))
        if when < datetime.now(timezone.utc) + timedelta(days=1):
            api.put(f"/playdates/{found['id']}", {
                "title": spec["title"], "description": spec["description"],
                "parkName": spec["park"], "address": spec["address"],
                "latitude": spec["loc"][0], "longitude": spec["loc"][1],
                "startsAt": starts_at, "maxParticipants": spec["max_participants"],
            }, host_token)
            ok(f"  playdate \"{spec['title']}\" moved to {starts_at}")
        playdate_id = found["id"]
    else:
        r = api.post("/playdates", {
            "title": spec["title"], "description": spec["description"],
            "parkName": spec["park"], "address": spec["address"],
            "latitude": spec["loc"][0], "longitude": spec["loc"][1],
            "startsAt": starts_at, "maxParticipants": spec["max_participants"],
            "visibility": "PUBLIC",
        }, host_token)
        if r.status_code not in (200, 201):
            fail(f"playdate \"{spec['title']}\" failed — {err(r)}")
            return
        playdate_id = jbody(r)["id"]
        ok(f"  playdate \"{spec['title']}\" at {spec['park']} ({starts_at})")

    for key in spec["joiners"]:
        j = api.post(f"/playdates/{playdate_id}/join", token=tokens[key])
        if j.status_code not in (200, 201, 409):
            warn(f"  {key} could not join \"{spec['title']}\" — {err(j)}")
    if spec["demo_joins"] and demo_token:
        j = api.post(f"/playdates/{playdate_id}/join", token=demo_token)
        if j.status_code not in (200, 201):
            warn(f"  demo account could not join \"{spec['title']}\" — {err(j)}")

    if spec["chat"]:
        existing = jbody(api.get(f"/playdates/{playdate_id}/messages", host_token))
        if isinstance(existing, list) and existing:
            dim(f"    group chat already has {len(existing)} messages")
            return
        for key, text in spec["chat"]:
            m = api.post(f"/playdates/{playdate_id}/messages", {"content": text}, tokens[key])
            if m.status_code not in (200, 201):
                warn(f"  group message failed — {err(m)}")
                break
        ok(f"    group chat seeded ({len(spec['chat'])} messages)")


# ─── Commands ─────────────────────────────────────────────────────────────────

def cmd_dry_run():
    print(f"\nDRY RUN — nothing is written. Target: {TARGET} ({BASE})\n")
    print(f"  Demo account moves to Apple Park {APPLE_PARK}, both dogsitting roles on,\n"
          f"  Discover filters cleared, search radius 50 km.\n")
    for p in PROFILES:
        d = distance_km(APPLE_PARK, p["loc"])
        roles = []
        if p.get("sitter"):
            roles.append("sitter")
        if p.get("seeker"):
            roles.append("wants sitter")
        if p.get("dog"):
            roles.append(f"dog: {p['dog']['name']}")
        if p["key"] in CONVERSATIONS:
            roles.append(f"chat ({len(CONVERSATIONS[p['key']])} msgs)")
        print(f"  {email_for(p):32} {p['name']:16} {d:5.1f} km  {', '.join(roles)}")
    print()
    for spec in PLAYDATES:
        print(f"  playdate  {spec['title']:38} {spec['park']}, in {spec['days_out']} days"
              f"{' (demo joins)' if spec['demo_joins'] else ''}")
    print(f"\n  {len(PROFILES)} accounts, password {PASSWORD}, all @{TEST_DOMAIN}")
    print("  Re-run with --apply to create them, --cleanup to remove them.\n")


def cmd_apply():
    print(f"\nSeeding Apple Park demo content into {TARGET} ({BASE})\n")

    demo_tokens = {}
    for email in DEMO_EMAILS:
        token, uid = setup_demo(email)
        if token:
            demo_tokens[email] = (token, uid)
            ok(f"demo account {email} → Apple Park, sitter+seeker on, filters cleared")
        else:
            warn(f"demo account {email}: could not sign in (skipped)")
    if not demo_tokens:
        sys.exit("No demo account could be signed into — check the credentials in App Store Connect.")

    tokens, ids = {}, {}
    print()
    for p in PROFILES:
        token, uid = ensure_account(p)
        if not token:
            continue
        if not apply_profile(p, token):
            continue
        ensure_user_photo(p, token)
        ensure_dog(p, token)
        tokens[p["key"]], ids[p["key"]] = token, uid
        bits = []
        if p.get("sitter"):
            bits.append("sitter")
        if p.get("seeker"):
            bits.append("wants sitter")
        if p.get("dog"):
            bits.append(p["dog"]["name"])
        ok(f"{p['name']:16} {distance_km(APPLE_PARK, p['loc']):5.1f} km  {', '.join(bits)}")

    for email, (demo_token, demo_id) in demo_tokens.items():
        print(f"\nWiring content for {email}")
        # Pending likes: the reviewer's first right swipe produces a match overlay.
        for p in PROFILES:
            if p["role"] == "discover" and p["key"] in tokens:
                like(tokens[p["key"]], demo_id)
        ok(f"  {sum(1 for p in PROFILES if p['role'] == 'discover')} profiles have liked the demo account")

        for p in PROFILES:
            if p["key"] in CONVERSATIONS and p["key"] in tokens:
                seed_conversation(p, tokens[p["key"]], ids[p["key"]], demo_token, demo_id)

    primary = next(iter(demo_tokens.values()))[0]
    print()
    for spec in PLAYDATES:
        if spec["host"] in tokens:
            seed_playdate(spec, tokens, ids, primary)

    print()
    cmd_verify()


def cmd_verify():
    print(f"What the reviewer sees ({TARGET}):\n")
    for email in DEMO_EMAILS:
        token = login(email, DEMO_PASSWORD)
        if not token:
            warn(f"{email}: cannot sign in")
            continue
        me = jbody(api.get("/users/me", token))
        loc = (me.get("latitude"), me.get("longitude"))
        where = f"{loc[0]:.4f}, {loc[1]:.4f}" if None not in loc else "not set"
        km = f" ({distance_km(APPLE_PARK, loc):.1f} km from Apple Park)" if None not in loc else ""

        def count(path):
            r = api.get(path, token)
            body = jbody(r)
            return len(body) if isinstance(body, list) else f"{r.status_code} {err(r)[:40]}"

        dogs = jbody(api.get("/dogs/me", token))
        print(f"  {email}")
        print(f"    location        {where}{km}")
        print(f"    roles           sitter={me.get('isSitter')} lookingForSitter={me.get('lookingForSitter')} "
              f"hasDog={me.get('hasDog')} dogs={len(dogs) if isinstance(dogs, list) else '?'}")
        print(f"    filters         radius={me.get('maxDistanceKm')} km age={me.get('minAge')}-{me.get('maxAge')} "
              f"dogAge={me.get('minDogAge')}-{me.get('maxDogAge')}")
        print(f"    Discover        {count('/discover')} profiles")
        print(f"    Dogsitting      {count('/sitters/available')} sitters, {count('/sitters/seekers')} owners seeking")
        print(f"    Chats           {count('/matches')} conversations")
        print(f"    Playdates       {count('/playdates')} upcoming")
        print()


def cmd_cleanup():
    print(f"\nDeleting seeded @{TEST_DOMAIN} accounts from {TARGET} ({BASE})")
    print("(via DELETE /users/me, which also removes their dogs, matches, messages and photos)\n")
    removed = 0
    for p in PROFILES:
        email = email_for(p)
        token = login(email, PASSWORD)
        if not token:
            dim(f"  {email} — not present")
            continue
        r = api.delete("/users/me", token)
        if r.status_code in (200, 204):
            ok(f"  {email} deleted")
            removed += 1
        else:
            fail(f"  {email} — {err(r)}")
    print(f"\n{removed} accounts removed. The demo account itself was left untouched.\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--apply", action="store_true", help="create/refresh the demo content")
    g.add_argument("--verify", action="store_true", help="read-only check of what each tab shows")
    g.add_argument("--cleanup", action="store_true", help="delete the seeded accounts")
    args = ap.parse_args()

    if args.apply:
        cmd_apply()
    elif args.verify:
        cmd_verify()
    elif args.cleanup:
        cmd_cleanup()
    else:
        cmd_dry_run()


if __name__ == "__main__":
    main()
