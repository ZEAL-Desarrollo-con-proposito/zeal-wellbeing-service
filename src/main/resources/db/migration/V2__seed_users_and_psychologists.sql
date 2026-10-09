-- Usuarios semilla del MVP (HU-01: el MVP no tiene registro). Contraseña de demo: Zeal2026*
-- Se guarda solo su hash BCrypt; nunca la contraseña en claro.
INSERT INTO user_accounts (id, email, password_hash, full_name, role, organization) VALUES
    ('usr-empleado-001',  'david.cajamarca@nexura.co', '$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'David Cajamarca', 'EMPLEADO',  'Nexura S.A.S.'),
    ('usr-empleado-002',  'mariana.torres@nexura.co',  '$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'Mariana Torres',  'EMPLEADO',  'Nexura S.A.S.'),
    ('usr-psicologo-001', 'laura.martinez@zeal.co',    '$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'Laura Martínez',  'PSICOLOGO', 'ZEAL'),
    ('usr-admin-001',     'talento.humano@nexura.co',  '$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'Talento Humano',  'ADMIN',     'Nexura S.A.S.');

-- Directorio de psicólogos (HU-04). Solo Laura tiene cuenta para iniciar sesión en la demo.
INSERT INTO psychologists (id, full_name, title) VALUES
    ('usr-psicologo-001', 'Laura Martínez', 'Psicóloga clínica'),
    ('usr-psicologo-002', 'Andrés Rojas',   'Psicólogo organizacional'),
    ('usr-psicologo-003', 'Valeria Castro', 'Psicóloga clínica'),
    ('usr-psicologo-004', 'Camila Duarte',  'Psicóloga de la salud');

INSERT INTO psychologist_focus_areas (psychologist_id, position, focus_area) VALUES
    ('usr-psicologo-001', 0, 'Ansiedad laboral'),
    ('usr-psicologo-001', 1, 'Burnout'),
    ('usr-psicologo-002', 0, 'Carrera'),
    ('usr-psicologo-002', 1, 'Burnout'),
    ('usr-psicologo-003', 0, 'Duelo'),
    ('usr-psicologo-003', 1, 'Ansiedad laboral'),
    ('usr-psicologo-004', 0, 'Sueño'),
    ('usr-psicologo-004', 1, 'Ansiedad laboral');
