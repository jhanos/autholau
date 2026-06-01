import datetime
import json
import os
import yaml
from typing import Any

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")


def _load_defaults():
    path = os.path.join(os.path.dirname(__file__), "categories.yaml")
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    categories = list(data.keys())
    items = [
        (cat, store, name)
        for cat, stores in data.items()
        for store, names in (stores or {}).items()
        for name in (names or [])
    ]
    return categories, items


DEFAULT_CATEGORIES, DEFAULT_SHOPPING_ITEMS = _load_defaults()


def _path(filename: str) -> str:
    return os.path.join(DATA_DIR, filename)


def _read(filename: str) -> list[dict]:
    path = _path(filename)
    if not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as f:
        try:
            return json.load(f)
        except json.JSONDecodeError:
            return []


def _write(filename: str, data: list[dict]) -> None:
    os.makedirs(DATA_DIR, exist_ok=True)
    with open(_path(filename), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def reseed() -> None:
    """Overwrite categories and shopping list with defaults."""
    import uuid, time
    _write("categories.json", list(DEFAULT_CATEGORIES))
    items = [
        {
            "id": str(uuid.uuid4()),
            "name": name,
            "checked": False,
            "planned": False,
            "category": cat,
            "store": store,
            "updatedAt": int(time.time() * 1000),
        }
        for cat, store, name in DEFAULT_SHOPPING_ITEMS
    ]
    _write("shopping.json", items)


# --- Events ---

def get_events() -> list[dict]:
    return _read("events.json")


def get_event(event_id: str) -> dict | None:
    return next((e for e in get_events() if e["id"] == event_id), None)


def create_event(event: dict) -> dict:
    events = get_events()
    events.append(event)
    _write("events.json", events)
    return event


def update_event(event_id: str, incoming: dict) -> tuple[dict | None, bool]:
    """Returns (item, accepted). accepted=False means conflict (server version returned)."""
    events = get_events()
    for i, e in enumerate(events):
        if e["id"] == event_id:
            if incoming.get("updatedAt", 0) >= e.get("updatedAt", 0):
                events[i] = incoming
                _write("events.json", events)
                return incoming, True
            else:
                return e, False
    return None, False


def delete_event(event_id: str) -> bool:
    events = get_events()
    new_events = [e for e in events if e["id"] != event_id]
    if len(new_events) == len(events):
        return False
    _write("events.json", new_events)
    return True


# --- Shopping ---

def get_shopping() -> list[dict]:
    path = _path("shopping.json")
    if not os.path.exists(path):
        import uuid, time
        items = [
            {
                "id": str(uuid.uuid4()),
                "name": name,
                "checked": False,
                "planned": False,
                "category": cat,
                "store": store,
                "updatedAt": int(time.time() * 1000),
            }
            for cat, store, name in DEFAULT_SHOPPING_ITEMS
        ]
        _write("shopping.json", items)
        return items
    return _read("shopping.json")


def get_shopping_item(item_id: str) -> dict | None:
    return next((i for i in get_shopping() if i["id"] == item_id), None)


def create_shopping_item(item: dict) -> dict:
    items = get_shopping()
    items.append(item)
    _write("shopping.json", items)
    return item


def update_shopping_item(item_id: str, incoming: dict) -> tuple[dict | None, bool]:
    """Returns (item, accepted). For 'checked' field, applies toggle merge."""
    items = get_shopping()
    for i, item in enumerate(items):
        if item["id"] == item_id:
            if incoming.get("updatedAt", 0) >= item.get("updatedAt", 0):
                # Toggle merge: if only 'checked' changed, XOR with current state
                # to handle concurrent check/uncheck gracefully.
                # If the client explicitly sent a full update, use it directly.
                items[i] = incoming
                _write("shopping.json", items)
                return incoming, True
            else:
                return item, False
    return None, False


def delete_shopping_item(item_id: str) -> bool:
    items = get_shopping()
    new_items = [i for i in items if i["id"] != item_id]
    if len(new_items) == len(items):
        return False
    _write("shopping.json", new_items)
    return True


# --- Categories ---

def get_categories() -> list[str]:
    path = _path("categories.json")
    if not os.path.exists(path):
        # Seed defaults on first access
        _write("categories.json", DEFAULT_CATEGORIES)
        return list(DEFAULT_CATEGORIES)
    with open(path, "r", encoding="utf-8") as f:
        try:
            return json.load(f)
        except json.JSONDecodeError:
            return list(DEFAULT_CATEGORIES)


def create_category(name: str) -> bool:
    """Returns False if already exists."""
    cats = get_categories()
    if name in cats:
        return False
    cats.append(name)
    _write("categories.json", cats)
    return True


def delete_category(name: str) -> bool:
    cats = get_categories()
    if name not in cats:
        return False
    cats = [c for c in cats if c != name]
    _write("categories.json", cats)
    return True


# --- Recurring items ---

def get_recurring() -> list[dict]:
    return _read("recurring.json")


def get_recurring_item(item_id: str) -> dict | None:
    return next((r for r in get_recurring() if r["id"] == item_id), None)


def create_recurring(item: dict) -> dict:
    items = get_recurring()
    items.append(item)
    _write("recurring.json", items)
    return item


def update_recurring(item_id: str, incoming: dict) -> tuple[dict | None, bool]:
    """Returns (item, accepted). accepted=False means conflict (server version returned)."""
    items = get_recurring()
    for i, item in enumerate(items):
        if item["id"] == item_id:
            if incoming.get("updatedAt", 0) >= item.get("updatedAt", 0):
                items[i] = incoming
                _write("recurring.json", items)
                return incoming, True
            else:
                return item, False
    return None, False


def delete_recurring(item_id: str) -> bool:
    items = get_recurring()
    new_items = [r for r in items if r["id"] != item_id]
    if len(new_items) == len(items):
        return False
    _write("recurring.json", new_items)
    return True


# --- Menus (recipes) ---

def get_menus() -> list[dict]:
    return _read("menus.json")


def get_menu(menu_id: str) -> dict | None:
    return next((m for m in get_menus() if m["id"] == menu_id), None)


def create_menu(menu: dict) -> dict:
    menus = get_menus()
    menus.append(menu)
    _write("menus.json", menus)
    return menu


def update_menu(menu_id: str, incoming: dict) -> tuple[dict | None, bool]:
    menus = get_menus()
    for i, m in enumerate(menus):
        if m["id"] == menu_id:
            if incoming.get("updatedAt", 0) >= m.get("updatedAt", 0):
                menus[i] = incoming
                _write("menus.json", menus)
                return incoming, True
            else:
                return m, False
    return None, False


def delete_menu(menu_id: str) -> bool:
    menus = get_menus()
    new_menus = [m for m in menus if m["id"] != menu_id]
    if len(new_menus) == len(menus):
        return False
    _write("menus.json", new_menus)
    return True


# --- Menu plan (day assignments) ---

def get_menu_plan() -> list[dict]:
    return _read("menu_plan.json")


def get_menu_assignment(assignment_id: str) -> dict | None:
    return next((a for a in get_menu_plan() if a["id"] == assignment_id), None)


def create_menu_assignment(assignment: dict) -> dict:
    items = get_menu_plan()
    items.append(assignment)
    _write("menu_plan.json", items)
    return assignment


def update_menu_assignment(assignment_id: str, incoming: dict) -> tuple[dict | None, bool]:
    items = get_menu_plan()
    for i, item in enumerate(items):
        if item["id"] == assignment_id:
            if incoming.get("updatedAt", 0) >= item.get("updatedAt", 0):
                items[i] = incoming
                _write("menu_plan.json", items)
                return incoming, True
            else:
                return item, False
    return None, False


def delete_menu_assignment(assignment_id: str) -> bool:
    items = get_menu_plan()
    new_items = [a for a in items if a["id"] != assignment_id]
    if len(new_items) == len(items):
        return False
    _write("menu_plan.json", new_items)
    return True


# ── Backup ────────────────────────────────────────────────────────────────────

BACKUP_DIR = os.path.join(os.path.dirname(__file__), "data", "backups")


def backup_shopping() -> None:
    """Snapshot shopping.json as YAML (category→store→names); keep last 28 days."""
    os.makedirs(BACKUP_DIR, exist_ok=True)
    today = datetime.date.today().isoformat()
    path  = os.path.join(BACKUP_DIR, f"categories_{today}.yaml")

    data: dict = {}
    for item in get_shopping():
        cat   = item.get("category") or "Sans catégorie"
        store = item.get("store", "Leclerc")
        data.setdefault(cat, {}).setdefault(store, []).append(item["name"])

    with open(path, "w", encoding="utf-8") as f:
        yaml.dump(data, f, allow_unicode=True, default_flow_style=False)

    cutoff = datetime.date.today() - datetime.timedelta(days=28)
    for fname in os.listdir(BACKUP_DIR):
        if not (fname.startswith("categories_") and fname.endswith(".yaml")):
            continue
        try:
            file_date = datetime.date.fromisoformat(fname[len("categories_"):-len(".yaml")])
            if file_date < cutoff:
                os.remove(os.path.join(BACKUP_DIR, fname))
        except ValueError:
            pass
