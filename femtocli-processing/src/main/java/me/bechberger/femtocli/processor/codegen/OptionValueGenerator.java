package me.bechberger.femtocli.processor.codegen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import me.bechberger.femtocli.ParseException;
import me.bechberger.femtocli.processor.model.CommandModel;
import me.bechberger.femtocli.processor.model.OptionModel;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates the setOptionValue helper method for parser classes.
 * <p>
 * This class generates the method that sets option values on the command object
 * based on the field name, handling type conversion and multi-value options.
 */
public final class OptionValueGenerator {

    private OptionValueGenerator() {
        // Utility class
    }

    /**
     * Generates the setOptionValue helper method.
     *
     * @param model         the command model
     * @param classBuilder  the class builder to add method to
     * @param hasMultiValue whether the command has multi-value options
     */
    public static void addOptionValueMethod(CommandModel model, TypeSpec.Builder classBuilder, boolean hasMultiValue) {
        ClassName commandClassName = ClassName.bestGuess(model.getQualifiedName());
        MethodSpec.Builder setOptionMethod = MethodSpec.methodBuilder("setOptionValue")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(void.class)
                .addParameter(commandClassName, "command")
                .addParameter(String.class, "optionName")
                .addParameter(String.class, "value")
                .addException(ParseException.class);

        // Get field name for option
        setOptionMethod.addStatement("String fieldName = getFieldNameForOption(optionName)");
        setOptionMethod.beginControlFlow("if (fieldName == null)");
        setOptionMethod.addStatement("throw $T.unknownOption(optionName)", ParseException.class);
        setOptionMethod.endControlFlow();

        // Track that this option was provided
        setOptionMethod.addStatement("parsedOptions.add(fieldName)");

        if (hasMultiValue) {
            // Handle multi-value options (with split delimiter)
            setOptionMethod.beginControlFlow("if (isMultiValueOption(optionName))");
            setOptionMethod.addStatement("String delimiter = getSplitDelimiter(optionName)");
            setOptionMethod.beginControlFlow("if (!delimiter.isEmpty())");
            setOptionMethod.addStatement("String[] splitValues = value.split(delimiter)");
            setOptionMethod.addStatement("$T<$T> accumulator = multiValueAccumulator.computeIfAbsent(fieldName, k -> new $T<>())", 
                    ArrayList.class, String.class, ArrayList.class);
            setOptionMethod.beginControlFlow("for (String v : splitValues)");
            setOptionMethod.addStatement("accumulator.add(v)");
            setOptionMethod.endControlFlow();
            setOptionMethod.nextControlFlow("else");
            setOptionMethod.addStatement("$T<$T> accumulator = multiValueAccumulator.computeIfAbsent(fieldName, k -> new $T<>())", 
                    ArrayList.class, String.class, ArrayList.class);
            setOptionMethod.addStatement("accumulator.add(value)");
            setOptionMethod.endControlFlow();
            setOptionMethod.addStatement("return");
            setOptionMethod.endControlFlow();
        }

        // Generate switch for setting field values based on field name
        setOptionMethod.beginControlFlow("switch (fieldName)");

        Set<String> processedFields = new LinkedHashSet<>();
        for (OptionModel option : model.getAllOptions()) {
            String fieldName = option.getFieldName();
            if (processedFields.contains(fieldName)) {
                continue;
            }
            // Skip multi-value options - they are handled separately
            if (option.hasSplit()) {
                processedFields.add(fieldName);
                continue;
            }
            processedFields.add(fieldName);

            String fieldType = option.getFieldType().toString();

            // Get the field access expression (accounting for mixin field names)
            String fieldAccess = getFieldAccessExpression(fieldName, option.getMixinFieldName());

            // Generate case for setting this field
            setOptionMethod.addCode("case \"$L\":\n", fieldName);

            // Ensure mixin is initialized if needed
            ParseMethodGenerator.initializeMixinIfNeeded(setOptionMethod, model, option);

            addConvertedValueAssignment(setOptionMethod, fieldAccess, "value", fieldType,
                    option.getConverterClassName(), option.getConverterMethod(),
                    option.getVerifierClassName(), option.getVerifierMethod(), "command");

            setOptionMethod.addCode("break;\n");
        }

        setOptionMethod.endControlFlow();

        classBuilder.addMethod(setOptionMethod.build());
    }

    static void converterStatement(MethodSpec.Builder setOptionMethod, String fieldName, String fieldType) {
        switch (fieldType) {
            case "boolean", "java.lang.Boolean" ->
                setOptionMethod.addStatement("command.$L = convertBoolean(value)", fieldName);
            case "int", "java.lang.Integer" ->
                setOptionMethod.addStatement("command.$L = convertInt(value)", fieldName);
            case "long", "java.lang.Long" ->
                setOptionMethod.addStatement("command.$L = convertLong(value)", fieldName);
            case "float", "java.lang.Float" ->
                setOptionMethod.addStatement("command.$L = convertFloat(value)", fieldName);
            case "double", "java.lang.Double" ->
                setOptionMethod.addStatement("command.$L = convertDouble(value)", fieldName);
            default ->
                setOptionMethod.addStatement("command.$L = value", fieldName);
        }
    }

