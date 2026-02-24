package me.bechberger.femtocli.processor.codegen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import me.bechberger.femtocli.ParseException;
import me.bechberger.femtocli.processor.model.CommandModel;
import me.bechberger.femtocli.processor.model.OptionModel;
import me.bechberger.femtocli.processor.model.ParameterModel;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates the main parse method and related logic for parser classes.
 * <p>
 * This class generates the parse method along with option parsing logic,
 * positional parameter application, multi-value option application,
 * default value application, and required option validation.
 */
public class ParseMethodGenerator {


    private ParseMethodGenerator() {
    }

    /**
     * Adds the main parse() method for commands without subcommands.
     */
    public static void addParseSimpleMethod(CommandModel model, TypeSpec.Builder classBuilder, boolean hasParameters, boolean hasOptions, boolean hasMultiValue) {
        ClassName commandClassName = ClassName.bestGuess(model.getQualifiedName());
        MethodSpec.Builder parseBuilder = MethodSpec.methodBuilder("parse")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(commandClassName)
                .addParameter(String[].class, "args")
                .addJavadoc("Parses command line arguments and returns a populated command instance.\n")
                .addJavadoc("@param args Command line arguments\n")
                .addJavadoc("@return Populated command instance\n")
                .addJavadoc("@throws ParseException if parsing fails")
                .addJavadoc("Help request (--help) prints help and returns default command instance.\n")
                .addJavadoc("Use getHelpText() to get the help text as a string.");

        // Add help check at the start - prints help and returns default instance if found
        addEarlyHelpCheck(parseBuilder, commandClassName, model.isMixinStandardHelpOptions());

        addParseImplementationBody(parseBuilder, model, commandClassName, hasParameters, hasOptions, hasMultiValue, model.isMixinStandardHelpOptions());
        classBuilder.addMethod(parseBuilder.build());
    }

    /**
     * Adds the CommandParsingResult static class.
     */
    private static void addCommandParsingResultClass(TypeSpec.Builder classBuilder, CommandModel model) {
        ClassName rootClassName = ClassName.bestGuess(model.getQualifiedName());
        TypeSpec.Builder resultBuilder = TypeSpec.classBuilder("CommandParsingResult")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

        resultBuilder.addField(rootClassName, "rootCommand", Modifier.PUBLIC, Modifier.FINAL);
        resultBuilder.addField(Object.class, "subcommand", Modifier.PUBLIC, Modifier.FINAL);

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(rootClassName, "rootCommand")
                .addParameter(Object.class, "subcommand")
                .addStatement("this.rootCommand = rootCommand")
                .addStatement("this.subcommand = subcommand")
                .build();

        resultBuilder.addMethod(constructor);

        resultBuilder.addMethod(MethodSpec.methodBuilder("getRootCommand")
                .addModifiers(Modifier.PUBLIC)
                .returns(rootClassName)
                .addStatement("return rootCommand")
                .build());

        resultBuilder.addMethod(MethodSpec.methodBuilder("getSubcommand")
                .addModifiers(Modifier.PUBLIC)
                .returns(Object.class)
                .addStatement("return subcommand")
                .build());

        resultBuilder.addMethod(MethodSpec.methodBuilder("hasSubcommand")
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addStatement("return subcommand != null")
                .build());

        classBuilder.addType(resultBuilder.build());
    }

