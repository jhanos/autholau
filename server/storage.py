import json
import os
from typing import Any

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")

DEFAULT_CATEGORIES = [
    "Maison",
    "Féculents",
    "Condiments",
    "Petit Dej/Gouter",
    "Viandes/Poissons",
    "Laitage",
    "Fruits/Légumes",
    "Hygiène/Beauté",
    "Surgelés",
]

# (category, store, name) tuples — seeded into shopping.json on first access
DEFAULT_SHOPPING_ITEMS = [
    # Maison — Leclerc
    ("Maison", "Leclerc", "Sel adoucisseur"),
    ("Maison", "Leclerc", "Sac poubelle"),
    ("Maison", "Leclerc", "Papier toilette"),
    ("Maison", "Leclerc", "Sopalin"),
    ("Maison", "Leclerc", "Film alimentaire"),
    ("Maison", "Leclerc", "Piles"),
    ("Maison", "Leclerc", "Lessive"),
    ("Maison", "Leclerc", "Liquide vaisselle"),
    ("Maison", "Leclerc", "Produit WC"),
    ("Maison", "Leclerc", "Éponges"),
    ("Maison", "Leclerc", "Papier aluminium"),
    ("Maison", "Leclerc", "Vinaigre blanc"),
    ("Maison", "Leclerc", "Litière Chat"),
    ("Maison", "Leclerc", "Biscuit pour chat"),
    ("Maison", "Leclerc", "Biscuits pour chien"),
    # Féculents
    ("Féculents", "Leclerc", "Pâtes"),
    ("Féculents", "Leclerc", "Riz"),
    ("Féculents", "Leclerc", "Semoule"),
    ("Féculents", "Leclerc", "Pommes de terre"),
    ("Féculents", "Leclerc", "Farine"),
    ("Féculents", "Grand Frais", "Pain"),
    # Condiments
    ("Condiments", "Leclerc", "Huile d'olive"),
    ("Condiments", "Leclerc", "Ketchup"),
    # Petit Dej/Gouter — Leclerc
    ("Petit Dej/Gouter", "Leclerc", "Café"),
    ("Petit Dej/Gouter", "Leclerc", "Thé"),
    ("Petit Dej/Gouter", "Leclerc", "Cacao"),
    ("Petit Dej/Gouter", "Leclerc", "Compote"),
    ("Petit Dej/Gouter", "Leclerc", "Confiture"),
    ("Petit Dej/Gouter", "Leclerc", "Nocciolata"),
    ("Petit Dej/Gouter", "Leclerc", "Biscuits gouter"),
    ("Petit Dej/Gouter", "Leclerc", "Céréales"),
    ("Petit Dej/Gouter", "Leclerc", "Pain tranché"),
    # Viandes/Poissons — Leclerc
    ("Viandes/Poissons", "Leclerc", "Poulet"),
    ("Viandes/Poissons", "Leclerc", "Jambon"),
    ("Viandes/Poissons", "Leclerc", "Saucisse"),
    ("Viandes/Poissons", "Leclerc", "Poulet pané"),
    ("Viandes/Poissons", "Leclerc", "Lardons"),
    ("Viandes/Poissons", "Leclerc", "Lardons Saumon"),
    ("Viandes/Poissons", "Leclerc", "Thon"),
    # Laitage — Leclerc
    ("Laitage", "Leclerc", "Lait"),
    ("Laitage", "Leclerc", "Yaourt Augustine"),
    ("Laitage", "Leclerc", "Gruyère"),
    ("Laitage", "Leclerc", "Parmesan"),
    ("Laitage", "Leclerc", "Oeufs"),
    ("Laitage", "Leclerc", "Yaourt Thomas"),
    ("Laitage", "Leclerc", "Yaourt Laura"),
    ("Laitage", "Leclerc", "Beurre"),
    ("Laitage", "Leclerc", "Petit beurre"),
    ("Laitage", "Leclerc", "Kiri"),
    ("Laitage", "Leclerc", "Skyr"),
    ("Laitage", "Leclerc", "Lait Végétal"),
    # Laitage — Grand Frais
    ("Laitage", "Grand Frais", "Oeufs"),
    ("Laitage", "Grand Frais", "Yaourt Chocolat"),
    ("Laitage", "Grand Frais", "Yaourt trois chocolats"),
    # Fruits/Légumes — Grand Frais
    ("Fruits/Légumes", "Grand Frais", "Framboises"),
    ("Fruits/Légumes", "Grand Frais", "Fraises"),
    ("Fruits/Légumes", "Grand Frais", "Melons"),
    ("Fruits/Légumes", "Grand Frais", "Tomates"),
    ("Fruits/Légumes", "Grand Frais", "Salade"),
    ("Fruits/Légumes", "Grand Frais", "Concombre"),
    ("Fruits/Légumes", "Grand Frais", "Carottes"),
    ("Fruits/Légumes", "Grand Frais", "Pommes"),
    ("Fruits/Légumes", "Grand Frais", "Bananes"),
    ("Fruits/Légumes", "Grand Frais", "Oranges"),
    ("Fruits/Légumes", "Grand Frais", "Citrons"),
    ("Fruits/Légumes", "Grand Frais", "Poivrons"),
    ("Fruits/Légumes", "Grand Frais", "Pommes de terre"),
    # Hygiène/Beauté — Leclerc
    ("Hygiène/Beauté", "Leclerc", "Brosse à dents"),
    ("Hygiène/Beauté", "Leclerc", "Dentifrice"),
    ("Hygiène/Beauté", "Leclerc", "Shampoing"),
    ("Hygiène/Beauté", "Leclerc", "Gel douche"),
    ("Hygiène/Beauté", "Leclerc", "Coton-tiges"),
    # Surgelés — Leclerc
    ("Surgelés", "Leclerc", "Pizza"),
    ("Surgelés", "Leclerc", "Frites"),
    ("Surgelés", "Leclerc", "Nuggets"),
    ("Surgelés", "Leclerc", "Glaces"),
    ("Surgelés", "Leclerc", "Petits pois"),
    ("Surgelés", "Leclerc", "Haricots verts"),
    ("Surgelés", "Leclerc", "Poissons ronds"),
    ("Surgelés", "Leclerc", "Poissons panés"),
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
