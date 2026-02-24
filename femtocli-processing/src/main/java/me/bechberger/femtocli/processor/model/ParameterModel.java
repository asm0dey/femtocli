package me.bechberger.femtocli.processor.model;

import me.bechberger.femtocli.TypeConverter;
import me.bechberger.femtocli.Verifier;
import me.bechberger.femtocli.annotations.Parameters;
import me.bechberger.femtocli.processor.ProcessingException;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Model representing metadata for a single @Parameters annotated field.
 */
public class ParameterModel {

    private final VariableElement element;
    private final Parameters annotation;
    private final TypeMirror fieldType;

    /**
     * Creates a ParameterModel from a @Parameters annotated field.
     */
    public ParameterModel(VariableElement element, Parameters annotation) throws ProcessingException {
        this.element = element;
        this.annotation = annotation;
        this.fieldType = element.asType();

        validate();
    }

    /**
     * Validates the parameter configuration.
     */
    private void validate() throws ProcessingException {
        String index = getIndex();
        if (index == null || index.isEmpty()) {
            throw new ProcessingException(element, "@Parameters.index() cannot be empty");
        }

        // Validate that both converter and converterMethod are not specified simultaneously
        // We use the annotation's toString() which returns the FQN to avoid MirroredTypeException
        if (!isDefaultConverter() && !annotation.converterMethod().isEmpty()) {
            throw new ProcessingException(element,
                "Cannot specify both @Parameters.converter() and @Parameters.converterMethod()");
        }

        // Validate that both verifier and verifierMethod are not specified simultaneously
        if (!isDefaultVerifier() && !annotation.verifierMethod().isEmpty()) {
            throw new ProcessingException(element,
                "Cannot specify both @Parameters.verifier() and @Parameters.verifierMethod()");
        }
    }

    /**
     * Returns the field element.
     */
    public VariableElement getElement() {
        return element;
    }

    /**
     * Returns the field name.
     */
    public String getFieldName() {
        return element.getSimpleName().toString();
    }

    /**
     * Returns the field type.
     */
    public TypeMirror getFieldType() {
        return fieldType;
    }

    /**
     * Returns the simple class name of the field type.
     */
    public String getFieldTypeSimpleName() {
        return fieldType.toString().replaceFirst(".*\\.", "");
    }

    /**
     * Returns the index or index range for this parameter.
     */
    public String getIndex() {
        return annotation.index();
    }

    /**
     * Returns whether this is a single-index parameter (e.g., "0", "1").
     */
    public boolean isSingleIndex() {
        String index = getIndex();
        return !index.contains("..");
    }