    /**
     * Adds the main parse() method for commands with subcommands.
     * This method delegates to either subcommand parsers or the root command.
     */
    public static void addParseWithSubcommandMethod(CommandModel model, TypeSpec.Builder classBuilder) {
        addCommandParsingResultClass(classBuilder, model);
        ClassName commandClassName = ClassName.bestGuess(model.getQualifiedName());
        String parserClassName = model.getClassName() + "Parser";
        ClassName resultType = ClassName.get(model.getPackageName(), parserClassName, "CommandParsingResult");

        MethodSpec.Builder parseBuilder = MethodSpec.methodBuilder("parse")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(resultType)
                .addParameter(String[].class, "args")
                .addJavadoc("Parses command line arguments, routing to subcommands if specified.\n")
                .addJavadoc("@param args Command line arguments\n")
                .addJavadoc("@return Parsing result containing root and subcommand\n")
                .addJavadoc("@throws ParseException if parsing fails\n")
                .addJavadoc("Help request (--help) prints help and returns null without exception.");

        // Find subcommand index FIRST - this allows options before subcommand and --help on subcommands
        parseBuilder.addStatement("int subIdx = findSubcommandIndex(args)");
        parseBuilder.beginControlFlow("if (subIdx >= 0)");
        // Parse any root options that appear before the subcommand
        parseBuilder.addStatement("$T rootCommand", commandClassName);
        parseBuilder.beginControlFlow("if (subIdx > 0)");
        parseBuilder.addStatement("String[] rootArgs = new String[subIdx]");
        parseBuilder.addStatement("System.arraycopy(args, 0, rootArgs, 0, subIdx)");
        parseBuilder.addStatement("rootCommand = parseRoot(rootArgs)");
        parseBuilder.nextControlFlow("else");
        parseBuilder.addStatement("rootCommand = parseRoot(new String[0])");
        parseBuilder.endControlFlow();
        parseBuilder.addStatement("String subcommand = args[subIdx]");
        parseBuilder.addStatement("String[] subcommandArgs = new String[args.length - subIdx - 1]");
        parseBuilder.addStatement("System.arraycopy(args, subIdx + 1, subcommandArgs, 0, args.length - subIdx - 1)");
        parseBuilder.addComment("Route to subcommand parser");
        addSubcommandRoutingWithResult(parseBuilder, model, resultType);
        // If we get here, the subcommand name was not one of the known ones (shouldn't happen)
        parseBuilder.nextControlFlow("else");
        parseBuilder.addComment("No subcommand provided - either only root options/help or an unknown token");
        // If there is a first non-option token and it's not a subcommand, throw unknown subcommand
        parseBuilder.beginControlFlow("for (int i = 0; i < args.length; i++)");
        parseBuilder.beginControlFlow("if (!args[i].startsWith(\"-\"))");
        parseBuilder.addStatement("throw $T.unknownSubcommand(args[i])", ParseException.class);
        parseBuilder.endControlFlow();
        parseBuilder.endControlFlow();
        // Otherwise, handle help on root command and parse as root
        parseBuilder.beginControlFlow("if (args.length > 0)");
        parseBuilder.beginControlFlow("for (String arg : args)");
        parseBuilder.beginControlFlow("if (\"--help\".equals(arg))");
        parseBuilder.addStatement("printHelp()");
        parseBuilder.addStatement("return null");
        parseBuilder.endControlFlow();
        if (model.isMixinStandardHelpOptions()) {
            parseBuilder.beginControlFlow("if (\"-h\".equals(arg))");
            parseBuilder.addStatement("printHelp()");
            parseBuilder.addStatement("return null");
            parseBuilder.endControlFlow();
        }
        parseBuilder.endControlFlow();
        parseBuilder.endControlFlow();
        parseBuilder.addComment("Parse as root command");
        parseBuilder.addStatement("return new $T(parseRoot(args), null)", resultType);
        parseBuilder.endControlFlow();

        classBuilder.addMethod(parseBuilder.build());
    }

    /**
     * Adds routing statements for each subcommand, wrapping results in CommandParsingResult.
     */
    private static void addSubcommandRoutingWithResult(MethodSpec.Builder builder, CommandModel model, ClassName resultType) {
        for (var subcommand : model.getSubcommands()) {
            String subcommandName = subcommand.getName().toString();
            String subcommandClassName = subcommand.getClassName() + "Parser";
            String subcommandPackage = subcommand.getPackageName();
            String fullParserClass = subcommandPackage + "." + subcommandClassName;

            builder.beginControlFlow("if (\"$L\".equals(subcommand))", subcommandName);
            // Delegate to subcommand parser using precomputed subcommandArgs
            builder.addStatement("Object subInstance = $L.parse(subcommandArgs)", fullParserClass);
            builder.addStatement("return new $T(rootCommand, subInstance)", resultType);
            builder.endControlFlow();
        }

        // Add routing for method-based subcommands
        for (var methodSubcommand : model.getMethodSubcommands()) {
            String subcommandName = methodSubcommand.getName();
            String methodName = methodSubcommand.getMethod().getSimpleName().toString();
            String parentQualifiedName = methodSubcommand.getParentQualifiedName();
            ClassName parentClassName = ClassName.bestGuess(parentQualifiedName);
            String returnType = methodSubcommand.getReturnType();

            builder.beginControlFlow("if (\"$L\".equals(subcommand))", subcommandName);
            // Invoke method-based subcommand - create parent and call method
            builder.addComment("Create parent instance and invoke method-based subcommand");
            builder.addStatement("$T parent = new $T()", parentClassName, parentClassName);
            if ("void".equals(returnType)) {
                // Void method - call without capturing result
                builder.addStatement("parent.$L()", methodName);
                builder.addStatement("return new $T(rootCommand, null)", resultType);
            } else if ("int".equals(returnType)) {
                // Int method - capture return value
                builder.addStatement("$T result = parent.$L()", Integer.class, methodName);
                builder.addStatement("return new $T(rootCommand, result)", resultType);
            } else {
                // Other return type - capture as Object
                builder.addStatement("$T result = ($T) parent.$L()", Object.class, returnType, methodName);
                builder.addStatement("return new $T(rootCommand, result)", resultType);
            }
            builder.endControlFlow();
        }

        // Unknown subcommand
        builder.addStatement("throw $T.unknownSubcommand(subcommand)", ParseException.class);
    }

