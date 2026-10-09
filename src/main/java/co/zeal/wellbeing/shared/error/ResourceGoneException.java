package co.zeal.wellbeing.shared.error;

/** El recurso existió pero ya no se puede usar (p. ej. un cupo liberado cuya ventana venció). */
public class ResourceGoneException extends DomainException {

    public ResourceGoneException(String message) {
        super(message);
    }
}
