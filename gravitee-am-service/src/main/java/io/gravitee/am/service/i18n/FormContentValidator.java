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
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.ITemplateHandler;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.ICDATASection;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IComment;
import org.thymeleaf.model.IDocType;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.model.IProcessingInstruction;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEnd;
import org.thymeleaf.model.ITemplateStart;
import org.thymeleaf.model.IText;
import org.thymeleaf.model.IXMLDeclaration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateparser.markup.HTMLTemplateParser;

/**
 * Validates, when a custom form is saved, the expressions Thymeleaf will evaluate when it renders the form.
 *
 * <p>The template is parsed like Thymeleaf does and only the places where an expression is evaluated are checked:
 * the values of {@code th:*} attributes and the inline expressions ({@code [[...]]}, {@code [(...)]}) of the text.
 * Comments, and the script and style content outside of inline expressions, are not evaluated and are left alone,
 * except for the prototype-only comment blocks ({@code <!--/*&#47; ... /*&#47;-->}) that Thymeleaf renders.
 * The evaluation itself remains restricted by {@link RestrictedExpressionEvaluator}.
 */
public class FormContentValidator {

    private static final String OWNER_TEMPLATE = "custom-form";
    private static final String PROTOTYPE_BLOCK_DELIMITER = "/*/";

    private final IEngineConfiguration configuration = new TemplateEngine().getConfiguration();
    private final HTMLTemplateParser parser = new HTMLTemplateParser(2, 4096);
    private final RestrictedExpressionEvaluator evaluator = new RestrictedExpressionEvaluator();

    /**
     * @throws org.thymeleaf.exceptions.TemplateProcessingException if the content uses an expression that is not
     * valid or not allowed in a custom template
     */
    public void validate(String content) {
        if (content == null) {
            return;
        }
        parser.parseString(configuration, OWNER_TEMPLATE, content, 0, 0, TemplateMode.HTML, new ExpressionCollector());
    }

    private void validateExpressionsOf(String source) {
        for (String expression : extractExpressions(source)) {
            evaluator.validate(expression);
        }
    }

    private static List<String> extractExpressions(String source) {
        List<String> expressions = new ArrayList<>();
        for (int i = 0; i < source.length() - 1; i++) {
            char marker = source.charAt(i);
            if ((marker == '$' || marker == '*') && source.charAt(i + 1) == '{') {
                int end = endOfExpression(source, i + 1);
                if (end < 0) {
                    throw new TemplateProcessingException("Unterminated expression in [" + source + "]");
                }
                expressions.add(source.substring(i + 2, end));
                i = end;
            }
        }
        return expressions;
    }

    private static int endOfExpression(String source, int openingBrace) {
        int depth = 0;
        char quote = 0;
        for (int i = openingBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private void validateInlineExpressionsOf(String text) {
        validateInlineRegions(text, "[[", "]]");
        validateInlineRegions(text, "[(", ")]");
    }

    private void validateInlineRegions(String text, String opening, String closing) {
        int from = 0;
        while ((from = text.indexOf(opening, from)) >= 0) {
            int start = from + opening.length();
            int end = text.indexOf(closing, start);
            if (end < 0) {
                end = text.length();
            }
            validateExpressionsOf(text.substring(start, end));
            from = end;
        }
    }

    private void validatePrototypeBlock(String comment) {
        if (comment.startsWith(PROTOTYPE_BLOCK_DELIMITER) && comment.endsWith(PROTOTYPE_BLOCK_DELIMITER)
                && comment.length() >= 2 * PROTOTYPE_BLOCK_DELIMITER.length()) {
            validate(comment.substring(PROTOTYPE_BLOCK_DELIMITER.length(), comment.length() - PROTOTYPE_BLOCK_DELIMITER.length()));
        }
    }

    private void validateAttributesOf(IProcessableElementTag tag) {
        for (var attribute : tag.getAllAttributes()) {
            String name = attribute.getAttributeCompleteName().toLowerCase(Locale.ROOT);
            if ((name.startsWith("th:") || name.startsWith("data-th-")) && attribute.getValue() != null) {
                validateExpressionsOf(attribute.getValue());
            }
        }
    }

    private final class ExpressionCollector implements ITemplateHandler {

        @Override
        public void handleStandaloneElement(IStandaloneElementTag tag) {
            validateAttributesOf(tag);
        }

        @Override
        public void handleOpenElement(IOpenElementTag tag) {
            validateAttributesOf(tag);
        }

        @Override
        public void handleText(IText text) {
            validateInlineExpressionsOf(text.getText());
        }

        @Override
        public void handleCDATASection(ICDATASection cdata) {
            validateInlineExpressionsOf(cdata.getContent());
        }

        @Override
        public void handleComment(IComment comment) {
            validatePrototypeBlock(comment.getContent());
        }

        @Override
        public void setNext(ITemplateHandler next) {
        }

        @Override
        public void setContext(ITemplateContext context) {
        }

        @Override
        public void handleTemplateStart(ITemplateStart templateStart) {
        }

        @Override
        public void handleTemplateEnd(ITemplateEnd templateEnd) {
        }

        @Override
        public void handleXMLDeclaration(IXMLDeclaration xmlDeclaration) {
        }

        @Override
        public void handleDocType(IDocType docType) {
        }

        @Override
        public void handleCloseElement(ICloseElementTag tag) {
        }

        @Override
        public void handleProcessingInstruction(IProcessingInstruction processingInstruction) {
        }
    }
}