    /**
     * Generates a statement that converts and verifies a value for a given option/parameter.
     */
    public static void addConvertedValueAssignment(MethodSpec.Builder builder, String targetAccess, String rawValue,
                                                    String fieldType, String converterClassName, String converterMethod,
                                                    String verifierClassName, String verifierMethod, String commandVar) {
        builder.beginControlFlow("");
        String convertedVar = "convertedValue";
        builder.addStatement("Object $L", convertedVar);
        builder.beginControlFlow("try");

        // Conversion logic
        if (converterClassName != null && !converterClassName.equals("me.bechberger.femtocli.TypeConverter.NullTypeConverter")
                && !converterClassName.equals("me.bechberger.femtocli.TypeConverter$NullTypeConverter")) {
            builder.addStatement("$L = new $L().convert($L)", convertedVar, converterClassName, rawValue);
        } else if (converterMethod != null && !converterMethod.isEmpty()) {
            if (converterMethod.contains("#")) {
                String[] parts = converterMethod.split("#", 2);
                builder.addStatement("$L = $L.$L($L)", convertedVar, parts[0], parts[1], rawValue);
            } else {
                builder.addStatement("$L = $L.$L($L)", convertedVar, commandVar, converterMethod, rawValue);
            }
        } else {
            // Built-in conversions
            switch (fieldType) {
                case "boolean", "java.lang.Boolean" -> builder.addStatement("$L = convertBoolean($L)", convertedVar, rawValue);
                case "int", "java.lang.Integer" -> builder.addStatement("$L = convertInt($L)", convertedVar, rawValue);
                case "long", "java.lang.Long" -> builder.addStatement("$L = convertLong($L)", convertedVar, rawValue);
                case "float", "java.lang.Float" -> builder.addStatement("$L = convertFloat($L)", convertedVar, rawValue);
                case "double", "java.lang.Double" -> builder.addStatement("$L = convertDouble($L)", convertedVar, rawValue);
                default -> builder.addStatement("$L = $L", convertedVar, rawValue);
            }
        }

        // Verification logic
        String verifierCastType = null;
        switch (fieldType) {
            case "boolean" -> verifierCastType = "java.lang.Boolean";
            case "byte" -> verifierCastType = "java.lang.Byte";
            case "short" -> verifierCastType = "java.lang.Short";
            case "int" -> verifierCastType = "java.lang.Integer";
            case "long" -> verifierCastType = "java.lang.Long";
            case "float" -> verifierCastType = "java.lang.Float";
            case "double" -> verifierCastType = "java.lang.Double";
            case "char" -> verifierCastType = "java.lang.Character";
            default -> verifierCastType = fieldType;
        }
        String verifierArgExpr;
        if (verifierCastType.equals("java.lang.Object") || verifierCastType.equals("Object")) {
            verifierArgExpr = convertedVar;
        } else if (verifierCastType.equals("java.lang.String") || verifierCastType.equals("String")) {
            verifierArgExpr = "(String) " + convertedVar;
        } else {
            verifierArgExpr = "(" + verifierCastType + ") " + convertedVar;
        }
        if (verifierClassName != null && !verifierClassName.equals("me.bechberger.femtocli.Verifier.NullVerifier")
                && !verifierClassName.equals("me.bechberger.femtocli.Verifier$NullVerifier")) {
            builder.addStatement("new $L().verify($L)", verifierClassName, verifierArgExpr);
        } else if (verifierMethod != null && !verifierMethod.isEmpty()) {
            if (verifierMethod.contains("#")) {
                String[] parts = verifierMethod.split("#", 2);
                builder.addStatement("$L.$L($L)", parts[0], parts[1], verifierArgExpr);
            } else {
                builder.addStatement("$L.$L($L)", commandVar, verifierMethod, verifierArgExpr);
            }
        }

        // Assignment logic (with casting)
        if (targetAccess.contains("::")) {
            String[] parts = targetAccess.split("::", 2);
            builder.addStatement("$L.$L($L)", parts[0], parts[1], convertedVar);
        } else {
            String cast = "";
            if (fieldType.equals("java.lang.String") || fieldType.equals("String")) {
                if (!targetAccess.startsWith("tmp")) {
                    cast = "(String)";
                }
            } else if (fieldType.equals("java.lang.Object") || fieldType.equals("Object")) {
                cast = "";
            } else {
                // For primitives, cast to wrapper type to allow unboxing on assignment
                String wrapperType = switch (fieldType) {
                    case "boolean" -> "java.lang.Boolean";
                    case "byte" -> "java.lang.Byte";
                    case "short" -> "java.lang.Short";
                    case "int" -> "java.lang.Integer";
                    case "long" -> "java.lang.Long";
                    case "float" -> "java.lang.Float";
                    case "double" -> "java.lang.Double";
                    case "char" -> "java.lang.Character";
                    default -> fieldType;
                };
                cast = "(" + wrapperType + ")";
            }
            builder.addStatement("$L = $L$L", targetAccess, cast, convertedVar);
        }

        builder.nextControlFlow("catch (Exception e)");
        builder.addStatement("if (e instanceof me.bechberger.femtocli.ParseException) throw (me.bechberger.femtocli.ParseException)e");
        builder.addStatement("throw new me.bechberger.femtocli.ParseException(e.getMessage(), e)");
        builder.endControlFlow();
        builder.endControlFlow();
    }

    /**
     * Returns the field access expression for an option.
     * For regular options: "command.fieldName"
     * For mixin options: "command.mixinFieldName.fieldName"
     */
    static String getFieldAccessExpression(String fieldName, String mixinFieldName) {
        if (mixinFieldName != null && !mixinFieldName.isEmpty()) {
            return "command." + mixinFieldName + "." + fieldName;
        }
        return "command." + fieldName;
    }
}
