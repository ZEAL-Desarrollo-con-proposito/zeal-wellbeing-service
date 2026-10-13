-- Más usuarios semilla (contraseña de demo: Zeal2026*).
-- Nexura: Camilo y Sofía ya aparecen como pacientes en los datos de demostración; ahora pueden
-- iniciar sesión. Órbita Labs es una segunda empresa cliente con su administradora y 4 empleados:
-- sirve para demostrar el aislamiento entre empresas (no ve convocatorias, talleres ni personas
-- de Nexura).
INSERT INTO user_accounts (id, email, password_hash, full_name, role, organization) VALUES
    ('usr-empleado-003', 'camilo.rincon@nexura.co',       '$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'Camilo Rincón',          'EMPLEADO', 'Nexura S.A.S.'),
    ('usr-empleado-004', 'sofia.mejia@nexura.co',         '$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'Sofía Mejía',            'EMPLEADO', 'Nexura S.A.S.'),
    ('usr-admin-002',    'gestion.humana@orbitalabs.co',  '$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'Gestión Humana Órbita',  'ADMIN',    'Órbita Labs S.A.S.'),
    ('usr-empleado-101', 'andrea.gomez@orbitalabs.co',    '$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'Andrea Gómez',           'EMPLEADO', 'Órbita Labs S.A.S.'),
    ('usr-empleado-102', 'felipe.restrepo@orbitalabs.co', '$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'Felipe Restrepo',        'EMPLEADO', 'Órbita Labs S.A.S.'),
    ('usr-empleado-103', 'natalia.vargas@orbitalabs.co',  '$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'Natalia Vargas',         'EMPLEADO', 'Órbita Labs S.A.S.'),
    ('usr-empleado-104', 'santiago.herrera@orbitalabs.co','$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa', 'Santiago Herrera',       'EMPLEADO', 'Órbita Labs S.A.S.');
