/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.math.expr;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.druid.java.util.common.StringUtils;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mechanism by which Druid expressions can define new functions for the Druid expression language. When
 * {@link ExprListenerImpl} is creating a {@link FunctionExpr}, {@link ExprMacroTable} will first be checked to find
 * the function by name, falling back to {@link Parser#getFunction(String)} to map to a built-in {@link Function} if
 * none is defined in the macro table.
 */
public class ExprMacroTable
{
  private static final ExprMacroTable NIL = new ExprMacroTable(Collections.emptyList());

  private final Map<String, ExprMacro> macroMap;

  public ExprMacroTable(final List<ExprMacro> macros)
  {
    this.macroMap = macros.stream().collect(
        Collectors.toMap(
            m -> StringUtils.toLowerCase(m.name()),
            m -> m
        )
    );
  }

  public static ExprMacroTable nil()
  {
    return NIL;
  }

  public List<ExprMacro> getMacros()
  {
    return ImmutableList.copyOf(macroMap.values());
  }

  /**
   * Returns an expr corresponding to a function call if this table has an entry for {@code functionName}.
   * Otherwise, returns null.
   *
   * @param functionName function name
   * @param args         function arguments
   *
   * @return expr for this function call, or null
   */
  @Nullable
  public Expr get(final String functionName, final List<Expr> args)
  {
    final ExprMacro exprMacro = macroMap.get(StringUtils.toLowerCase(functionName));
    if (exprMacro == null) {
      return null;
    }

    return exprMacro.apply(args);
  }

  public interface ExprMacro
  {
    String name();

    Expr apply(List<Expr> args);
  }

  /**
   * Base class for single argument {@link ExprMacro} function {@link Expr}
   */
  public abstract static class BaseScalarUnivariateMacroFunctionExpr implements Expr
  {
    protected final Expr arg;

    public BaseScalarUnivariateMacroFunctionExpr(Expr arg)
    {
      this.arg = arg;
    }

    @Override
    public void visit(final Visitor visitor)
    {
      arg.visit(visitor);
      visitor.visit(this);
    }

    @Override
    public BindingDetails analyzeInputs()
    {
      final String identifier = arg.getIdentifierIfIdentifier();
      if (identifier == null) {
        return arg.analyzeInputs();
      }
      return arg.analyzeInputs().mergeWithScalars(ImmutableSet.of(identifier));
    }
  }

  /**
   * Base class for multi-argument {@link ExprMacro} function {@link Expr}
   */
  public abstract static class BaseScalarMacroFunctionExpr implements Expr
  {
    protected final List<Expr> args;

    public BaseScalarMacroFunctionExpr(final List<Expr> args)
    {
      this.args = args;
    }


    @Override
    public void visit(final Visitor visitor)
    {
      for (Expr arg : args) {
        arg.visit(visitor);
      }
      visitor.visit(this);
    }

    @Override
    public BindingDetails analyzeInputs()
    {
      Set<String> scalars = new HashSet<>();
      BindingDetails accumulator = new BindingDetails();
      for (Expr arg : args) {
        final String identifier = arg.getIdentifierIfIdentifier();
        if (identifier != null) {
          scalars.add(identifier);
        }
        accumulator = accumulator.merge(arg.analyzeInputs());
      }
      return accumulator.mergeWithScalars(scalars);
    }
  }
}
