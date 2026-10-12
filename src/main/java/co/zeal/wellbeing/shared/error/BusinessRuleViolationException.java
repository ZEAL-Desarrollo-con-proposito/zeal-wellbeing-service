package co.zeal.wellbeing.shared.error;

/** La petición es inválida según una regla de negocio, independiente de la concurrencia. */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