    /**
     * Adds the parseRoot method with the actual parsing logic for commands with subcommands.
     */
    public static void addParseRootMethod(CommandModel model, TypeSpec.Builder classBuilder, boolean hasParameters, boolean hasOptions, boolean hasMultiValue, boolean hasSubcommands) {
        ClassName commandClassName = ClassName.bestGuess(model.getQualifiedName());
        MethodSpec.Builder parseBuilder = MethodSpec.methodBuilder("parseRoot")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(commandClassName)
                .addParameter(String[].class, "args")
                .addJavadoc("Parses command line arguments and returns a populated command instance.\n")
                .addJavadoc("@param args Command line arguments\n")
                .addJavadoc("@return Populated command instance\n")
                .addJavadoc("@throws ParseException if parsing fails");

        addParseImplementationBody(parseBuilder, model, commandClassName, hasParameters, hasOptions, hasMultiValue, model.isMixinStandardHelpOptions());
        classBuilder.addMethod(parseBuilder.build());
    }

    /**
     * Adds the parsing logic implementation to a method builder.
     * This is shared between parse() and parseRoot() methods.
     */
    private static void addParseImplementationBody(MethodSpec.Builder parseBuilder, CommandModel model, ClassName commandClassName, boolean hasParameters, boolean hasOptions, boolean hasMultiValue, boolean mixinStandardHelpOptions) {
        // Reset parsed options set, multi-value accumulator, and positional args
        if (hasOptions) {
            parseBuilder.addStatement("parsedOptions.clear()");
        }
        if (hasMultiValue) {
            parseBuilder.addStatement("multiValueAccumulator.clear()");
        }
        if (hasParameters) {
            parseBuilder.addStatement("positionalArgs.clear()");
        }
        if (hasOptions && hasParameters) {
            parseBuilder.addStatement("seenPositional = false");
        }
        if (hasOptions || hasParameters) {
            parseBuilder.addStatement("afterEndOfOptions = false");
        }

        // Create command instance
        parseBuilder.addStatement("$T command = new $T()", commandClassName, commandClassName);

        // Parse options and collect positional arguments
        addOptionParsingLogic(parseBuilder, model, hasParameters, hasOptions);

        // Apply positional parameters
        if (hasParameters) {
            addPositionalParameterApplication(parseBuilder, model);
        }

        // Apply multi-value options
        if (hasMultiValue) {
            addMultiValueApplication(parseBuilder, model);
        }

        // Apply default values
        if (hasOptions) {
            addDefaultValueApplication(parseBuilder, model);
        }

        // Validate required options
        if (hasOptions) {
            addRequiredOptionValidation(parseBuilder, model);
        }

        // Return command
        parseBuilder.addStatement("return command");
    }

