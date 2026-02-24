package me.bechberger.femtocli.processor.model;

import me.bechberger.femtocli.annotations.Mixin;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model representing metadata for a @Mixin annotated field.
 *
 * <p>Mixins allow sharing options across multiple commands.</p>
 */
public class MixinModel {

    private final VariableElement element;
    private final Mixin annotation;
    private final TypeMirror mixinType;
    private final String fieldName;
    private final List<OptionModel> options = new ArrayList<>();

    /**
     * Creates a MixinModel from a @Mixin annotated field.
     */
    public MixinModel(VariableElement element, Mixin annotation) {
        this.element = element;
        this.annotation = annotation;
        this.mixinType = element.asType();
        this.fieldName = element.getSimpleName().toString();
    }

    /**
     * Returns the field element.
     */
    public VariableElement getElement() {
        return element;
    }

    /**
     * Returns the field name for the mixin.
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Returns the type of the mixin class.
     */
    public TypeMirror getMixinType() {
        return mixinType;
    }

    /**
     * Returns the simple name of the mixin class.
     */
    public String getMixinClassName() {
        String typeName = mixinType.toString();
        // Handle generic types by extracting the raw type
        int genericStart = typeName.indexOf('<');
        if (genericStart > 0) {
            typeName = typeName.substring(0, genericStart);
        }
        return typeName.replaceFirst(".*\\.", "");
    }

    /**
     * Returns the qualified name of the mixin class.
     */
    public String getMixinQualifiedName() {
        String typeName = mixinType.toString();
        // Handle generic types by extracting the raw type
        int genericStart = typeName.indexOf('<');
        if (genericStart > 0) {
            typeName = typeName.substring(0, genericStart);
        }
        return typeName;
    }

    /**
     * Returns all options inherited from this mixin.
     */
    public List<OptionModel> getOptions() {
        return Collections.unmodifiableList(options);
    }

    /**
     * Adds an option to this mixin.
     */
    public void addOption(OptionModel option) {
        options.add(option);
    }
}
