# Docker & Database Guide

## 🐳 Docker Setup

The project uses Docker Compose to run PostgreSQL 17 in a container.

### Commands

| Action | Command |
|---|---|
| **Start Database** | `docker-compose up -d` |
| **Stop Database** | `docker-compose down` |
| **View Logs** | `docker logs -f retailhub-db` |
| **Check Status** | `docker ps` |

---

## 🐘 PostgreSQL Connection

| Parameter | Value |
|---|---|
| **Host** | `localhost` |
| **Port** | `5434` (mapped from 5432) |
| **Database** | `retailhub` |
| **User** | `retailhub` |
| **Password** | `secret` |
| **JDBC URL** | `jdbc:postgresql://localhost:5434/retailhub` |

### Manual Connection (psql)

If you have PostgreSQL installed locally:
```powershell
$env:PGPASSWORD='secret'
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -h localhost -p 5434 -U retailhub -d retailhub
```

---

## 🛠 Troubleshooting

**1. Port Conflict (5432 is busy)**
We mapped container port `5432` to host port `5434` in `docker-compose.yml` because your local machine already has PostgreSQL running on 5432/5433.

**2. Resetting Database**
To wipe all data and start fresh:
```powershell
docker-compose down -v
docker-compose up -d
```
The app will automatically recreate schemas via Flyway on next startup.

**3. Flyway Migrations**
Migrations are in `app/src/main/resources/db/migration`.
If Flyway automation fails, you can apply them manually using `psql`.

---

## 🚀 Quick Database Access (Docker Exec)

To connect to the running database **directly from the container** (easiest way, no local psql needed):

```powershell
# 1. Enter the database container
docker exec -it retailhub-db psql -U retailhub -d retailhub

# You will see the prompt: retailhub=# 

# Useful SQL commands:
\dt             -- List all tables
\d users        -- Describe 'users' table
select * from users; 
\q              -- Quit
```