    /**
     * Adds option parsing logic to the parse method.
     */
    public static void addOptionParsingLogic(MethodSpec.Builder parseBuilder, CommandModel model, boolean hasParameters, boolean hasOptions) {
        boolean hasSubcommands = !model.getSubcommands().isEmpty();
        parseBuilder.addComment("Parse command line arguments");

        parseBuilder.beginControlFlow("for (int i = 0; i < args.length; i++)");
        parseBuilder.addStatement("String arg = args[i]");

        if (hasSubcommands) {
            parseBuilder.addComment("Check for subcommand");
            for (var subcommand : model.getSubcommands()) {
                parseBuilder.beginControlFlow("if (\"$L\".equals(arg))", subcommand.getName());
                parseBuilder.addStatement("throw new $T(\"Unknown argument: \" + arg)", ParseException.class);
                parseBuilder.endControlFlow();
            }
        }

        // Handle end-of-options marker
        if (hasOptions || hasParameters) {
            parseBuilder.beginControlFlow("if (\"--\".equals(arg))");
            parseBuilder.addStatement("afterEndOfOptions = true");
            parseBuilder.addStatement("continue");
            parseBuilder.endControlFlow();
        }

        // If after end-of-options marker, treat all as positional
        if (hasParameters) {
            parseBuilder.beginControlFlow("if (afterEndOfOptions)");
            parseBuilder.addStatement("positionalArgs.add(arg)");
            parseBuilder.addStatement("continue");
            parseBuilder.endControlFlow();
        } else if (hasOptions) {
            parseBuilder.beginControlFlow("if (afterEndOfOptions)");
            parseBuilder.addStatement("throw new $T(\"Unknown argument: \" + arg)", ParseException.class);
            parseBuilder.endControlFlow();
        }

        if (hasOptions) {
            parseBuilder.beginControlFlow("if (arg.startsWith(\"-\") && !afterEndOfOptions)");
            if (hasParameters) {
                parseBuilder.beginControlFlow("if (seenPositional)");
                parseBuilder.addStatement("throw new $T(\"Options must go before positional parameters: \" + arg)", ParseException.class);
                parseBuilder.endControlFlow();
            }
            parseBuilder.beginControlFlow("if (arg.startsWith(\"--\"))");
            parseBuilder.addComment("Handle long options (--option or --option=value)");
            parseBuilder.addStatement("int equalsIndex = arg.indexOf('=')");

            parseBuilder.beginControlFlow("if (equalsIndex > 0)")
                    .addComment("Format: --option=value")
                    .addStatement("String optionName = arg.substring(0, equalsIndex)")
                    .addStatement("String value = arg.substring(equalsIndex + 1)")
                    .addStatement("setOptionValue(command, optionName, value)")
                    .nextControlFlow("else")
                    .addComment("Format: --option [value]");
            parseBuilder.addStatement("String optionName = arg");
            parseBuilder.beginControlFlow("if (getFieldNameForOption(optionName) == null)")
                    .addStatement("throw $T.unknownOption(optionName)", ParseException.class)
                    .endControlFlow();

            // Check if it's a boolean flag
            parseBuilder.beginControlFlow("if (isBooleanOption(optionName))")
                    .addStatement("setOptionValue(command, optionName, null)")
                    .nextControlFlow("else")
                    .beginControlFlow("if (i + 1 >= args.length || args[i + 1].startsWith(\"-\"))")
                    .addStatement("throw new $T(\"Option \" + optionName + \" requires a value\")", ParseException.class)
                    .nextControlFlow("else")
                    .addStatement("i++")
                    .addStatement("setOptionValue(command, optionName, args[i])")
                    .endControlFlow()
                    .endControlFlow();
            parseBuilder.endControlFlow(); // Close long options
            parseBuilder.nextControlFlow("else if (arg.startsWith(\"-\") && arg.length() > 1)");
            parseBuilder.addComment("Handle short options (-o, -ovalue, -o=value, or combined -abc)");

            parseBuilder.beginControlFlow("if (arg.indexOf('=') != -1)")
                    .addComment("Format: -o=value")
                    .addStatement("int eqIndex = arg.indexOf('=')")
                    .addStatement("String optionName = arg.substring(0, eqIndex)")
                    .addStatement("String value = arg.substring(eqIndex + 1)")
                    .addStatement("setOptionValue(command, optionName, value)")
                    .nextControlFlow("else")
                    .addComment("Possibly combined options or -o value or -ovalue")
                    .beginControlFlow("for (int j = 1; j < arg.length(); j++)")
                    .addStatement("String currentOption = \"-\" + arg.charAt(j)")
                    .beginControlFlow("if (isBooleanOption(currentOption))")
                    .addStatement("setOptionValue(command, currentOption, null)")
                    .nextControlFlow("else")
                    .addComment("Option requires a value")
                    .beginControlFlow("if (getFieldNameForOption(currentOption) == null)")
                    .addStatement("throw $T.unknownOption(currentOption)", ParseException.class)
                    .endControlFlow()
                    .beginControlFlow("if (j + 1 < arg.length())")
                    .addComment("Format: -ovalue")
                    .addStatement("setOptionValue(command, currentOption, arg.substring(j + 1))")
                    .addStatement("j = arg.length()")
                    .nextControlFlow("else")
                    .addComment("Format: -o value")
                    .beginControlFlow("if (i + 1 >= args.length || args[i + 1].startsWith(\"-\"))")
                    .addStatement("throw new $T(\"Option \" + currentOption + \" requires a value\")", ParseException.class)
                    .endControlFlow()
                    .addStatement("i++")
                    .addStatement("setOptionValue(command, currentOption, args[i])")
                    .endControlFlow()
                    .endControlFlow()
                    .endControlFlow()
                    .endControlFlow();
            parseBuilder.endControlFlow(); // Close short options
            parseBuilder.nextControlFlow("else");
        }
        if (hasParameters) {
            parseBuilder.addComment("Positional argument - store for later processing");
            if (hasOptions) {
                parseBuilder.addStatement("seenPositional = true");
            }
            parseBuilder.addStatement("positionalArgs.add(arg)");
        } else {
            parseBuilder.addStatement("throw new $T(\"Unknown argument: \" + arg)", ParseException.class);
        }

        if (hasOptions) {
            parseBuilder.endControlFlow();
        }

        parseBuilder.endControlFlow(); // main for loop
    }

