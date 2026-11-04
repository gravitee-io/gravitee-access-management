/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.service.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import ognl.ASTAssign;
import ognl.ASTConst;
import ognl.ASTCtor;
import ognl.ASTEval;
import ognl.ASTInstanceof;
import ognl.ASTMethod;
import ognl.ASTProperty;
import ognl.ASTStaticField;
import ognl.ASTStaticMethod;
import ognl.ASTVarRef;
import ognl.Node;
import ognl.Ognl;
import ognl.OgnlException;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.standard.expression.IStandardVariableExpression;
import org.thymeleaf.standard.expression.IStandardVariableExpressionEvaluator;
import org.thymeleaf.standard.expression.OGNLVariableExpressionEvaluator;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;

/**
 * Variable expression evaluator for templates whose content is authored by users (custom forms).
 *
 * <p>Thymeleaf only blocks a fixed list of type names, which leaves every other class on the classpath reachable
 * from an expression. This evaluator inspects the parsed OGNL expression before it is evaluated and rejects the
 * constructs that allow to leave the model exposed by the template context: object creation, static access,
 * assignment, type checks, runtime evaluation of expressions held in strings, OGNL internals and reflective members. Property navigation, method calls on the model
 * and operators remain available, as they are what the default templates rely on.
 */
public class RestrictedExpressionEvaluator implements IStandardVariableExpressionEvaluator {

    private static final int MAX_CACHED_EXPRESSIONS = 5000;

    private static final Set<String> FORBIDDEN_MEMBERS = Set.of(
            "class", "getclass", "classloader", "getclassloader", "forname", "loadclass", "newinstance",
            "getruntime", "getmethod", "getmethods", "getdeclaredmethod", "getdeclaredmethods",
            "getconstructor", "getconstructors", "getdeclaredconstructor", "getdeclaredconstructors",
            "getfield", "getfields", "getdeclaredfield", "getdeclaredfields", "setaccessible", "invoke",
            "getmodule", "getdeclaringclass", "getprotectiondomain", "getresource", "getresourceasstream");

    private final IStandardVariableExpressionEvaluator delegate = new OGNLVariableExpressionEvaluator(true);
    private final Set<String> validated = ConcurrentHashMap.newKeySet();

    @Override
    public Object evaluate(IExpressionContext context, IStandardVariableExpression expression, StandardExpressionExecutionContext expContext) {
        validate(expression.getExpression());
        return delegate.evaluate(context, expression, expContext);
    }

    /**
     * Validates every {@code ${...}} and {@code *{...}} expression embedded in a Thymeleaf template, without
     * rendering it. Used to reject a custom form at save time rather than at render time.
     *
     * @throws TemplateProcessingException if any embedded expression is not valid or uses a construct that is not allowed
     */
    public void validateContent(String content) {
        if (content == null) {
            return;
        }
        for (String expression : extractExpressions(content)) {
            validate(expression);
        }
    }

    private static List<String> extractExpressions(String content) {
        List<String> expressions = new ArrayList<>();
        for (String marker : List.of("${", "*{")) {
            int from = 0;
            while ((from = content.indexOf(marker, from)) >= 0) {
                int depth = 0;
                int end = from + 1;
                for (; end < content.length(); end++) {
                    char c = content.charAt(end);
                    if (c == '{') {
                        depth++;
                    } else if (c == '}' && --depth == 0) {
                        break;
                    }
                }
                expressions.add(content.substring(from + 2, end));
                from = end;
            }
        }
        return expressions;
    }

    /**
     * @throws TemplateProcessingException if the expression is not valid or uses a construct that is not allowed
     */
    private void validate(String expression) {
        if (validated.contains(expression)) {
            return;
        }
        try {
            check((Node) Ognl.parseExpression(expression), expression);
        } catch (OgnlException e) {
            throw new TemplateProcessingException("Invalid expression [" + expression + "]", e);
        }
        if (validated.size() >= MAX_CACHED_EXPRESSIONS) {
            validated.clear();
        }
        validated.add(expression);
    }

    private void check(Node node, String expression) {
        if (node instanceof ASTCtor || node instanceof ASTStaticMethod || node instanceof ASTStaticField
                || node instanceof ASTAssign || node instanceof ASTInstanceof || node instanceof ASTEval) {
            throw forbidden(expression);
        }
        if (node instanceof ASTVarRef varRef && isForbiddenVariable(varRef.toString().substring(1))) {
            throw forbidden(expression);
        }
        if (node instanceof ASTMethod method && isForbiddenMember(method.getMethodName())) {
            throw forbidden(expression);
        }
        if (node instanceof ASTProperty property) {
            checkProperty(property, expression);
        }
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            check(node.jjtGetChild(i), expression);
        }
    }

    // a property selector computed at runtime could be turned into a forbidden member name
    private void checkProperty(ASTProperty property, String expression) {
        if (property.jjtGetNumChildren() == 1 && property.jjtGetChild(0) instanceof ASTConst constant) {
            if (constant.getValue() instanceof String name && isForbiddenMember(name)) {
                throw forbidden(expression);
            }
        } else {
            throw forbidden(expression);
        }
    }

    private static boolean isForbiddenMember(String name) {
        return FORBIDDEN_MEMBERS.contains(name.toLowerCase(Locale.ROOT)) || name.startsWith("getDeclared");
    }

    private static boolean isForbiddenVariable(String name) {
        return name.startsWith("_") || "context".equals(name);
    }

    private static TemplateProcessingException forbidden(String expression) {
        return new TemplateProcessingException("Expression [" + expression + "] is not allowed in a custom template");
    }
}
