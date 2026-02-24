package me.bechberger.femtocli.processor.codegen;


import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates field tracking and state management for parser classes.
 * <p>
 * This class adds fields for tracking parsed options, collecting multi-value
 * option values, storing positional arguments, and tracking parser state.
 */
public final class FieldTrackingGenerator {

    private FieldTrackingGenerator() {
        // Utility class
    }

    /**
     * Adds tracking fields for option and parameter parsing.
     *
     * @param classBuilder  the class builder to add fields to
     * @param hasParameters whether the command has positional parameters
     * @param hasOptions    whether the command has options
     * @param hasMultiValue whether the command has multi-value options
     */
    public static void addOptionTrackingFields(TypeSpec.Builder classBuilder, boolean hasParameters, boolean hasOptions, boolean hasMultiValue) {
        if (hasOptions) {
            // Field to track which options were explicitly provided
            classBuilder.addField(FieldSpec.builder(
                            ParameterizedTypeName.get(Set.class, String.class),
                            "parsedOptions"
                    )
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .initializer("new $T<>()", LinkedHashSet.class)
                    .build());
        }

        if (hasMultiValue) {
            // Field to accumulate multi-value option values
            classBuilder.addField(FieldSpec.builder(
                            ParameterizedTypeName.get(
                                    ClassName.get(LinkedHashMap.class),
                                    ClassName.get(String.class),
                                    ParameterizedTypeName.get(ClassName.get(ArrayList.class), ClassName.get(String.class))
                            ),
                            "multiValueAccumulator"
                    )
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .initializer("new $T<>()", LinkedHashMap.class)
                    .build());
        }

        if (hasParameters) {
            // Field to collect positional arguments
            classBuilder.addField(FieldSpec.builder(
                            ParameterizedTypeName.get(ArrayList.class, String.class),
                            "positionalArgs"
                    )
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .initializer("new $T<>()", ArrayList.class)
                    .build());
        }

        if (hasOptions && hasParameters) {
            // Field to track if we've seen a positional argument
            classBuilder.addField(FieldSpec.builder(
                            boolean.class,
                            "seenPositional"
                    )
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .initializer("false")
                    .build());
        }

        if (hasOptions || hasParameters) {
            // Field to track if we've seen "--" end-of-options marker
            classBuilder.addField(FieldSpec.builder(
                            boolean.class,
                            "afterEndOfOptions"
                    )
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .initializer("false")
                    .build());
        }
    }
}
