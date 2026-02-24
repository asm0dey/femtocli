package me.bechberger.femtocli.processor;

import com.google.auto.service.AutoService;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Mixin;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;
import me.bechberger.femtocli.processor.codegen.ParserGenerator;
import me.bechberger.femtocli.processor.model.*;

import javax.annotation.processing.*;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * Annotation processor for FemtoCli code generation.
 *
 * <p>Generates parser classes for classes annotated with {@link Command}.
 * The generated parser class for a command class named X will be named XParser.</p>
 *
 * <p>Processing steps:</p>
 * <ol>
 *   <li>Collect all @Command annotated classes</li>
 *   <li>Extract model metadata (options, parameters, subcommands, mixins)</li>
 *   <li>Generate XParser class using JavaPoet</li>
 * </ol>
 */
@SupportedAnnotationTypes({
    "me.bechberger.femtocli.annotations.Command",
    "me.bechberger.femtocli.annotations.Option",
    "me.bechberger.femtocli.annotations.Parameters",
    "me.bechberger.femtocli.annotations.Mixin"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class CliProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Filer filer;
    private Messager messager;

    private final Map<String, CommandModel> commandModels = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        commandModels.clear();

        try {
            // Process all @Command annotated classes first
            Set<? extends Element> commandElements = roundEnv.getElementsAnnotatedWith(Command.class);
            for (Element element : commandElements) {
                if (element.getKind() == ElementKind.CLASS) {
                    processCommandClass((TypeElement) element);
                }
            }

            // Process method-based subcommands after all classes are processed
            for (Element element : commandElements) {
                if (element.getKind() == ElementKind.METHOD) {
                    processMethodSubcommand((ExecutableElement) element);
                }
            }

            // Generate parser classes after all models are collected
            generateParsers();

        } catch (ProcessingException e) {
            error(e.getElement(), e.getMessage());
        }

        return true;
    }

    /**
     * Process a single @Command annotated class.
     */
    private void processCommandClass(TypeElement element) throws ProcessingException {
        Command commandAnnotation = element.getAnnotation(Command.class);

        if (commandAnnotation == null) {
            return;
        }

        String commandName = commandAnnotation.name();
        if (commandName.isEmpty()) {
            throw new ProcessingException(element, "@Command.name() cannot be empty");
        }

        // Check if this command has already been processed
        if (commandModels.containsKey(commandName)) {
            throw new ProcessingException(element,
                "Duplicate command name: '" + commandName + "'. Each command must have a unique name.");
        }

        String packageName = elementUtils.getPackageOf(element).getQualifiedName().toString();
        CommandModel model = new CommandModel(element, commandAnnotation, packageName);
        commandModels.put(commandName, model);

        // Extract options from fields
        extractOptions(element, model);

        // Extract parameters from fields
        extractParameters(element, model);

        // Extract mixins from fields
        extractMixins(element, model);

        // Extract subcommands
        extractSubcommands(element, model);
    }

    /**
     * Process a method-based subcommand (@Command on a method within a @Command class).
     */
    private void processMethodSubcommand(ExecutableElement method) throws ProcessingException {
        Command commandAnnotation = method.getAnnotation(Command.class);

        if (commandAnnotation == null) {
            return;
        }

        String subcommandName = commandAnnotation.name();
        if (subcommandName.isEmpty()) {
            throw new ProcessingException(method, "@Command.name() cannot be empty for method-based subcommand");
        }

        // Find the parent class
        TypeElement parentClass = (TypeElement) method.getEnclosingElement();

        // Check if the parent class is a @Command class
        Command parentAnnotation = parentClass.getAnnotation(Command.class);
        if (parentAnnotation == null) {
            throw new ProcessingException(method,
                "Method-based subcommand '" + subcommandName + "' must be in a class annotated with @Command");
        }

        // Find or create the parent command model
        String parentCommandName = parentAnnotation.name();
        CommandModel parentModel = commandModels.get(parentCommandName);
        if (parentModel == null) {
            throw new ProcessingException(method,
                "Parent command '" + parentCommandName + "' for method-based subcommand '" + subcommandName + "' not found");
        }

        // Check for duplicate method subcommand names
        for (MethodSubcommandModel existing : parentModel.getMethodSubcommands()) {
            if (existing.getName().equals(subcommandName)) {
                throw new ProcessingException(method,
                    "Duplicate method-based subcommand name: '" + subcommandName + "'");
            }
        }

        // Create and add the method subcommand model
        MethodSubcommandModel methodSubcommand = new MethodSubcommandModel(method, parentClass, commandAnnotation);

        // Extract @Option annotations from method parameters
        extractMethodOptionParameters(method, methodSubcommand);

        // Extract @Parameters annotations from method parameters
        extractMethodPositionalParameters(method, methodSubcommand);

        parentModel.addMethodSubcommand(methodSubcommand);
    }

    /**
     * Extract @Option annotated parameters from a method.
     */
    private void extractMethodOptionParameters(ExecutableElement method, MethodSubcommandModel model) throws ProcessingException {
        for (VariableElement param : method.getParameters()) {
            Option option = param.getAnnotation(Option.class);
            if (option != null) {
                OptionModel optionModel = new OptionModel(param, option);
                model.addOption(optionModel);
            }
        }
    }

    /**
     * Extract @Parameters annotated parameters from a method.
     */
    private void extractMethodPositionalParameters(ExecutableElement method, MethodSubcommandModel model) throws ProcessingException {
        for (VariableElement param : method.getParameters()) {
            Parameters parameters = param.getAnnotation(Parameters.class);
            if (parameters != null) {
                ParameterModel parameterModel = new ParameterModel(param, parameters);
                model.addParameter(parameterModel);
            }
        }
    }

    /**
     * Extract @Option annotated fields from the command class.
     */
    private void extractOptions(TypeElement element, CommandModel model) throws ProcessingException {
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                Option option = enclosed.getAnnotation(Option.class);
                if (option != null) {
                    OptionModel optionModel = new OptionModel((VariableElement) enclosed, option);
                    model.addOption(optionModel);
                }
            }
        }
    }

    /**
     * Extract @Parameters annotated fields from the command class.
     */
    private void extractParameters(TypeElement element, CommandModel model) throws ProcessingException {
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                Parameters parameters = enclosed.getAnnotation(Parameters.class);
                if (parameters != null) {
                    ParameterModel parameterModel = new ParameterModel((VariableElement) enclosed, parameters);
                    model.addParameter(parameterModel);
                }
            }
        }
    }

    /**
     * Extract @Mixin annotated fields from the command class and populate options from mixin classes.
     */
    private void extractMixins(TypeElement element, CommandModel model) throws ProcessingException {
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                Mixin mixin = enclosed.getAnnotation(Mixin.class);
                if (mixin != null) {
                    MixinModel mixinModel = new MixinModel((VariableElement) enclosed, mixin);
                    model.addMixin(mixinModel);

                    // Extract options from mixin class
                    extractOptionsFromMixinClass(mixinModel);
                }
            }
        }
    }

    /**
     * Extract @Option annotated fields from a mixin class.
     * This allows mixin classes to share their options with commands.
     */
    private void extractOptionsFromMixinClass(MixinModel mixinModel) throws ProcessingException {
        String mixinQualifiedName = mixinModel.getMixinQualifiedName();
        if (mixinQualifiedName.isEmpty()) {
            return;
        }

        TypeElement mixinElement = elementUtils.getTypeElement(mixinQualifiedName);
        if (mixinElement == null) {
            // Mixin class may not be available yet (not compiled), try again after round
            return;
        }

        String mixinFieldName = mixinModel.getFieldName();
        for (Element enclosed : mixinElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                Option option = enclosed.getAnnotation(Option.class);
                if (option != null) {
                    // Pass the mixin field name so OptionModel knows it's from a mixin
                    OptionModel optionModel = new OptionModel((VariableElement) enclosed, option, mixinFieldName);
                    mixinModel.addOption(optionModel);
                }
            }
        }
    }

    /**
     * Extract subcommands from @Command annotation.
     */
    private void extractSubcommands(TypeElement element, CommandModel model) throws ProcessingException {
        Command commandAnnotation = element.getAnnotation(Command.class);
        List<? extends TypeMirror> subcommandTypes = Collections.emptyList();

        try {
            Class<?>[] subcommandClasses = commandAnnotation.subcommands();
            // If we reach here, the classes are already compiled and accessible
            // This is unlikely during annotation processing but possible
            for (Class<?> subcommandClass : subcommandClasses) {
                TypeElement subcommandElement = elementUtils.getTypeElement(subcommandClass.getCanonicalName());
                if (subcommandElement != null) {
                    SubcommandModel subcommandModel = new SubcommandModel(subcommandElement);
                    model.addSubcommand(subcommandModel);
                }
            }
            return;
        } catch (javax.lang.model.type.MirroredTypesException e) {
            subcommandTypes = e.getTypeMirrors();
        } catch (Exception e) {
            // Other exceptions, ignore
        }

        for (TypeMirror subcommandType : subcommandTypes) {
            Element subcommandElement = typeUtils.asElement(subcommandType);
            if (subcommandElement instanceof TypeElement) {
                SubcommandModel subcommandModel = new SubcommandModel((TypeElement) subcommandElement);
                model.addSubcommand(subcommandModel);
            }
        }
    }

    /**
     * Generate parser classes for all collected command models.
     */
    private void generateParsers() throws ProcessingException {
        ParserGenerator generator = new ParserGenerator();

        for (CommandModel model : commandModels.values()) {
            var parserFile = generator.generate(model);
            try {
                parserFile.writeTo(filer);
                messager.printMessage(Diagnostic.Kind.NOTE,
                    "Generated parser: " + parserFile.packageName() + "." + parserFile.typeSpec().name());
            } catch (Exception e) {
                throw new ProcessingException(model.getElement(),
                    "Failed to generate parser for command '" + model.getName() + "': " + e.getMessage());
            }
        }
    }

    private void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private void warning(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.WARNING, message, element);
    }
}
