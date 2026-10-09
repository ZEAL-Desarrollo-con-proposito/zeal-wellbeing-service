# zeal-wellbeing-service

Wellbeing Domain de ZEAL (puerto **8081**): autenticación, directorio de psicólogos, agenda,
citas, cupos liberados, salas de videollamada y chat privado. Es contenido clínico: el rol `ADMIN`
recibe `403` en todo `/api/wellbeing/**` y `/api/chats/**` (HU-11, RNF-08).

## Ejecutar

```bash
cd ../zeal-infra-compose && docker compose up -d   # PostgreSQL + Redis
./mvnw spring-boot:run
```

Flyway crea el esquema `wellbeing` y siembra los usuarios (contraseña `Zeal2026*`). Con
`DEMO_DATA_ENABLED=true` (por defecto) se siembra una agenda relativa al día de arranque.

| Variable | Por defecto |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/zeal_wellbeing_db` / `zeal_user` / `zeal_password` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `JWT_SECRET` | secreto de desarrollo (**cámbialo fuera de local**, igual en los 3 servicios) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` |
| `APP_TIME_ZONE` | `America/Bogota` |

## API

| Método | Ruta | Rol |
|---|---|---|
| POST | `/api/auth/login` | público |
| GET | `/api/wellbeing/psicologos`, `/{id}`, `/{id}/franjas?desde&hasta` | EMPLEADO |
| GET/POST/DELETE | `/api/wellbeing/franjas/mias?desde&hasta`, `/api/wellbeing/franjas`, `/{id}` | PSICOLOGO |
| GET | `/api/wellbeing/reservas/agenda?desde&hasta` | PSICOLOGO |
| GET/POST | `/api/wellbeing/reservas/mias`, `/api/wellbeing/reservas`, `/{id}/cancelar` | EMPLEADO |
| GET/POST | `/api/wellbeing/cupos`, `/api/wellbeing/cupos/{id}/tomar` | EMPLEADO |
| GET/POST | `/api/wellbeing/reservas/{id}/sala`, `/sala/activar` | participante / PSICOLOGO |
| GET/POST | `/api/chats/{empleado:psicologo}`, `/mensajes` | participante |

Errores: siempre `{ status, error, message, path, timestamp }`, con `message` legible.

## Concurrencia

- **C-2** (cero doble reserva): `SELECT … FOR UPDATE` sobre la franja + índice único parcial
  `uq_reservations_confirmed_slot` como segunda barrera.
- **C-3** (cupo liberado): la cancelación bloquea la cita y la franja, detecta candidatos y crea la
  oferta en la misma transacción; la toma bloquea oferta y franja (gana el primero, el resto 409).
- `lock_timeout = 5s`: bajo contención extrema se responde 409 reintentable, nunca se cuelga.

## Eventos (Redis Pub/Sub, JSON plano, publicados después del commit)

`zeal.wellbeing.slots`, `zeal.wellbeing.freed-slots.offered`, `zeal.wellbeing.freed-slots.taken`,
`zeal.wellbeing.rooms.updated`, `zeal.notifications`.

## Pruebas

`./mvnw verify` (requiere Docker: Testcontainers levanta PostgreSQL). Incluye las pruebas de carrera
C-2 (100 hilos) y C-3 (50 hilos).
