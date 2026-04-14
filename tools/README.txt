Деплой на основной VPS (Windows, PuTTY plink/pscp в PATH).

1) Задайте пароль SSH (не храните в репозитории):
   set RETAILHUB_SSH_PASSWORD=ваш_пароль

2) Из каталога diplom выполните:
   powershell -File .\retailhub-deploy.ps1

Переменные (необязательно):
  RETAILHUB_SSH_HOST   (по умолчанию 83.147.255.205)
  RETAILHUB_SSH_USER   (по умолчанию root)
  RETAILHUB_REMOTE_DIR (по умолчанию /opt/retailhub)

Скрипт упаковывает проект без каталогов target, заливает на сервер,
собирает store-service (Maven), пересобирает образы store-service и web-app,
поднимает контейнеры. В .env на сервере при отсутствии добавляется строка
QR_SCAN_BASE_URL=http://83.147.255.205

Проверка Kafka → auth: bash tools/verify_user_kafka_flow.sh (на сервере Linux,
предварительно pscp скрипта и sed -i 's/\r$//' для CRLF).

PNG QR через gateway (на сервере): bash tools/check_qr_png.sh [qr_code_uuid]
(логин менеджера +70001111111 / password; UUID из БД store_db.qr_codes).

Жизненный цикл заявки (на сервере, после прогрева JVM ~2 мин):
  bash tools/lifecycle_test_server.sh
(сид: QR-токен 5555…, консультант +70002222222 / password; правит request_db.replica_* при необходимости.)