    /**
     * Adds logic to apply positional parameters after option parsing.
     */
    public static void addPositionalParameterApplication(MethodSpec.Builder parseBuilder, CommandModel model) {
        parseBuilder.addComment("Apply positional parameters to command fields");

        // First, handle single-index parameters
        for (ParameterModel parameter : model.getParameters()) {
            if (parameter.isSingleIndex()) {
                int index = parameter.getSingleIndex();
                String fieldName = parameter.getFieldName();

                parseBuilder.beginControlFlow("if (positionalArgs.size() > $L)", index);
                parseBuilder.addStatement("setParameterValue(command, \"$L\", positionalArgs.get($L))", fieldName, index);
                parseBuilder.endControlFlow();
            }
        }

        // Then, handle range and varargs parameters
        for (ParameterModel parameter : model.getParameters()) {
            if (!parameter.isSingleIndex()) {
                String fieldName = parameter.getFieldName();

                if (parameter.isUnbounded()) {
                    // Unbounded range (1..*, 0..*, etc.)
                    int rangeStart = parameter.getRangeStart();
                    parseBuilder.beginControlFlow("if (positionalArgs.size() > $L)", rangeStart);
                    parseBuilder.addStatement("$T<$T> varargsList = new $T<>()", ArrayList.class, String.class, ArrayList.class);
                    parseBuilder.beginControlFlow("for (int i = $L; i < positionalArgs.size(); i++)", rangeStart);
                    parseBuilder.addStatement("varargsList.add(positionalArgs.get(i))");
                    parseBuilder.endControlFlow();
                    parseBuilder.addStatement("setParameterValues(command, \"$L\", varargsList)", fieldName);
                    parseBuilder.endControlFlow();
                } else {
                    // Bounded range (0..1, 1..3, etc.)
                    int rangeStart = parameter.getRangeStart();
                    int rangeEnd = parameter.getRangeEnd();

                    if (rangeStart == rangeEnd) {
                        // Single value from range
                        parseBuilder.beginControlFlow("if (positionalArgs.size() > $L)", rangeStart);
                        parseBuilder.addStatement("setParameterValue(command, \"$L\", positionalArgs.get($L))", fieldName, rangeStart);
                        parseBuilder.endControlFlow();
                    } else {
                        // Multiple values from range
                        parseBuilder.beginControlFlow("if (positionalArgs.size() >= $L)", rangeEnd + 1);
                        parseBuilder.addStatement("$T<$T> rangeList = new $T<>()", ArrayList.class, String.class, ArrayList.class);
                        parseBuilder.beginControlFlow("for (int i = $L; i <= $L && i < positionalArgs.size(); i++)", rangeStart, rangeEnd);
                        parseBuilder.addStatement("rangeList.add(positionalArgs.get(i))");
                        parseBuilder.endControlFlow();
                        parseBuilder.addStatement("setParameterValues(command, \"$L\", rangeList)", fieldName);
                        parseBuilder.endControlFlow();
                    }
                }
            }
        }
    }

