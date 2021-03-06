package com.nvankempen.csc444.mjava.ast.nodes;

import com.nvankempen.csc444.mjava.ast.analysis.TypeVisitor;
import com.nvankempen.csc444.mjava.ast.analysis.Visitor;
import com.nvankempen.csc444.mjava.ast.utils.Type;
import org.antlr.v4.runtime.Token;

public class RegularVarDeclaration extends VarDeclaration {
    private Type type;

    public RegularVarDeclaration(Type type, Identifier name, Expression value, Token start, Token stop) {
        super(name, value, start, stop);
        this.type = type;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public void setType(Type type) {
        this.type = type;
    }

    public void accept(Visitor v) {
        v.visit(this);
    }

    public Type accept(TypeVisitor v) {
        return v.visit(this);
    }
}
