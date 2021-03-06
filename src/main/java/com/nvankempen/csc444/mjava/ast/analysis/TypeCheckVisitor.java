package com.nvankempen.csc444.mjava.ast.analysis;

import com.nvankempen.csc444.mjava.ast.nodes.*;
import com.nvankempen.csc444.mjava.ast.utils.*;
import org.antlr.v4.runtime.misc.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class TypeCheckVisitor implements TypeVisitor {

    private ErrorHandler errorHandler = new DefaultErrorHandler();
    private ClassDeclaration current;
    private Map<Identifier, ClassDeclaration> classes;
    private Map<Identifier, Type> instance;
    private Map<Identifier, Type> method;
    private Map<Identifier, Type> parameters;

    public void setErrorHandler(ErrorHandler handler) {
        errorHandler = handler;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    private void setUnknown(Identifier name) {
        if (parameters.containsKey(name)) {
            parameters.put(name, new UnknownType());
        }

        if (method.containsKey(name)) {
            method.put(name, new UnknownType());
        }

        instance.put(name, new UnknownType());
    }

    private ClassDeclaration getSuperClass(ClassDeclaration sub) {
        if (!sub.hasSuperClass()) {
            return null;
        }

        return classes.get(sub.getSuperclass());
    }

    @Override
    public Type visit(Program program) {
        classes = new HashMap<>();

        // Build type table
        for (ClassDeclaration declaration : program.getClasses()) {
            if (classes.containsKey(declaration.getName())) {
                errorHandler.error(declaration.getName().getStart(), "The class %s has already been declared line %d.", classes.get(declaration.getName()).getName().getStart().getLine());
            } else if (program.getMainClass().getName().equals(declaration.getName())) {
                errorHandler.error(declaration.getName().getStart(), "The class %s has already been declared line %d.", program.getMainClass().getName().getStart().getLine());
            } else {
                classes.put(declaration.getName(), declaration);
            }
        }

        program.getMainClass().accept(this);
        program.getClasses().forEach(c -> c.accept(this));

        return null;
    }

    @Override
    public Type visit(ClassDeclaration declaration) {
        if (declaration.hasSuperClass() && !classes.containsKey(declaration.getSuperclass())) {
            errorHandler.error(declaration.getSuperclass().getStart(), "The class %s does not exist.", declaration.getSuperclass().getName());
            declaration.setSuperclass(null);
        }

        for (ClassDeclaration sup = getSuperClass(declaration); sup != null; sup = getSuperClass(sup)) {
            if (declaration.getName().equals(sup.getName())) {
                errorHandler.error(declaration.getSuperclass().getStart(), "Inheritance cycles are not allowed.");
                declaration.setSuperclass(null);
            }
        }

        current = declaration;
        instance = new HashMap<>();

        for (VarDeclaration variable : declaration.getVariables()) {
            variable.accept(this);
            if (instance.containsKey(variable.getName())) {
                errorHandler.error(variable.getStart(), variable.getStop(), "Variable %s has already been defined in this scope.", variable.getName());
                setUnknown(variable.getName());
            } else {
                instance.put(variable.getName(), variable.getType());
            }
        }

        List<MethodDeclaration> methods = declaration.getMethods();
        for (int i = 0; i < methods.size(); ++i) {
            for (int j = i + 1; j < methods.size(); ++j) {
                if (methods.get(i).getName().equals(methods.get(j).getName()) && methods.get(i).getParameters().size() == methods.get(j).getParameters().size()) {
                    // Both methods have the same name.
                    boolean different = false;
                    for (int k = 0; k < methods.get(i).getParameters().size(); ++k) {
                        if (!methods.get(i).getParameters().get(k).getType().equals(methods.get(j).getParameters().get(k).getType())) {
                            different = true;
                            break;
                        }
                    }

                    if (!different) {
                        errorHandler.error(
                                methods.get(j).getStart(), methods.get(j).getStop(),
                                "%s(%s) has already been declared line %d.",
                                methods.get(i).getName(),
                                String.join(", ", methods.get(i).getParameters().stream().map(x -> x.getType().getName()).collect(Collectors.toList())),
                                methods.get(i).getStart().getLine()
                        );

                        methods.get(i).setType(new UnknownType());
                        methods.get(j).setType(new UnknownType());
                    }
                }
            }

            methods.get(i).accept(this);
        }

        return null;
    }

    @Override
    public Type visit(RegularVarDeclaration declaration) {
        // Check if type exists.
        Type type = declaration.getType();
        if (!(type.isInt() || type.isIntArray() || type.isBoolean())) {
            for (Identifier name : classes.keySet()) {
                if (name.getName().equals(type.getName())) {
                    return null;
                }
            }

            errorHandler.error(declaration.getStart(), declaration.getStop(), "The type %s does not exist.", declaration.getType().getName());
            declaration.setType(new UnknownType());
            return null;
        }

        if (declaration.hasValue()) {
            Type expression = declaration.getValue().accept(this);

            if (expression.conformsTo(type, classes) == -1) {
                errorHandler.error(declaration.getStart(), declaration.getStop(), "Type mismatch. Trying to assign a(n) %s to a(n) %s variable.", expression.getName(), type.getName());
                setUnknown(declaration.getName());
            }
        }

        return null;
    }

    @Override
    public Type visit(UntypedVarDeclaration declaration) {
        declaration.setType(declaration.getValue().accept(this));
        return null;
    }

    @Override
    public Type visit(MethodDeclaration declaration) {
        method = new HashMap<>();
        parameters = new HashMap<>();

        for (Formal parameter : declaration.getParameters()) {
            parameter.accept(this);
            if (parameters.containsKey(parameter.getName())) {
                errorHandler.error(parameter.getStart(), parameter.getStop(), "Parameter %s has already been defined in this scope.", parameter.getName());
            } else {
                parameters.put(parameter.getName(), parameter.getType());
            }
        }

        for (VarDeclaration variable : declaration.getVariables()) {
            variable.accept(this);
            if (parameters.containsKey(variable.getName()) || method.containsKey(variable.getName())) {
                errorHandler.error(variable.getStart(), variable.getStop(), "Variable %s has already been defined in this scope.", variable.getName());
            } else {
                method.put(variable.getName(), variable.getType());
            }
        }

        for (Statement statement : declaration.getStatements()) {
            statement.accept(this);
        }

        Type ret = declaration.getReturn().accept(this);
        if (!ret.equals(declaration.getType())) {
            errorHandler.error(declaration.getReturn().getStart(), declaration.getReturn().getStop(), "Method declares returning a(n) %s, but returns a(n) %s.", declaration.getType().getName(), ret.getName());
            declaration.setType(new UnknownType());
        }

        return null;
    }

    @Override
    public Type visit(Block block) {
        for (Statement statement : block.getStatements()) {
            statement.accept(this);
        }
        return null;
    }

    @Override
    public Type visit(If statement) {
        Type condition = statement.getCondition().accept(this);
        if (!condition.isBoolean()) {
            errorHandler.error(statement.getCondition().getStart(), statement.getCondition().getStop(), "Expected a boolean. Got a(n) %s.", condition.getName());
        }

        statement.getTrueStatement().accept(this);
        if (statement.hasFalseStatement()) statement.getFalseStatement().accept(this);

        return null;
    }

    @Override
    public Type visit(While statement) {
        Type condition = statement.getCondition().accept(this);
        if (!condition.isBoolean()) {
            errorHandler.error(statement.getCondition().getStart(), statement.getCondition().getStop(), "Expected a boolean. Got a(n) %s.", condition.getName());
        }

        statement.getStatement().accept(this);
        return null;
    }

    @Override
    public Type visit(Print statement) {
        Type type = statement.getExpression().accept(this);
        if (!type.isInt()) {
            errorHandler.error(statement.getExpression().getStart(), statement.getExpression().getStop(), "Expected an integer. Got a(n) %s.", type.getName());
        }

        return null;
    }

    @Override
    public Type visit(VarAssign statement) {
        Type variable = statement.getVariable().accept(this);
        Type expression = statement.getValue().accept(this);

        if (expression.conformsTo(variable, classes) == -1) {
            errorHandler.error(statement.getStart(), statement.getStop(), "Type mismatch. Trying to assign a(n) %s to a(n) %s variable.", expression.getName(), variable.getName());
            setUnknown(statement.getVariable());
        }

        return null;
    }

    @Override
    public Type visit(ArrayAssign statement) {
        Type array = statement.getArray().accept(this);
        Type index = statement.getIndex().accept(this);
        Type value = statement.getValue().accept(this);

        if (!array.isIntArray()) {
            errorHandler.error(statement.getArray().getStart(), statement.getArray().getStop(), "Expected an integer array. Got a(n) %s.", array.getName());
        }
        if (!index.isInt()) {
            errorHandler.error(statement.getIndex().getStart(), statement.getIndex().getStop(), "Expected an integer. Got a(n) %s.", index.getName());
        }
        if (!value.isInt()) {
            errorHandler.error(statement.getValue().getStart(), statement.getValue().getStop(), "Expected an integer. Got a(n) %s.", value.getName());
        }

        return null;
    }

    @Override
    public Type visit(And expression) {
        Type left = expression.getLeft().accept(this);
        Type right = expression.getRight().accept(this);

        if (!left.isBoolean()) {
            errorHandler.error(expression.getLeft().getStart(), expression.getLeft().getStop(), "Expected a boolean. Got a(n) %s.", left.getName());
        }

        if (!right.isBoolean()) {
            errorHandler.error(expression.getRight().getStart(), expression.getRight().getStop(), "Expected a boolean. Got a(n) %s.", right.getName());
        }

        if (right.isBoolean() && left.isBoolean()) {
            return new BooleanType();
        }

        return new UnknownType();
    }

    @Override
    public Type visit(Not expression) {
        Type type = expression.getExpression().accept(this);
        if (!type.isBoolean()) {
            errorHandler.error(expression.getExpression().getStart(), expression.getExpression().getStop(), "Expected a boolean. Got a(n) %s.", type.getName());
            return new UnknownType();
        }
        return new BooleanType();
    }

    @Override
    public Type visit(LessThan expression) {
        Type left = expression.getLeft().accept(this);
        Type right = expression.getRight().accept(this);

        if (!left.isInt()) {
            errorHandler.error(expression.getLeft().getStart(), expression.getLeft().getStop(), "Expected an integer. Got a(n) %s.", left.getName());
        }

        if (!right.isInt()) {
            errorHandler.error(expression.getRight().getStart(), expression.getRight().getStop(), "Expected an integer. Got a(n) %s.", right.getName());
        }

        if (right.isBoolean() && left.isBoolean()) {
            return new BooleanType();
        }

        return new UnknownType();
    }

    @Override
    public Type visit(Plus expression) {
        Type left = expression.getLeft().accept(this);
        Type right = expression.getRight().accept(this);

        if (!left.isInt()) {
            errorHandler.error(expression.getLeft().getStart(), expression.getLeft().getStop(), "Expected an integer. Got a(n) %s.", left.getName());
        }

        if (!right.isInt()) {
            errorHandler.error(expression.getRight().getStart(), expression.getRight().getStop(), "Expected an integer. Got a(n) %s.", right.getName());
        }

        if (right.isBoolean() && left.isBoolean()) {
            return new IntegerType();
        }

        return new UnknownType();
    }

    @Override
    public Type visit(Minus expression) {
        Type left = expression.getLeft().accept(this);
        Type right = expression.getRight().accept(this);

        if (!left.isInt()) {
            errorHandler.error(expression.getLeft().getStart(), expression.getLeft().getStop(), "Expected an integer. Got a(n) %s.", left.getName());
        }

        if (!right.isInt()) {
            errorHandler.error(expression.getRight().getStart(), expression.getRight().getStop(), "Expected an integer. Got a(n) %s.", right.getName());
        }

        if (right.isBoolean() && left.isBoolean()) {
            return new IntegerType();
        }

        return new UnknownType();
    }

    @Override
    public Type visit(Times expression) {
        Type left = expression.getLeft().accept(this);
        Type right = expression.getRight().accept(this);

        if (!left.isInt()) {
            errorHandler.error(expression.getLeft().getStart(), expression.getLeft().getStop(), "Expected an integer. Got a(n) %s.", left.getName());
        }

        if (!right.isInt()) {
            errorHandler.error(expression.getRight().getStart(), expression.getRight().getStop(), "Expected an integer. Got a(n) %s.", right.getName());
        }

        if (right.isBoolean() && left.isBoolean()) {
            return new IntegerType();
        }

        return new UnknownType();
    }

    @Override
    public Type visit(ArrayLookup expression) {
        Type array = expression.getArray().accept(this);
        Type index = expression.getIndex().accept(this);

        if (!array.isIntArray()) {
            errorHandler.error(expression.getArray().getStart(), expression.getArray().getStop(), "Expected an integer array. Got a(n) %s.", array.getName());
        }

        if (!index.isInt()) {
            errorHandler.error(expression.getIndex().getStart(), expression.getIndex().getStop(), "Expected an integer. Got a(n) %s.", index.getName());
        }

        if (array.isIntArray() && index.isInt()) {
            return new IntegerType();
        }

        return new UnknownType();
    }

    @Override
    public Type visit(ArrayLength expression) {
        Type array = expression.getArray().accept(this);

        if (!array.isIntArray()) {
            errorHandler.error(expression.getArray().getStart(), expression.getArray().getStop(), "Expected an integer array. Got a(n) %s.", array.getName());
            return new UnknownType();
        }

        return new IntegerType();
    }

    @Override
    public Type visit(Call expression) {
        Type exp = expression.getObject().accept(this);
        List<Type> arguments = expression.getArguments().stream().map(e -> e.accept(this)).collect(Collectors.toList());

        if (exp.isUnknown()) {
            return new UnknownType();
        }

        if (!(exp instanceof IdentifierType)) {
            errorHandler.error(
                    expression.getObject().getStart(), expression.getObject().getStop(),
                    "Type %s does not have a method %s(%s).",
                    exp.getName(), expression.getMethod(),
                    arguments.stream().map(Type::getName).collect(Collectors.toList())
            );
            return new UnknownType();
        }

        for (Identifier c = ((IdentifierType) exp).getIdentifier(); c != null; c = classes.get(c).getSuperclass()) {
            List<Pair<MethodDeclaration, Integer>> matches = new ArrayList<>();
            for (MethodDeclaration method : classes.get(c).getMethods()) {
                if (method.getName().equals(expression.getMethod()) && method.getParameters().size() == arguments.size()) {
                    int match = 0;
                    for (int i = 0; i < arguments.size(); ++i) {
                        int distance = arguments.get(i).conformsTo(method.getParameters().get(i).getType(), classes);
                        if (distance == -1) {
                            match = -1;
                            break;
                        } else {
                            match += (1 << (i + 1)) * distance;
                        }
                    }

                    if (match != -1) {
                        matches.add(new Pair<>(method, match));
                    }
                }
            }

            if (matches.size() != 0) {
                Pair<MethodDeclaration, Integer> min = matches.get(0);

                for (Pair<MethodDeclaration, Integer> p : matches) {
                    if (p.b < min.b) {
                        min = p;
                    }
                }

                expression.setDeclaration(min.a);
                expression.setObjectType(classes.get(c));
                return min.a.getType();
            }
        }

        errorHandler.error(
                expression.getStart(), expression.getStop(),
                "Type %s does not have a method %s(%s).",
                exp.getName(), expression.getMethod(),
                String.join(", ", arguments.stream().map(Type::getName).collect(Collectors.toList()))
        );

        return new UnknownType();
    }

    @Override
    public Type visit(IntegerLiteral expression) {
        return new IntegerType();
    }

    @Override
    public Type visit(BooleanLiteral expression) {
        return new BooleanType();
    }

    @Override
    public Type visit(NewArray expression) {
        Type length = expression.getLength().accept(this);

        if (!length.isInt()) {
            errorHandler.error(expression.getLength().getStart(), expression.getLength().getStop(), "Expected an integer. Got a(n) %s.", length.getName());
        }

        return new IntegerArrayType();
    }

    @Override
    public Type visit(NewObject expression) {
        if (classes.containsKey(expression.getClassName())) {
            return new IdentifierType(expression.getClassName());
        }

        errorHandler.error(expression.getStart(), expression.getStop(), "The type %s does not exist.", expression.getClassName());
        return new UnknownType();
    }

    @Override
    public Type visit(This expression) {
        return new IdentifierType(current.getName());
    }

    @Override
    public Type visit(Identifier identifier) {
        Type type = null;

        if (parameters.containsKey(identifier)) {
            type = parameters.get(identifier);
        } else if (method.containsKey(identifier)) {
            type = method.get(identifier);
        } else if (instance.containsKey(identifier)) {
            type = instance.get(identifier);
        } else {
            for (ClassDeclaration sub = current; sub != null && type == null; sub = getSuperClass(sub)) {
                for (VarDeclaration variable : sub.getVariables()) {
                    if (variable.getName().equals(identifier)) {
                        type = variable.getType();
                        break;
                    }
                }
            }
        }

        if (type == null) {
            errorHandler.error(identifier.getStart(), "The variable %s has not been declared.", identifier);
            return new UnknownType();
        }

        return type;
    }

    @Override
    public Type visit(Formal formal) {
        // Check if type exists.
        Type type = formal.getType();
        if (!(type.isInt() || type.isIntArray() || type.isBoolean())) {
            for (Identifier name : classes.keySet()) {
                if (name.getName().equals(type.getName())) {
                    return null;
                }
            }

            errorHandler.error(formal.getStart(), formal.getStop(), "The type %s does not exist.", formal.getType().getName());
            formal.setType(new UnknownType());
            return null;
        }

        return null;
    }

    @Override
    public Type visit(MainClass main) {
        instance = new HashMap<>();
        method = new HashMap<>();
        parameters = new HashMap<>();

        main.getStatement().accept(this);
        return null;
    }
}