    /**
     * Adds logic to apply multi-value options after parsing.
     */
    public static void addMultiValueApplication(MethodSpec.Builder parseBuilder, CommandModel model) {
        parseBuilder.addComment("Apply multi-value options from accumulator");

        Set<String> processedFields = new LinkedHashSet<>();
        for (OptionModel option : model.getAllOptions()) {
            String fieldName = option.getFieldName();
            if (processedFields.contains(fieldName) || !option.hasSplit()) {
                processedFields.add(fieldName);
                continue;
            }
            processedFields.add(fieldName);

            String fieldType = option.getFieldType().toString();
            String fieldAccess = getFieldAccessExpression(fieldName, option.getMixinFieldName());

            parseBuilder.beginControlFlow("if (multiValueAccumulator.containsKey(\"$L\"))", fieldName);

            // Ensure mixin is initialized if needed
            initializeMixinIfNeeded(parseBuilder, model, option);

            // For List types - store as List<String> for simplicity
            if (fieldType.contains("List") || fieldType.contains("ArrayList")) {
                parseBuilder.addStatement("$T<Object> tmp = new $T<>()", ArrayList.class, ArrayList.class);
                parseBuilder.beginControlFlow("for (String v : multiValueAccumulator.get(\"$L\"))", fieldName);
                OptionValueGenerator.addConvertedValueAssignment(parseBuilder, "tmp::add", "v", "java.lang.Object",
                        option.getConverterClassName(), option.getConverterMethod(),
                        option.getVerifierClassName(), option.getVerifierMethod(), "command");
                parseBuilder.endControlFlow();
                parseBuilder.addStatement("$L = (java.util.List) tmp", fieldAccess);
            }
            // For array types
            else if (fieldType.endsWith("[]")) {
                String componentType = fieldType.substring(0, fieldType.length() - 2);
                parseBuilder.addStatement("$T<$T> values = multiValueAccumulator.get(\"$L\")", ArrayList.class, String.class, fieldName);
                parseBuilder.addStatement("Object[] tmp = new Object[values.size()]");
                parseBuilder.beginControlFlow("for (int i = 0; i < values.size(); i++)");
                OptionValueGenerator.addConvertedValueAssignment(parseBuilder, "tmp[i]", "values.get(i)", "java.lang.Object",
                        option.getConverterClassName(), option.getConverterMethod(),
                        option.getVerifierClassName(), option.getVerifierMethod(), "command");
                parseBuilder.endControlFlow();
                parseBuilder.addStatement("$T[] array = new $T[tmp.length]", ClassName.bestGuess(componentType), ClassName.bestGuess(componentType));
                parseBuilder.addStatement("System.arraycopy(tmp, 0, array, 0, tmp.length)");
                parseBuilder.addStatement("$L = array", fieldAccess);
            }

            parseBuilder.endControlFlow();
        }
    }

    static void initializeMixinIfNeeded(MethodSpec.Builder parseBuilder, CommandModel model, OptionModel option) {
        if (option.getMixinFieldName() != null && !option.getMixinFieldName().isEmpty()) {
            String mixinField = option.getMixinFieldName();
            String mixinType = null;
            for (var mixin : model.getMixins()) {
                if (mixin.getFieldName().equals(mixinField)) {
                    mixinType = mixin.getMixinQualifiedName();
                    break;
                }
            }
            if (mixinType != null) {
                parseBuilder.addStatement("if (command.$L == null) command.$L = new $L()", mixinField, mixinField, mixinType);
            }
        }
    }

    /**
     * Returns the field access expression for an option.
     * For regular options: "command.fieldName"
     * For mixin options: "command.mixinFieldName.fieldName"
     */
    private static String getFieldAccessExpression(String fieldName, String mixinFieldName) {
        return OptionValueGenerator.getFieldAccessExpression(fieldName, mixinFieldName);
    }

