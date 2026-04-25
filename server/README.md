# Autholau Server

## Setup

```bash
cd server
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

## Configuration

Copy `.env.example` to `.env` and set values:

```bash
cp .env.example .env
```

## Run

```bash
python app.py
```

Or with gunicorn for production:

```bash
gunicorn -w 2 -b 0.0.0.0:5000 app:app
```
