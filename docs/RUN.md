# How to Run RetailHub

## Prerequisites
- Java 25
- Docker & Docker Compose
- Maven (included wrapper `mvnw`)

## 1. Start Database
```powershell
docker-compose up -d
```

## 2. Build Project (Clean Build)
```powershell
# Windows PowerShell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'; .\mvnw.cmd clean package -DskipTests
```

## 3. Run Application
```powershell
# Windows PowerShell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'; java -jar app/target/app-0.0.1-SNAPSHOT.jar
```
The application will start at `http://localhost:8087`.
Swagger UI: `http://localhost:8087/swagger-ui.html`

## 4. Verify Authorization
To check if login works (and get a token):
```powershell
curl -X POST "http://localhost:8087/auth/login" `
     -H "Content-Type: application/json" `
     -d '{ "phone_number": "+79990000000", "password": "password" }'
```
Note: Use `phone_number` (snake_case), not `phoneNumber`.

## Troubleshooting

### "Missing Table" Error / Database Empty
If Flyway fails to run automatically, you can force migration manually:
```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'; .\mvnw.cmd -pl app flyway:migrate `
  -Dflyway.url=jdbc:postgresql://localhost:5434/retailhub `
  -Dflyway.user=retailhub `
  -Dflyway.password=secret `
  -Dflyway.locations=filesystem:app/src/main/resources/db/migration
```

### Access Database
```powershell
docker exec -it retailhub-db psql -U retailhub -d retailhub
```
Commands:
- `\dt` - List tables
- `SELECT * FROM users;` - Check users
