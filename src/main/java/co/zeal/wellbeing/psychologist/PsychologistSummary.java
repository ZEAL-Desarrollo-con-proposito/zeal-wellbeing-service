package co.zeal.wellbeing.psychologist;

/** Datos mínimos de un psicólogo que acompañan a una cita o a un cupo liberado. */
public record PsychologistSummary(String id, String fullName, String title) {

    public static PsychologistSummary of(Psychologist psychologist) {
        return new PsychologistSummary(psychologist.getId(), psychologist.getFullName(), psychologist.getTitle());
    }
}
