package com.nvankempen.csc444.mjava.ast.nodes;

import com.nvankempen.csc444.mjava.ast.analysis.TypeVisitor;
import com.nvankempen.csc444.mjava.ast.analysis.Visitor;
import com.nvankempen.csc444.mjava.ast.utils.Type;
import com.nvankempen.csc444.mjava.codegen.CodeGenerationVisitor;
import org.antlr.v4.runtime.Token;

public class If extends Statement {
    private Expression condition;
    private Statement trueStatement;
    private Statement falseStatement;

    public If(Expression condition, Statement trueStatement, Statement falseStatement, Token start, Token stop) {
        super(start, stop);
        this.condition = condition;
        this.trueStatement = trueStatement;
        this.falseStatement = falseStatement;
    }

    public If(Expression condition, Statement statement, Token start, Token stop) {
        this(condition, statement, null, start, stop);
    }

    public boolean hasFalseStatement() {
        return falseStatement != null;
    }

    public Expression getCondition() {
        return condition;
    }

    public Statement getTrueStatement() {
        return trueStatement;
    }

    public Statement getFalseStatement() {
        return falseStatement;
    }

    public void accept(Visitor v) {
        v.visit(this);
    }

    public Type accept(TypeVisitor v) {
        return v.visit(this);
    }

    public void accept(CodeGenerationVisitor v) {
        v.visit(this);
    }
}
