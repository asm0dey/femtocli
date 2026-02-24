package me.bechberger.femtocli.processor.codegen;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import me.bechberger.femtocli.processor.model.CommandModel;
import me.bechberger.femtocli.processor.model.OptionModel;

import javax.lang.model.element.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates option lookup and metadata methods for parser classes.
 * <p>
 * This class generates methods for looking up options by name, checking if an
 * option is multi-value, and retrieving split delimiters.
 */
public final class OptionLookupGenerator {

    private OptionLookupGenerator() {
        // Utility class
    }

    /**
     * Adds methods to look up options by name.
     *
     * @param model         the command model
     * @param classBuilder  the class builder to add methods to
     * @param hasMultiValue whether the command has multi-value options
     */
    public static void addOptionLookupMethods(CommandModel model, TypeSpec.Builder classBuilder, boolean hasMultiValue) {
        addFieldNameForOptionMethod(classBuilder, model);

        if (hasMultiValue) {
            addIsMultiValueMethod(classBuilder, model);
            addSplitDelimiterMethod(classBuilder, model);
        }
    }

    /**
     * Adds a method that maps option names to field names.
     */
    private static void addFieldNameForOptionMethod(TypeSpec.Builder classBuilder, CommandModel model) {
        MethodSpec.Builder getOptionFieldMethod = MethodSpec.methodBuilder("getFieldNameForOption")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(String.class)
                .addParameter(String.class, "option");

        getOptionFieldMethod.beginControlFlow("switch (option)");

        Set<String> processedFields = new LinkedHashSet<>();
        for (OptionModel option : model.getAllOptions()) {
            String fieldName = option.getFieldName();
            if (processedFields.contains(fieldName)) {
                continue;
            }
            processedFields.add(fieldName);

            for (String name : option.getNames()) {
                String caseLabel = name.replace("\"", "\\\"");
                getOptionFieldMethod.addCode("case \"$L\": return \"$L\";\n", caseLabel, fieldName);
            }
        }

        // Add standard help options if enabled
        if (model.isMixinStandardHelpOptions()) {
            getOptionFieldMethod.addCode("case \"-h\": return \"help\";\n");
            getOptionFieldMethod.addCode("case \"--help\": return \"help\";\n");
            getOptionFieldMethod.addCode("case \"-V\": return \"version\";\n");
            getOptionFieldMethod.addCode("case \"--version\": return \"version\";\n");
        }

        getOptionFieldMethod.addCode("default: return null;\n");
        getOptionFieldMethod.endControlFlow();

        classBuilder.addMethod(getOptionFieldMethod.build());
    }

    /**
     * Adds method to check if an option is multi-value.
     */
    private static void addIsMultiValueMethod(TypeSpec.Builder classBuilder, CommandModel model) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("isMultiValueOption")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(boolean.class)
                .addParameter(String.class, "optionName");

        method.beginControlFlow("switch (optionName)");

        Set<String> processedFields = new LinkedHashSet<>();
        for (OptionModel option : model.getAllOptions()) {
            String fieldName = option.getFieldName();
            if (processedFields.contains(fieldName)) {
                continue;
            }
            processedFields.add(fieldName);

            if (option.hasSplit()) {
                for (String name : option.getNames()) {
                    method.addCode("case \"$L\": return true;\n", name);
                }
            }
        }

        method.addStatement("default: return false");
        method.endControlFlow();

        classBuilder.addMethod(method.build());
    }

    /**
     * Adds method to get split delimiter for an option.
     */
    private static void addSplitDelimiterMethod(TypeSpec.Builder classBuilder, CommandModel model) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("getSplitDelimiter")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(String.class)
                .addParameter(String.class, "optionName");

        method.beginControlFlow("switch (optionName)");

        Set<String> processedFields = new LinkedHashSet<>();
        for (OptionModel option : model.getAllOptions()) {
            String fieldName = option.getFieldName();
            if (processedFields.contains(fieldName)) {
                continue;
            }
            processedFields.add(fieldName);

            if (option.hasSplit()) {
                for (String name : option.getNames()) {
                    String delimiter = option.getSplit();
                    method.addCode("case \"$L\": return $S;\n", name, delimiter);
                }
            }
        }

        method.addStatement("default: return \"\"");
        method.endControlFlow();

        classBuilder.addMethod(method.build());
    }

    /**
     * Adds method to check if an option is a boolean flag.
     *
     * @param model        the command model
     * @param classBuilder the class builder to add method to
     */
    public static void addTypeInfoMethods(CommandModel model, TypeSpec.Builder classBuilder) {
        MethodSpec.Builder isBooleanMethod = MethodSpec.methodBuilder("isBooleanOption")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(boolean.class)
                .addParameter(String.class, "optionName");

        isBooleanMethod.addStatement("String fieldName = getFieldNameForOption(optionName)");
        isBooleanMethod.beginControlFlow("if (fieldName == null)");
        isBooleanMethod.addStatement("return false");
        isBooleanMethod.endControlFlow();

        isBooleanMethod.beginControlFlow("switch (fieldName)");

        Set<String> processedFields = new LinkedHashSet<>();
        for (OptionModel option : model.getAllOptions()) {
            String fieldName = option.getFieldName();
            if (processedFields.contains(fieldName)) {
                continue;
            }
            processedFields.add(fieldName);

            String fieldType = option.getFieldType().toString();
            if (fieldType.equals("boolean") || fieldType.equals("java.lang.Boolean")) {
                isBooleanMethod.addCode("case \"$L\": return true;\n", fieldName);
            }
        }

        isBooleanMethod.addStatement("default: return false");
        isBooleanMethod.endControlFlow();

        classBuilder.addMethod(isBooleanMethod.build());
    }
}
