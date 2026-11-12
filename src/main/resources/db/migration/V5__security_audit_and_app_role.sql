-- R2-14 · Auditoría de seguridad de SOLO INSERCIÓN y separación de usuarios de la base.
--
-- security_audit: inicios de sesión, reutilización de refresh tokens (SEG-4) y accesos ajenos (SEG-3).
-- Nunca guarda correos, nombres ni contenido: solo el id interno, la IP, la hora y el correlationId.
CREATE TABLE security_audit (
    id             BIGSERIAL    PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    service        VARCHAR(40)  NOT NULL,
    event_type     VARCHAR(40)  NOT NULL,
    outcome        VARCHAR(12)  NOT NULL CHECK (outcome IN ('EXITO', 'FALLO', 'DENEGADO')),
    user_id        VARCHAR(64),
    ip             VARCHAR(45),
    correlation_id VARCHAR(64),
    detail         VARCHAR(300)
);
CREATE INDEX ix_security_audit_occurred_at ON security_audit (occurred_at);
CREATE INDEX ix_security_audit_user ON security_audit (user_id, occurred_at);

-- Ni la aplicación ni el propietario pueden modificar o borrar registros: el trigger lo impide para
-- cualquier usuario (deshabilitarlo exige ALTER TABLE, que la aplicación no tiene).
CREATE FUNCTION security_audit_is_append_only() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'security_audit es de solo inserción: % no está permitido', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END
$$;
CREATE TRIGGER security_audit_no_update_delete
    BEFORE UPDATE OR DELETE ON security_audit
    FOR EACH ROW EXECUTE FUNCTION security_audit_is_append_only();
CREATE TRIGGER security_audit_no_truncate
    BEFORE TRUNCATE ON security_audit
    FOR EACH STATEMENT EXECUTE FUNCTION security_audit_is_append_only();

-- Usuario de la APLICACIÓN (placeholder appUser = DB_USERNAME), distinto del propietario que corre
-- estas migraciones (SPRING_FLYWAY_USER). Si son el mismo (desarrollo en el host) no se hace nada.
-- Si el rol no existe y el propietario puede crearlo, se crea; en el nodo de datos lo crea
-- postgres/init/01-app-role.sh. Las tablas futuras heredan los permisos (ALTER DEFAULT PRIVILEGES).
DO $$
DECLARE
    app_user TEXT := '${appUser}';
BEGIN
    IF app_user = current_user THEN
        RAISE NOTICE 'La aplicación usa el mismo usuario que las migraciones (%): sin separación', app_user;
        RETURN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = app_user) THEN
        IF (SELECT rolsuper OR rolcreaterole FROM pg_roles WHERE rolname = current_user) THEN
            EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', app_user, '${appPassword}');
        ELSE
            RAISE EXCEPTION 'No existe el rol de aplicación % y % no puede crearlo', app_user, current_user;
        END IF;
    END IF;
    EXECUTE format('GRANT USAGE ON SCHEMA wellbeing TO %I', app_user);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA wellbeing TO %I', app_user);
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA wellbeing TO %I', app_user);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA wellbeing GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', app_user);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA wellbeing GRANT USAGE, SELECT ON SEQUENCES TO %I', app_user);
    -- Auditoría: solo insertar y leer.
    EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON security_audit FROM %I', app_user);
    -- El historial de migraciones no es asunto de la aplicación.
    EXECUTE format('REVOKE ALL ON flyway_schema_history FROM %I', app_user);
END
$$;