    /**
     * Adds default value application logic.
     */
    public static void addDefaultValueApplication(MethodSpec.Builder parseBuilder, CommandModel model) {
        parseBuilder.addComment("Apply default values for options that were not provided");

        Set<String> processedFields = new LinkedHashSet<>();
        for (OptionModel option : model.getAllOptions()) {
            String fieldName = option.getFieldName();
            if (processedFields.contains(fieldName)) {
                continue;
            }
            processedFields.add(fieldName);
            if (option.hasDefaultValue()) {
                String defaultValue = option.getDefaultValue();
                String fieldType = option.getFieldType().toString();
                String fieldAccess = getFieldAccessExpression(fieldName, option.getMixinFieldName());

                parseBuilder.beginControlFlow("if (!parsedOptions.contains(\"$L\"))", fieldName);

                // Ensure mixin is initialized if needed
                initializeMixinIfNeeded(parseBuilder, model, option);

                switch (fieldType) {
                    case "boolean", "java.lang.Boolean" ->
                            parseBuilder.addStatement("$L = $L", fieldAccess, Boolean.parseBoolean(defaultValue));
                    case "int", "java.lang.Integer" ->
                            parseBuilder.addStatement("$L = $L", fieldAccess, Integer.parseInt(defaultValue));
                    case "long", "java.lang.Long" ->
                            parseBuilder.addStatement("$L = $LL", fieldAccess, Long.parseLong(defaultValue));
                    case "float", "java.lang.Float" ->
                            parseBuilder.addStatement("$L = $Lf", fieldAccess, Float.parseFloat(defaultValue));
                    case "double", "java.lang.Double" ->
                            parseBuilder.addStatement("$L = $Ld", fieldAccess, Double.parseDouble(defaultValue));
                    default -> parseBuilder.addStatement("$L = $S", fieldAccess, defaultValue);
                }
                parseBuilder.endControlFlow();
            }
        }

        // Apply default values for positional parameters
        processedFields.clear();
        for (ParameterModel parameter : model.getParameters()) {
            String fieldName = parameter.getFieldName();
            if (processedFields.contains(fieldName)) {
                continue;
            }
            processedFields.add(fieldName);
            if (parameter.hasDefaultValue()) {
                String defaultValue = parameter.getDefaultValue();
                String fieldType = parameter.getFieldType().toString();

                // If it's a single index parameter, check if it was provided
                if (parameter.isSingleIndex()) {
                    int index = parameter.getSingleIndex();
                    parseBuilder.beginControlFlow("if (positionalArgs.size() <= $L)", index);
                } else if (parameter.isRange() && !parameter.isUnbounded()) {
                    int rangeEnd = parameter.getRangeEnd();
                    parseBuilder.beginControlFlow("if (positionalArgs.size() <= $L)", rangeEnd);
                } else {
                    // Unbounded range or varargs - usually doesn't have a default value in the same sense,
                    // but if it does, apply it if empty
                    parseBuilder.beginControlFlow("if (positionalArgs.size() <= $L)", parameter.getRangeStart());
                }

                switch (fieldType) {
                    case "boolean", "java.lang.Boolean" ->
                            parseBuilder.addStatement("command.$L = $L", fieldName, Boolean.parseBoolean(defaultValue));
                    case "int", "java.lang.Integer" ->
                            parseBuilder.addStatement("command.$L = $L", fieldName, Integer.parseInt(defaultValue));
                    case "long", "java.lang.Long" ->
                            parseBuilder.addStatement("command.$L = $LL", fieldName, Long.parseLong(defaultValue));
                    case "float", "java.lang.Float" ->
                            parseBuilder.addStatement("command.$L = $Lf", fieldName, Float.parseFloat(defaultValue));
                    case "double", "java.lang.Double" ->
                            parseBuilder.addStatement("command.$L = $Ld", fieldName, Double.parseDouble(defaultValue));
                    default ->
                            parseBuilder.addStatement("command.$L = $S", fieldName, defaultValue);
                }
                parseBuilder.endControlFlow();
            }
        }
    }