    /**
     * Returns the single index value if applicable, or -1 if range.
     */
    public int getSingleIndex() {
        if (!isSingleIndex()) {
            return -1;
        }
        try {
            return Integer.parseInt(getIndex());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Returns whether this is a range parameter (e.g., "0..1", "1..*").
     */
    public boolean isRange() {
        return getIndex().contains("..");
    }

    /**
     * Returns the start index for range parameters.
     */
    public int getRangeStart() {
        if (!isRange()) {
            return -1;
        }
        String index = getIndex();
        int dotDot = index.indexOf("..");
        String startStr = index.substring(0, dotDot);
        try {
            return Integer.parseInt(startStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Returns the end index for range parameters, or -1 for unbounded.
     */
    public int getRangeEnd() {
        if (!isRange()) {
            return -1;
        }
        String index = getIndex();
        int dotDot = index.indexOf("..");
        String endStr = index.substring(dotDot + 2);
        if ("*".equals(endStr)) {
            return -1; // Unbounded
        }
        try {
            return Integer.parseInt(endStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Returns whether this is an unbounded range (e.g., "0..*", "1..*").
     */
    public boolean isUnbounded() {
        return isRange() && getIndex().endsWith("*");
    }

    /**
     * Returns whether this is a varargs parameter.
     */
    public boolean isVarargs() {
        return isUnbounded() || (isRange() && getRangeEnd() > getRangeStart());
    }

    /**
     * Returns the arity specification.
     */
    public String getArity() {
        return annotation.arity();
    }

    /**
     * Returns the description.
     */
    public String getDescription() {
        return annotation.description();
    }

    /**
     * Returns the parameter label for help text.
     */
    public String getParamLabel() {
        return annotation.paramLabel();
    }

    /**
     * Returns the default value.
     */
    public String getDefaultValue() {
        return annotation.defaultValue();
    }

    /**
     * Returns whether a default value is set.
     */
    public boolean hasDefaultValue() {
        String defaultValue = getDefaultValue();
        return defaultValue != null && !defaultValue.equals("__NO_DEFAULT_VALUE__");
    }

    /**
     * Returns the custom converter class.
     */
    public Class<? extends TypeConverter<?>> getConverterClass() {
        return annotation.converter();
    }

    /**
     * Returns whether the default converter is used.
     */
    public boolean isDefaultConverter() {
        // In annotation processing, we compare the type mirror's toString() which returns the FQN
        // We wrap in try-catch to handle MirroredTypeException during compilation
        try {
            String converterFqn = annotation.converter().toString();
            return converterFqn.equals("me.bechberger.femtocli.TypeConverter.NullTypeConverter") ||
                   converterFqn.equals("me.bechberger.femtocli.TypeConverter$NullTypeConverter");
        } catch (javax.lang.model.type.MirroredTypeException e) {
            String converterFqn = e.getTypeMirror().toString();
            return converterFqn.equals("me.bechberger.femtocli.TypeConverter.NullTypeConverter") ||
                   converterFqn.equals("me.bechberger.femtocli.TypeConverter$NullTypeConverter");
        } catch (Exception e) {
            // Fallback to assuming default if we can't access the value
            return true;
        }
    }

    /**
     * Returns the converter method name.
     */
    public String getConverterMethod() {
        return annotation.converterMethod();
    }

    /**
     * Returns whether a custom converter is configured.
     */
    public boolean hasCustomConverter() {
        return !isDefaultConverter() || !getConverterMethod().isEmpty();
    }

    /**
     * Returns the custom verifier class.
     */
    public Class<? extends Verifier<?>> getVerifierClass() {
        return annotation.verifier();
    }

    /**
     * Returns whether the default verifier is used.
     */
    public boolean isDefaultVerifier() {
        // In annotation processing, we compare the type mirror's toString() which returns the FQN
        // We wrap in try-catch to handle MirroredTypeException during compilation
        try {
            String verifierFqn = annotation.verifier().toString();
            return verifierFqn.equals("me.bechberger.femtocli.Verifier.NullVerifier") ||
                   verifierFqn.equals("me.bechberger.femtocli.Verifier$NullVerifier");
        } catch (javax.lang.model.type.MirroredTypeException e) {
            String verifierFqn = e.getTypeMirror().toString();
            return verifierFqn.equals("me.bechberger.femtocli.Verifier.NullVerifier") ||
                   verifierFqn.equals("me.bechberger.femtocli.Verifier$NullVerifier");
        } catch (Exception e) {
            // Fallback to assuming default if we can't access the value
            return true;
        }
    }

    /**
     * Returns the verifier method name.
     */
    public String getVerifierMethod() {
        return annotation.verifierMethod();
    }

    /**
     * Returns whether a custom verifier is configured.
     */
    public boolean hasCustomVerifier() {
        return !isDefaultVerifier() || !getVerifierMethod().isEmpty();
    }

    /**
     * Returns the fully-qualified converter class name, even during annotation processing.
     */
    public String getConverterClassName() {
        try {
            return annotation.converter().getName();
        } catch (javax.lang.model.type.MirroredTypeException e) {
            return e.getTypeMirror().toString();
        }
    }

    /**
     * Returns the fully-qualified verifier class name, even during annotation processing.
     */
    public String getVerifierClassName() {
        try {
            return annotation.verifier().getName();
        } catch (javax.lang.model.type.MirroredTypeException e) {
            return e.getTypeMirror().toString();
        }
    }
}
