import os
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv

import auth
import storage

load_dotenv()

app = Flask(__name__)
CORS(app)

FAMILY_PASSWORD = os.environ.get("FAMILY_PASSWORD", "autholau")


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"}), 200


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

@app.route("/auth/login", methods=["POST"])
def login():
    data = request.get_json(silent=True) or {}
    password = data.get("password", "")
    if password != FAMILY_PASSWORD:
        return jsonify({"error": "Invalid password"}), 401
    token = auth.generate_token()
    return jsonify({"token": token}), 200


# ---------------------------------------------------------------------------
# Events
# ---------------------------------------------------------------------------

@app.route("/events", methods=["GET"])
@auth.require_auth
def list_events():
    return jsonify(storage.get_events()), 200


@app.route("/events", methods=["POST"])
@auth.require_auth
def create_event():
    data = request.get_json(silent=True)
    if not data or not data.get("id") or not data.get("title") or not data.get("date"):
        return jsonify({"error": "Missing required fields: id, title, date"}), 400
    if storage.get_event(data["id"]):
        return jsonify({"error": "Event already exists"}), 409
    event = storage.create_event(data)
    return jsonify(event), 201


@app.route("/events/<event_id>", methods=["PUT"])
@auth.require_auth
def update_event(event_id: str):
    data = request.get_json(silent=True)
    if not data:
        return jsonify({"error": "Missing body"}), 400
    data["id"] = event_id
    result, accepted = storage.update_event(event_id, data)
    if result is None:
        return jsonify({"error": "Event not found"}), 404
    if not accepted:
        return jsonify(result), 409
    return jsonify(result), 200


@app.route("/events/<event_id>", methods=["DELETE"])
@auth.require_auth
def delete_event(event_id: str):
    if not storage.delete_event(event_id):
        return jsonify({"error": "Event not found"}), 404
    return "", 204


# ---------------------------------------------------------------------------
# Shopping
# ---------------------------------------------------------------------------

@app.route("/shopping", methods=["GET"])
@auth.require_auth
def list_shopping():
    return jsonify(storage.get_shopping()), 200


@app.route("/shopping", methods=["POST"])
@auth.require_auth
def create_shopping_item():
    data = request.get_json(silent=True)
    if not data or not data.get("id") or not data.get("name"):
        return jsonify({"error": "Missing required fields: id, name"}), 400
    if storage.get_shopping_item(data["id"]):
        return jsonify({"error": "Item already exists"}), 409
    item = storage.create_shopping_item(data)
    return jsonify(item), 201


@app.route("/shopping/<item_id>", methods=["PUT"])
@auth.require_auth
def update_shopping_item(item_id: str):
    data = request.get_json(silent=True)
    if not data:
        return jsonify({"error": "Missing body"}), 400
    data["id"] = item_id
    result, accepted = storage.update_shopping_item(item_id, data)
    if result is None:
        return jsonify({"error": "Item not found"}), 404
    if not accepted:
        return jsonify(result), 409
    return jsonify(result), 200


@app.route("/shopping/<item_id>", methods=["DELETE"])
@auth.require_auth
def delete_shopping_item(item_id: str):
    if not storage.delete_shopping_item(item_id):
        return jsonify({"error": "Item not found"}), 404
    return "", 204


# ---------------------------------------------------------------------------
# Categories
# ---------------------------------------------------------------------------

@app.route("/categories", methods=["GET"])
@auth.require_auth
def list_categories():
    return jsonify(storage.get_categories()), 200


@app.route("/categories", methods=["POST"])
@auth.require_auth
def create_category():
    data = request.get_json(silent=True) or {}
    name = data.get("name", "").strip()
    if not name:
        return jsonify({"error": "Missing name"}), 400
    if not storage.create_category(name):
        return jsonify({"error": "Category already exists"}), 409
    return jsonify({"name": name}), 201


@app.route("/categories/<path:name>", methods=["DELETE"])
@auth.require_auth
def delete_category(name: str):
    if not storage.delete_category(name):
        return jsonify({"error": "Category not found"}), 404
    return "", 204


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    host = os.environ.get("HOST", "0.0.0.0")
    port = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("FLASK_DEBUG", "false").lower() == "true"
    app.run(host=host, port=port, debug=debug)
