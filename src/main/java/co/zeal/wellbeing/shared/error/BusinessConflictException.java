package co.zeal.wellbeing.shared.error;

/** La operación choca con el estado actual del recurso (p. ej. otra persona ganó la carrera). */
public class BusinessConflictException extends DomainException {

    public BusinessConflictException(String message) {
        super(message);
    }
}
