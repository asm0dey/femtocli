package me.bechberger.femtocli.processor.codegen;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import me.bechberger.femtocli.processor.model.CommandModel;
import me.bechberger.femtocli.processor.model.MethodSubcommandModel;
import me.bechberger.femtocli.processor.model.OptionModel;
import me.bechberger.femtocli.processor.model.ParameterModel;
import me.bechberger.femtocli.processor.model.SubcommandModel;

import javax.lang.model.element.Modifier;

/**
 * Generates help text methods for parser classes.
 * <p>
 * This class generates the printHelp and getHelpText methods that display usage information,
 * available options, positional parameters, and subcommands.
 */
public class HelpGenerator {

    private final CommandModel model;

    public HelpGenerator(CommandModel model) {
        this.model = model;
    }

    /**
     * Adds a help method to the parser class.
     */
    public void addHelpMethod(TypeSpec.Builder classBuilder) {
        // Add getHelpText() method that returns help as a string
        addGetHelpTextMethod(classBuilder);

        // Add printHelp() method that calls getHelpText() and prints
        MethodSpec.Builder printHelpBuilder = MethodSpec.methodBuilder("printHelp")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addJavadoc("Prints help text for this command.")
                .addStatement("$T.out.println(getHelpText())", System.class);
        classBuilder.addMethod(printHelpBuilder.build());
    }

    /**
     * Adds getHelpText() method that returns help text as a String.
     */
    private void addGetHelpTextMethod(TypeSpec.Builder classBuilder) {
        MethodSpec.Builder helpBuilder = MethodSpec.methodBuilder("getHelpText")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(String.class)
                .addJavadoc("Returns help text for this command as a string.\n")
                .addJavadoc("@return The help text.");

        helpBuilder.addStatement("$T sb = new $T()", StringBuilder.class, StringBuilder.class);

        // Basic help output
        String[] description = model.getDescription();
        boolean hasDescription = description != null && description.length > 0;
        String descriptionText = hasDescription ? description[0] : "";
        helpBuilder.addStatement("sb.append(\"Usage: " + model.getName() + "\\n\")");
        helpBuilder.addStatement("sb.append(\"" + escapeForJavaString(descriptionText) + "\\n\")");

        // Print subcommands section (if command has subcommands)
        addSubcommandsSectionToString(helpBuilder);

        // For commands with subcommands, suppress options/parameters in root help
        if (model.getSubcommands().isEmpty()) {
            // Print options section
            addOptionsSectionToString(helpBuilder);
            // Print parameters section
            addParametersSectionToString(helpBuilder);
        }

        helpBuilder.addStatement("return sb.toString()");
        classBuilder.addMethod(helpBuilder.build());
    }

    /**
     * Escapes special characters for use in Java string literals.
     */
    private String escapeForJavaString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
    }

    /**
     * Adds the subcommands section to the help string.
     */
    private void addSubcommandsSectionToString(MethodSpec.Builder helpBuilder) {
        if (model.getSubcommands().isEmpty() && model.getMethodSubcommands().isEmpty()) {
            return;
        }

        helpBuilder.addStatement("sb.append(\"\\n\")");
        helpBuilder.addStatement("sb.append(\"Subcommands:\\n\")");

        // Class-based subcommands
        for (SubcommandModel subcommand : model.getSubcommands()) {
            String subcommandName = subcommand.getName() != null ? subcommand.getName().toString() : "";
            helpBuilder.addStatement("sb.append(\"  " + escapeForJavaString(subcommandName) + "\\n\")");
            // Add description if present
            String description = subcommand.getDescription();
            if (description != null && !description.isEmpty()) {
                helpBuilder.addStatement("sb.append(\"    " + escapeForJavaString(description) + "\\n\")");
            }
            helpBuilder.addStatement("sb.append(\"\\n\")");
        }

        // Method-based subcommands
        for (MethodSubcommandModel methodSubcommand : model.getMethodSubcommands()) {
            String subcommandName = methodSubcommand.getName();
            helpBuilder.addStatement("sb.append(\"  " + escapeForJavaString(subcommandName) + "\\n\")");
            // Add description if present
            String[] description = methodSubcommand.getDescription();
            if (description != null && description.length > 0 && !description[0].isEmpty()) {
                helpBuilder.addStatement("sb.append(\"    " + escapeForJavaString(description[0]) + "\\n\")");
            }
            helpBuilder.addStatement("sb.append(\"\\n\")");
        }
    }

    /**
     * Adds the options section to the help string.
     */
    private void addOptionsSectionToString(MethodSpec.Builder helpBuilder) {
        // Use getAllOptions to include options from mixins
        var options = model.getAllOptions();
        if (options.isEmpty()) {
            return;
        }

        // Track seen options by field name to avoid duplicates
        // (getAllOptions may return same option multiple times if it has multiple names)
        java.util.Set<String> seenFields = new java.util.LinkedHashSet<>();

        helpBuilder.addStatement("sb.append(\"\\n\")");
        helpBuilder.addStatement("sb.append(\"Options:\\n\")");

        for (OptionModel option : options) {
            if (option.isHidden()) {
                continue;
            }

            // Skip if we've already seen this option (by field name)
            if (!seenFields.add(option.getFieldName())) {
                continue;
            }

            // Build option names string
            StringBuilder namesBuilder = new StringBuilder();
            for (String name : option.getNames()) {
                if (!namesBuilder.isEmpty()) {
                    namesBuilder.append(", ");
                }
                namesBuilder.append(name);
            }

            // Build param label if present
            String paramLabel = option.getParamLabel();
            if (paramLabel.isEmpty()) {
                paramLabel = option.getFieldName().toUpperCase();
            }

            helpBuilder.addStatement("sb.append(\"  " + escapeForJavaString(namesBuilder.toString()) + " <$L>\\n\")", paramLabel);

            // Add description
            String description = option.getDescription();
            if (!description.isEmpty()) {
                helpBuilder.addStatement("sb.append(\"    " + escapeForJavaString(description) + "\\n\")");
            }

            // Add default value if applicable
            if (option.hasDefaultValue() && option.isShowDefaultValueInHelp()) {
                helpBuilder.addStatement("sb.append(\"    Default: " + escapeForJavaString(option.getDefaultValue()) + "\\n\")");
            }

            helpBuilder.addStatement("sb.append(\"\\n\")");
        }
    }

    /**
     * Adds the positional parameters section to the help string.
     */
    private void addParametersSectionToString(MethodSpec.Builder helpBuilder) {
        if (model.getParameters().isEmpty()) {
            return;
        }

        helpBuilder.addStatement("sb.append(\"\\n\")");
        helpBuilder.addStatement("sb.append(\"Positional Parameters:\\n\")");

        for (ParameterModel parameter : model.getParameters()) {
            // Build index spec string
            String indexSpec = parameter.getIndex();

            // Build param label if present
            String paramLabel = parameter.getParamLabel();
            if (paramLabel.isEmpty()) {
                paramLabel = parameter.getFieldName().toUpperCase();
            }

            helpBuilder.addStatement("sb.append(\"  <$L> [$L]\\n\")", paramLabel, indexSpec);

            // Add description
            String description = parameter.getDescription();
            if (!description.isEmpty()) {
                helpBuilder.addStatement("sb.append(\"    " + escapeForJavaString(description) + "\\n\")");
            }

            // Add default value if applicable
            if (parameter.hasDefaultValue()) {
                helpBuilder.addStatement("sb.append(\"    Default: " + escapeForJavaString(parameter.getDefaultValue()) + "\\n\")");
            }

            helpBuilder.addStatement("sb.append(\"\\n\")");
        }
    }
}
