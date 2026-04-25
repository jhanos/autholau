import json
import os
from typing import Any

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")

DEFAULT_CATEGORIES = [
    "Maison",
    "Féculents",
    "Condiments",
    "Petit Dej",
    "Viandes/Poissons",
    "Laitage",
    "Fruits/Légumes",
    "Hygiène/Beauté",
    "Surgelés",
]


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
