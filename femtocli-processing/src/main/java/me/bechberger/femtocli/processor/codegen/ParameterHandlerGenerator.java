package me.bechberger.femtocli.processor.codegen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;
import me.bechberger.femtocli.ParseException;
import me.bechberger.femtocli.processor.model.CommandModel;
import me.bechberger.femtocli.processor.model.ParameterModel;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates parameter handling methods for parser classes.
 * <p>
 * This class generates methods for handling positional parameters including
 * getting parameter index, checking if a parameter is varargs, getting range info,
 * and setting parameter values.
 */
public class ParameterHandlerGenerator {

    private final CommandModel model;

    public ParameterHandlerGenerator(CommandModel model) {
        this.model = model;
    }

    /**
     * Adds methods for handling positional parameters.
     */
    public void addParameterHandlingMethods(TypeSpec.Builder classBuilder) {
        // Add method to get parameter index for a field
        addParameterIndexMethod(classBuilder);

        // Add method to get parameter type
        addParameterTypeMethod(classBuilder);

        // Add method to get parameter range info
        addParameterRangeMethod(classBuilder);

        // Add method to set parameter value
        addSetParameterValueMethod(classBuilder);

        // Add method for setting multiple positional parameter values
        addSetParameterValuesMethod(classBuilder);
    }

    /**
     * Adds method to map field name to parameter index.
     */
    private void addParameterIndexMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("getParameterIndex")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(int.class)
                .addParameter(String.class, "fieldName");

        method.beginControlFlow("switch (fieldName)");

        for (ParameterModel parameter : model.getParameters()) {
            if (parameter.isSingleIndex()) {
                method.addCode("case \"$L\": return $L;\n", parameter.getFieldName(), parameter.getSingleIndex());
            }
        }

        method.addStatement("default: return -1");
        method.endControlFlow();

        classBuilder.addMethod(method.build());
    }

    /**
     * Adds method to check if a field is a varargs parameter.
     */
    private void addParameterTypeMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("isVarargsParameter")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(boolean.class)
                .addParameter(String.class, "fieldName");

        method.beginControlFlow("switch (fieldName)");

        for (ParameterModel parameter : model.getParameters()) {
            if (parameter.isVarargs()) {
                method.addCode("case \"$L\": return true;\n", parameter.getFieldName());
            }
        }

        method.addStatement("default: return false");
        method.endControlFlow();

        classBuilder.addMethod(method.build());
    }

    /**
     * Adds method to get parameter range information.
     */
    private void addParameterRangeMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("getParameterRange")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(String.class)
                .addParameter(String.class, "fieldName");

        method.beginControlFlow("switch (fieldName)");

        for (ParameterModel parameter : model.getParameters()) {
            String indexSpec = parameter.getIndex().replace("\\", "\\\\");
            method.addCode("case \"$L\": return \"$L\";\n", parameter.getFieldName(), indexSpec);
        }

        method.addStatement("default: return \"0\"");
        method.endControlFlow();

        classBuilder.addMethod(method.build());
    }

    /**
     * Adds method to set a positional parameter value.
     */
    private void addSetParameterValueMethod(TypeSpec.Builder classBuilder) {
        ClassName commandClassName = ClassName.bestGuess(model.getQualifiedName());
        MethodSpec.Builder method = MethodSpec.methodBuilder("setParameterValue")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(void.class)
                .addParameter(commandClassName, "command")
                .addParameter(String.class, "fieldName")
                .addParameter(String.class, "value")
                .addException(ParseException.class);

        method.beginControlFlow("switch (fieldName)");

        Set<String> processedFields = new LinkedHashSet<>();
        for (ParameterModel parameter : model.getParameters()) {
            String fieldName = parameter.getFieldName();
            if (processedFields.contains(fieldName)) {
                continue;
            }
            processedFields.add(fieldName);

            String fieldType = parameter.getFieldType().toString();

            method.addCode("case \"$L\":\n", fieldName);

            if (fieldType.startsWith("java.util.List") || fieldType.startsWith("List")) {
                method.addStatement("throw new $T(\"Cannot set multiple values for field \" + fieldName + \" via setParameterValue\")",
                        ParseException.class);
            } else {
                String targetAccess = "command." + fieldName;
                OptionValueGenerator.addConvertedValueAssignment(method, targetAccess, "value", fieldType,
                        parameter.isDefaultConverter() ? null : parameter.getConverterClassName(), parameter.getConverterMethod(),
                        parameter.isDefaultVerifier() ? null : parameter.getVerifierClassName(), parameter.getVerifierMethod(), "command");
                method.addCode("break;\n");
            }
        }

        method.endControlFlow();

        classBuilder.addMethod(method.build());
    }

    /**
     * Adds method for setting multiple positional parameter values.
     */
    private void addSetParameterValuesMethod(TypeSpec.Builder classBuilder) {
        ClassName commandClassName = ClassName.bestGuess(model.getQualifiedName());
        MethodSpec.Builder multiMethod = MethodSpec.methodBuilder("setParameterValues")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(void.class)
                .addParameter(commandClassName, "command")
                .addParameter(String.class, "fieldName")
                .addParameter(ParameterizedTypeName.get(List.class, String.class), "values")
                .addException(ParseException.class);

        multiMethod.beginControlFlow("switch (fieldName)");

        Set<String> processedFields = new LinkedHashSet<>();
        for (ParameterModel parameter : model.getParameters()) {
            String fieldName = parameter.getFieldName();
            if (processedFields.contains(fieldName)) {
                continue;
            }
            processedFields.add(fieldName);

            String fieldType = parameter.getFieldType().toString();

            multiMethod.addCode("case \"$L\":\n", fieldName);

            if (fieldType.startsWith("java.util.List") || fieldType.startsWith("List")) {
                multiMethod.addStatement("$T<Object> tmp = new $T<>()", ArrayList.class, ArrayList.class);
                multiMethod.beginControlFlow("for (String v : values)");
                OptionValueGenerator.addConvertedValueAssignment(multiMethod, "tmp::add", "v", "java.lang.Object",
                        parameter.isDefaultConverter() ? null : parameter.getConverterClassName(), parameter.getConverterMethod(),
                        parameter.isDefaultVerifier() ? null : parameter.getVerifierClassName(), parameter.getVerifierMethod(), "command");
                multiMethod.endControlFlow();
                multiMethod.addStatement("command.$L = (java.util.List) tmp", fieldName);
            } else {
                // If the field is not a list, but we have multiple values, use the first one if present
                multiMethod.beginControlFlow("if (!values.isEmpty())");
                multiMethod.addStatement("setParameterValue(command, fieldName, values.get(0))");
                multiMethod.endControlFlow();
            }
            multiMethod.addCode("break;\n");
        }

        multiMethod.endControlFlow();

        classBuilder.addMethod(multiMethod.build());
    }

    /**
     * Adds a type conversion statement for the given field type.
     */
    private void addTypeConversionStatement(MethodSpec.Builder method, String fieldType, String fieldName) {
        OptionValueGenerator.converterStatement(method, fieldName, fieldType);
    }
}
