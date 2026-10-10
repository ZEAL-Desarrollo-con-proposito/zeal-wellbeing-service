# zeal-wellbeing-service
Desarrollo profesional, retos internos y capacitaciones (Business Domain).

## Directorio y reservas

- `GET /api/wellbeing/psicologos` devuelve los psicólogos y su próximo bloque disponible; los bloques reservados no se consideran libres.
- `GET /api/wellbeing/psicologos/{id}/franjas?desde=YYYY-MM-DD&hasta=YYYY-MM-DD` devuelve las franjas del rango de fechas inclusivo. Las fechas se interpretan en UTC y la respuesta incluye el estado para que el cliente pueda mostrar los bloques ocupados.
- `POST /api/wellbeing/reservas` recibe `{"slotId":"..."}`. Reservar una franja ya tomada devuelve `409 Conflict`.

Las reservas se ejecutan con aislamiento `SERIALIZABLE` y bloqueo pesimista de la franja. La tabla `reservations` impone unicidad para las reservas activas de cada franja; cancelar libera esa clave para permitir una reserva posterior. Tras confirmar la reserva, el servicio publica la franja actualizada en Redis mediante `events.slots`, canal que el Real-Time Gateway difunde por `/topic/franjas`.
