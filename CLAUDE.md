# RetailHub — памятка для Claude

## Где искать накопленные разборы

`docs/notes/` — заметки по темам проекта (архитектура, Kafka, и т.п.). Индекс: [docs/notes/README.md](docs/notes/README.md).

Если вопрос про архитектуру, взаимодействие сервисов, Kafka-топики, outbox, eventual consistency — сначала смотри [docs/notes/architecture-and-kafka.md](docs/notes/architecture-and-kafka.md).

## Где сервер

Прод-стенд: `root@83.147.255.205`. Docker-compose стек называется `retailhub-*`. Kafka — в контейнере `retailhub-kafka-1`, bootstrap `localhost:29092` изнутри контейнера.

## Стиль ответов

По-русски, простым языком, но технически точно. Для ссылок на код — markdown-формат `[filename.java](path/to/file.java)`.
