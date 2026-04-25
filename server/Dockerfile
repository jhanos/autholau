FROM python:alpine

RUN apk add --update uv

COPY . /app

WORKDIR /app

EXPOSE 5000

ENTRYPOINT ["uv", "run", "app.py"]
