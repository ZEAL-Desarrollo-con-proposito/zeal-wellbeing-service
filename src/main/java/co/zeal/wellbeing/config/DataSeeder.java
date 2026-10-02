package co.zeal.wellbeing.config;

import co.zeal.wellbeing.psychologist.Psychologist;
import co.zeal.wellbeing.psychologist.PsychologistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final PsychologistRepository psychologistRepository;

    public DataSeeder(PsychologistRepository psychologistRepository) {
        this.psychologistRepository = psychologistRepository;
    }

    @Override
    public void run(String... args) {
        if (psychologistRepository.count() > 0) {
            log.info("[DataSeeder] Datos semilla ya existen, omitiendo.");
            return;
        }
        log.info("[DataSeeder] Insertando datos semilla...");
        psychologistRepository.saveAll(List.of(
                Psychologist.builder()
                        .id("usr-psicologo-001")
                        .fullName("Laura Martínez")
                        .title("Psicóloga Clínica")
                        .focusAreas(List.of("Estrés laboral", "Ansiedad", "Bienestar emocional"))
                        .build()
        ));
        log.info("[DataSeeder] Semilla insertada: 1 psicólogo.");
    }
}
