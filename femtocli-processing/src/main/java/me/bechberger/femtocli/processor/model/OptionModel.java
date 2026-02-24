package me.bechberger.femtocli.processor.model;

import me.bechberger.femtocli.TypeConverter;
import me.bechberger.femtocli.Verifier;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.processor.ProcessingException;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;

/**
 * Model representing metadata for a single @Option annotated field.
 */
public class OptionModel {

    private final VariableElement element;
    private final Option annotation;
    private final String[] names;
    private final TypeMirror fieldType;
    private String mixinFieldName;
    private boolean fromMixin = false;

    /**
     * Creates an OptionModel from a @Option annotated field.
     */
    public OptionModel(VariableElement element, Option annotation) throws ProcessingException {
        this.element = element;
        this.annotation = annotation;
        this.names = annotation.names();
        this.fieldType = element.asType();
        this.mixinFieldName = null;
        this.fromMixin = false;

        validate();
    }

    /**
     * Creates an OptionModel from a @Option annotated field from a mixin.
     */
    public OptionModel(VariableElement element, Option annotation, String mixinFieldName) throws ProcessingException {
        this.element = element;
        this.annotation = annotation;
        this.names = annotation.names();
        this.fieldType = element.asType();
        this.mixinFieldName = mixinFieldName;
        this.fromMixin = true;

        validate();
    }

    /**
     * Validates the option configuration.
     */
    private void validate() throws ProcessingException {
        if (names == null || names.length == 0) {
            throw new ProcessingException(element, "@Option.names() cannot be empty");
        }

        // Validate short names (must be single character)
        for (String name : names) {
            if (name.startsWith("-") && !name.startsWith("--")) {
                if (name.length() > 2) {
                    throw new ProcessingException(element,
                        "Short option '" + name + "' must be a single character (e.g., '-o')");
                }
            } else if (!name.startsWith("-")) {
                throw new ProcessingException(element,
                        "Option name '" + name + "' must start with '-' or '--'");
            }
        }

        // Validate that both converter and converterMethod are not specified simultaneously
        // We use the annotation's toString() which returns the FQN to avoid MirroredTypeException
        if (!isDefaultConverter() && !annotation.converterMethod().isEmpty()) {
            throw new ProcessingException(element,
                "Cannot specify both @Option.converter() and @Option.converterMethod()");
        }

        // Validate that both verifier and verifierMethod are not specified simultaneously
        if (!isDefaultVerifier() && !annotation.verifierMethod().isEmpty()) {
            throw new ProcessingException(element,
                "Cannot specify both @Option.verifier() and @Option.verifierMethod()");
        }

        // Validate that split is only used with collection/array types
        if (hasSplit()) {
            String type = fieldType.toString();
            boolean isCollection = type.contains("java.util.List") ||
                               type.contains("java.util.Set") ||
                               type.contains("java.util.ArrayList") ||
                               type.endsWith("[]") ||
                               type.startsWith("[L") ||
                               type.startsWith("[");
            if (!isCollection) {
                throw new ProcessingException(element,
                    "@Option.split() can only be used with List, Set, or array types, but field '" +
                    getFieldName() + "' has type: " + type);
            }
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
     * Returns all option names.
     */
    public String[] getNames() {
        return names;
    }

    /**
     * Returns the primary name (first in the list, usually the long name).
     */
    public String getPrimaryName() {
        return names[0];
    }

    /**
     * Returns the long name if present (starts with --).
     */
    public String getLongName() {
        for (String name : names) {
            if (name.startsWith("--")) {
                return name;
            }
        }
        return null;
    }

    /**
     * Returns the short name if present (single dash, single character).
     */
    public String getShortName() {
        for (String name : names) {
            if (name.startsWith("-") && !name.startsWith("--")) {
                return name;
            }
        }
        return null;
    }

    /**
     * Returns the option description.
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
     * Returns whether this option is required.
     */
    public boolean isRequired() {
        return annotation.required();
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
        return defaultValue != null && !defaultValue.isEmpty() && !defaultValue.equals("__NO_DEFAULT_VALUE__");
    }

    /**
     * Returns the split delimiter.
     */
    public String getSplit() {
        return annotation.split();
    }

    /**
     * Returns whether split is configured.
     */
    public boolean hasSplit() {
        String split = getSplit();
        return split != null && !split.isEmpty();
    }

    /**
     * Returns the arity.
     */
    public String getArity() {
        return annotation.arity();
    }

    /**
     * Returns whether this option is hidden from help.
     */
    public boolean isHidden() {
        return annotation.hidden();
    }

    /**
     * Returns whether to show default value in help.
     */
    public boolean isShowDefaultValueInHelp() {
        return annotation.showDefaultValueInHelp();
    }

    /**
     * Returns whether to put default value on a new line in help.
     */
    public boolean isDefaultValueOnNewLine() {
        return annotation.defaultValueOnNewLine();
    }

    /**
     * Returns the custom default value help template.
     */
    public String getDefaultValueHelpTemplate() {
        return annotation.defaultValueHelpTemplate();
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

    /**
     * Returns whether to show enum descriptions.
     */
    public boolean isShowEnumDescriptions() {
        return annotation.showEnumDescriptions();
    }

    /**
     * Returns the list of names as an unmodifiable list.
     */
    public List<String> getNamesList() {
        return Collections.unmodifiableList(List.of(names));
    }

    /**
     * Returns the mixin field name if this option is from a mixin.
     */
    public String getMixinFieldName() {
        return mixinFieldName;
    }

    /**
     * Returns whether this option is from a mixin.
     */
    public boolean isFromMixin() {
        return fromMixin;
    }
}