    /**
     * Adds required option validation logic.
     */
    public static void addRequiredOptionValidation(MethodSpec.Builder parseBuilder, CommandModel model) {
        parseBuilder.addComment("Validate that all required options are provided");

        for (OptionModel option : model.getAllOptions()) {
            if (option.isRequired()) {
                String fieldName = option.getFieldName();
                String primaryName = option.getPrimaryName();

                parseBuilder.beginControlFlow("if (!parsedOptions.contains(\"$L\"))", fieldName);
                parseBuilder.addStatement("throw $T.missingRequiredOption(\"$L\")",
                        ParseException.class, primaryName);
                parseBuilder.endControlFlow();
            }
        }

        // Validate required positional parameters
        for (ParameterModel parameter : model.getParameters()) {
            if (parameter.isSingleIndex() && !parameter.hasDefaultValue()) {
                int index = parameter.getSingleIndex();
                parseBuilder.beginControlFlow("if (positionalArgs.size() <= $L)", index);
                parseBuilder.addStatement("throw new $T(\"Missing required positional parameter at index \" + $L)",
                        ParseException.class, index);
                parseBuilder.endControlFlow();
            }
        }
    }

    /**
     * Adds the findSubcommand method to the parser class.
     */
    public static void addFindSubcommandMethod(CommandModel model, TypeSpec.Builder classBuilder) {
        if (model.getSubcommands().isEmpty() && model.getMethodSubcommands().isEmpty()) {
            return;
        }

        MethodSpec.Builder builder = MethodSpec.methodBuilder("findSubcommandIndex")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(int.class)
                .addParameter(String[].class, "args")
                .addJavadoc("Finds the index of the first non-option argument that matches a subcommand name.\n")
                .addJavadoc("@param args Command line arguments\n")
                .addJavadoc("@return Index of subcommand in args, or -1 if no subcommand found");

        // If no arguments, return -1
        builder.beginControlFlow("if (args.length == 0)");
        builder.addStatement("return -1");
        builder.endControlFlow();

        // Scan arguments for the first non-option token; treat "--" as end of options
        builder.addStatement("boolean afterEndOfOptions = false");
        builder.beginControlFlow("for (int i = 0; i < args.length; i++)");
        builder.beginControlFlow("if (!afterEndOfOptions && \"--\".equals(args[i]))");
        builder.addStatement("afterEndOfOptions = true");
        builder.addStatement("continue");
        builder.endControlFlow();
        builder.beginControlFlow("if (!afterEndOfOptions && args[i].startsWith(\"-\"))");
        builder.addStatement("continue");
        builder.endControlFlow();
        for (var subcommand : model.getSubcommands()) {
            String subcommandName = subcommand.getName().toString();
            builder.beginControlFlow("if (\"$L\".equals(args[i]))", subcommandName);
            builder.addStatement("return i");
            builder.endControlFlow();
        }
        // First non-option token is not a known subcommand
        // Check method-based subcommands
        for (var methodSubcommand : model.getMethodSubcommands()) {
            String subcommandName = methodSubcommand.getName();
            builder.beginControlFlow("if (\"$L\".equals(args[i]))", subcommandName);
            builder.addStatement("return i");
            builder.endControlFlow();
        }
        // First non-option token is not a known subcommand
        builder.addStatement("return -1");
        builder.endControlFlow();

        builder.addStatement("return -1");
        classBuilder.addMethod(builder.build());
    }

    /**
     * Adds an early help check at the start of the parse method.
     * For simple commands, prints help and returns default command instance.
     * This is only used for commands without subcommands.
     */
    private static void addEarlyHelpCheck(MethodSpec.Builder parseBuilder, ClassName commandClassName, boolean mixinStandardHelpOptions) {
        parseBuilder.beginControlFlow("if (args.length > 0)");
        parseBuilder.beginControlFlow("for (String arg : args)");
        parseBuilder.beginControlFlow("if (\"--help\".equals(arg))");
        parseBuilder.addStatement("printHelp()");
        parseBuilder.addStatement("return new $T()", commandClassName);
        parseBuilder.endControlFlow();
        if (mixinStandardHelpOptions) {
            parseBuilder.beginControlFlow("if (\"-h\".equals(arg))");
            parseBuilder.addStatement("printHelp()");
            parseBuilder.addStatement("return new $T()", commandClassName);
            parseBuilder.endControlFlow();
        }
        parseBuilder.endControlFlow();
        parseBuilder.endControlFlow();
    }


}
