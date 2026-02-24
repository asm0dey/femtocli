package me.bechberger.femtocli.processor;

import javax.lang.model.element.Element;

/**
 * Exception thrown during annotation processing when validation fails.
 *
 * <p>This exception is used to report validation errors at compile time
 * with the offending element for proper error reporting.</p>
 */
public class ProcessingException extends Exception {

    private final Element element;

    /**
     * Creates a processing exception with the associated element.
     *
     * @param element The element that caused the error
     * @param message The error message
     */
    public ProcessingException(Element element, String message) {
        super(message);
        this.element = element;
    }

    /**
     * Returns the element that caused this processing exception.
     *
     * @return The element associated with this error
     */
    public Element getElement() {
        return element;
    }
}
