package me.bechberger.femtocli.processor.model;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;
import me.bechberger.femtocli.annotations.Parameters;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing metadata for a method-based subcommand.
 *
 * <p>Method-based subcommands are methods within a command class
 * annotated with @Command that can be invoked as subcommands.</p>
 */
public class MethodSubcommandModel {

    private final ExecutableElement method;
    private final TypeElement parentClass;
    private final Command commandAnnotation;
    private final String name;
    private final List<OptionModel> options = new ArrayList<>();
    private final List<ParameterModel> parameters = new ArrayList<>();

    /**
     * Creates a MethodSubcommandModel from a method element.
     *
     * @param method The method element annotated with @Command
     * @param parentClass The parent class containing the method
     * @param commandAnnotation The @Command annotation on the method
     */
    public MethodSubcommandModel(ExecutableElement method, TypeElement parentClass, Command commandAnnotation) {
        this.method = method;
        this.parentClass = parentClass;
        this.commandAnnotation = commandAnnotation;
        this.name = commandAnnotation.name();
    }

    /**
     * Returns the method element for this subcommand.
     */
    public ExecutableElement getMethod() {
        return method;
    }

    /**
     * Returns the parent class containing this method.
     */
    public TypeElement getParentClass() {
        return parentClass;
    }

    /**
     * Returns the subcommand name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the description of this subcommand.
     */
    public String[] getDescription() {
        return commandAnnotation.description();
    }

    /**
     * Returns the qualified name of the parent class.
     */
    public String getParentQualifiedName() {
        return parentClass.getQualifiedName().toString();
    }

    /**
     * Returns the simple name of the parent class.
     */
    public String getParentClassName() {
        return parentClass.getSimpleName().toString();
    }

    /**
     * Returns the package name of the parent class.
     */
    public String getPackageName() {
        Element e = parentClass;
        while (e != null && e.getKind() != ElementKind.PACKAGE) {
            e = e.getEnclosingElement();
        }
        if (e instanceof PackageElement) {
            return ((PackageElement) e).getQualifiedName().toString();
        }
        return "";
    }

    /**
     * Returns all options for this method-based subcommand.
     */
    public List<OptionModel> getOptions() {
        return options;
    }

    /**
     * Adds an option to this method-based subcommand.
     */
    public void addOption(OptionModel option) {
        options.add(option);
    }

    /**
     * Returns all parameters for this method-based subcommand.
     */
    public List<ParameterModel> getParameters() {
        return parameters;
    }

    /**
     * Adds a parameter to this method-based subcommand.
     */
    public void addParameter(ParameterModel parameter) {
        parameters.add(parameter);
    }

    /**
     * Returns the return type of this method.
     */
    public String getReturnType() {
        return method.getReturnType().toString();
    }

    /**
     * Returns whether this method returns an integer.
     */
    public boolean returnsInteger() {
        return "int".equals(getReturnType());
    }

    /**
     * Returns whether this method returns void.
     */
    public boolean returnsVoid() {
        return "void".equals(getReturnType());
    }
}
