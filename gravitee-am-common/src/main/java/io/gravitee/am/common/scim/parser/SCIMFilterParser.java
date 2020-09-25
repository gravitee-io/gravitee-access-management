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
package io.gravitee.am.common.scim.parser;

import io.gravitee.am.common.scim.Schema;
import io.gravitee.am.common.scim.filter.AttributePath;
import io.gravitee.am.common.scim.filter.Filter;
import io.gravitee.am.common.scim.filter.Operator;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class SCIMFilterParser {

    /**
     * The filter to be parsed.
     */
    private final String filterString;

    /**
     * The default schema that should be assumed when parsing attributes with
     * no schema explicitly defined in the URN.
     */
    private final String defaultSchema;

    /**
     * The position one higher than the last character.
     */
    private int endPos;

    /**
     * The current character position.
     */
    private int currentPos;

    /**
     * The position marking the first character of the previous word or value.
     */
    private int markPos;

    /**
     * Create a new instance of a filter parser.
     *
     * @param filterString  The filter to be parsed.
     * @param defaultSchema The default schema that should be assumed when parsing
     *                      attributes without the schema explicitly defined in
     *                      the URN.
     */
    private SCIMFilterParser(final String filterString, final String defaultSchema) {
        this.filterString = filterString;
        this.endPos = filterString.length();
        this.currentPos = 0;
        this.markPos = 0;
        this.defaultSchema = defaultSchema;
    }

    /**
     * Parse the filter provided in the constructor.
     *
     * @return  A parsed SCIM filter.
     *
     * @throws  IllegalArgumentException  If the filter string could not be parsed.
     */
    public static Filter parse(final String filterString) throws IllegalArgumentException {
        try {
            return new SCIMFilterParser(filterString, Schema.SCHEMA_URI_CORE).readFilter();
        } catch (Exception e) {
            throw new IllegalArgumentException(MessageFormat.format("Invalid filter ''{0}'': {1}", filterString, e.getMessage()));
        }
    }

    /**
     * Read a filter component at the current position. A filter component is
     * <pre>
     * attribute attribute-operator [value]
     * </pre>
     * Most attribute operators require a value but 'pr' (presence) requires
     * no value.
     *
     * @return  The parsed filter component.
     */
    private Filter readFilterComponent() {
        String word = readWord();
        if (word == null)
        {
            final String msg = String.format(
                    "End of input at position %d but expected a filter expression",
                    markPos);
            throw new IllegalArgumentException(msg);
        }

        final AttributePath filterAttribute;
        try {
            filterAttribute = AttributePath.parse(word, defaultSchema);
        } catch (final Exception e) {
            final String msg = String.format(
                    "Expected an attribute reference at position %d: %s",
                    markPos, e.getMessage());
            throw new IllegalArgumentException(msg);
        }

        final String operator = readWord();
        if (operator == null) {
            final String msg = String.format(
                    "End of input at position %d but expected an attribute operator",
                    markPos);
            throw new IllegalArgumentException(msg);
        }

        final Operator attributeOperator;
        try {
            attributeOperator = Operator.fromString(operator);
        } catch (Exception ex) {
            final String msg = String.format(
                    "Unrecognized attribute operator '%s' at position %d. " +
                            "Expected: eq,co,sw,pr,gt,ge,lt,le", operator, markPos);
            throw new IllegalArgumentException(msg);
        }
        final Object filterValue;
        if (!attributeOperator.equals(Operator.PRESENCE)) {
            filterValue = readValue();
            if (filterValue == null) {
                final String msg = String.format(
                        "End of input at position %d while expecting a value for " +
                                "operator %s", markPos, operator);
                throw new IllegalArgumentException(msg);
            }
        } else {
            filterValue = null;
        }

        final String filterValueString = (filterValue != null) ? filterValue.toString() : null;
        return new Filter(
                attributeOperator,
                filterAttribute,
                filterValueString,
                (filterValue != null) && (filterValue instanceof String),
                null);
    }

    /**
     * Read a filter expression.
     *
     * @return  The SCIM filter.
     */
    private Filter readFilter() {
        final Stack<Node> expressionStack = new Stack<Node>();

        // Employ the shunting-yard algorithm to parse into reverse polish notation,
        // where the operands are filter components and the operators are the
        // logical AND and OR operators. This algorithm ensures that operator
        // precedence and parentheses are respected.
        final List<Node> reversePolish = new ArrayList<Node>();
        for (String word = readWord(); word != null; word = readWord()) {
            if (word.equalsIgnoreCase("and") || word.equalsIgnoreCase("or")) {
                final OperatorNode currentOperator;
                if (word.equalsIgnoreCase("and")) {
                    currentOperator = new OperatorNode(Operator.AND, markPos);
                } else {
                    currentOperator = new OperatorNode(Operator.OR, markPos);
                }
                while (!expressionStack.empty() && (expressionStack.peek() instanceof OperatorNode)) {
                    final OperatorNode previousOperator = (OperatorNode) expressionStack.peek();
                    if (previousOperator.getPrecedence() < currentOperator.getPrecedence()) {
                        break;
                    }
                    reversePolish.add(expressionStack.pop());
                }
                expressionStack.push(currentOperator);
            } else if (word.equals("(")) {
                expressionStack.push(new LeftParenthesisNode(markPos));
            } else if (word.equals(")")) {
                while (!expressionStack.empty() && !(expressionStack.peek() instanceof LeftParenthesisNode)) {
                    reversePolish.add(expressionStack.pop());
                }
                if (expressionStack.empty()) {
                    final String msg =
                            String.format("No opening parenthesis matching closing " +
                                    "parenthesis at position %d", markPos);
                    throw new IllegalArgumentException(msg);
                }
                expressionStack.pop();
            } else {
                rewind();
                final int pos = currentPos;
                final Filter filterComponent = readFilterComponent();
                reversePolish.add(new FilterNode(filterComponent, pos));
            }
        }

        while (!expressionStack.empty()) {
            final Node node = expressionStack.pop();
            if (node instanceof LeftParenthesisNode) {
                final String msg =
                        String.format("No closing parenthesis matching opening " +
                                "parenthesis at position %d", node.getPos());
                throw new IllegalArgumentException(msg);
            }
            reversePolish.add(node);
        }

        // Evaluate the reverse polish notation to create a single complex filter.
        final Stack<FilterNode> filterStack = new Stack<FilterNode>();
        for (final Node node : reversePolish) {
            if (node instanceof OperatorNode) {
                final FilterNode rightOperand = filterStack.pop();
                final FilterNode leftOperand = filterStack.pop();
                final OperatorNode operatorNode = (OperatorNode)node;
                if (operatorNode.getOperator().equals(Operator.AND)) {
                    final Filter filter = createAndFilter(
                            Arrays.asList(leftOperand.getFilterComponent(),
                                    rightOperand.getFilterComponent()));
                    filterStack.push(new FilterNode(filter, leftOperand.getPos()));
                } else {
                    final Filter filter = createOrFilter(
                            Arrays.asList(leftOperand.getFilterComponent(),
                                    rightOperand.getFilterComponent()));
                    filterStack.push(new FilterNode(filter, leftOperand.getPos()));
                }
            } else {
                filterStack.push((FilterNode)node);
            }
        }

        if (filterStack.size() == 0) {
            final String msg = String.format("Empty filter expression");
            throw new IllegalArgumentException(msg);
        }
        else if (filterStack.size() > 1) {
            final String msg = String.format(
                    "Unexpected characters at position %d", expressionStack.get(1).pos);
            throw new IllegalArgumentException(msg);
        }

        return filterStack.get(0).filterComponent;
    }

    /**
     * Read a word at the current position. A word is a consecutive sequence of
     * characters terminated by whitespace or a parenthesis, or a single opening
     * or closing parenthesis. Whitespace before and after the word is consumed.
     * The start of the word is saved in {@code markPos}.
     *
     * @return The word at the current position, or {@code null} if the end of
     *         the input has been reached.
     */
    private String readWord() {
        skipWhitespace();
        markPos = currentPos;

        loop:
        while (currentPos < endPos) {
            final char c = filterString.charAt(currentPos);
            switch (c) {
                case '(':
                case ')':
                    if (currentPos == markPos) {
                        currentPos++;
                    }
                    break loop;
                case ' ':
                    break loop;
                default:
                    currentPos++;
                    break;
            }
        }

        if (currentPos - markPos == 0) {
            return null;
        }

        final String word = filterString.substring(markPos, currentPos);
        skipWhitespace();
        return word;
    }

    /**
     * Rewind the current position to the start of the previous word or value.
     */
    private void rewind() {
        currentPos = markPos;
    }

    /**
     * Read a value at the current position. A value can be a number, a datetime
     * or a boolean value (the words true or false), or a string value in double
     * quotes, using the same syntax as for JSON values. Whitespace before and
     * after the value is consumed. The start of the value is saved in
     * {@code markPos}.
     *
     * @return An Object representing the value at the current position, or
     *         {@code null} if the end of the input has already been reached.
     */
    public Object readValue() {
        skipWhitespace();
        markPos = currentPos;

        if (currentPos == endPos) {
            return null;
        }

        if (filterString.charAt(currentPos) == '"') {
            currentPos++;
            final StringBuilder builder = new StringBuilder();
            while (currentPos < endPos) {
                final char c = filterString.charAt(currentPos);
                switch (c) {
                    case '\\':
                        currentPos++;
                        if (endOfInput()) {
                            final String msg = String.format(
                                    "End of input in a string value that began at " +
                                            "position %d", markPos);
                            throw new IllegalArgumentException(msg);
                        }
                        final char escapeChar = filterString.charAt(currentPos);
                        currentPos++;
                        switch (escapeChar) {
                            case '"':
                            case '/':
                            case '\'':
                            case '\\':
                                builder.append(escapeChar);
                                break;
                            case 'b':
                                builder.append('\b');
                                break;
                            case 'f':
                                builder.append('\f');
                                break;
                            case 'n':
                                builder.append('\n');
                                break;
                            case 'r':
                                builder.append('\r');
                                break;
                            case 't':
                                builder.append('\t');
                                break;
                            case 'u':
                                if (currentPos + 4 > endPos)
                                {
                                    final String msg = String.format(
                                            "End of input in a string value that began at " +
                                                    "position %d", markPos);
                                    throw new IllegalArgumentException(msg);
                                }
                                final String hexChars =
                                        filterString.substring(currentPos, currentPos + 4);
                                builder.append((char)Integer.parseInt(hexChars, 16));
                                currentPos += 4;
                                break;
                            default:
                                final String msg = String.format(
                                        "Unrecognized escape sequence '\\%c' in a string value " +
                                                "at position %d", escapeChar, currentPos - 2);
                                throw new IllegalArgumentException(msg);
                        }
                        break;

                    case '"':
                        currentPos++;
                        skipWhitespace();
                        return builder.toString();

                    default:
                        builder.append(c);
                        currentPos++;
                        break;
                }
            }

            final String msg = String.format(
                    "End of input in a string value that began at " +
                            "position %d", markPos);
            throw new IllegalArgumentException(msg);
        } else {
            loop:
            while (currentPos < endPos) {
                final char c = filterString.charAt(currentPos);
                switch (c) {
                    case ' ':
                    case '(':
                    case ')':
                        break loop;

                    case '+':
                    case '-':
                    case '.':
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                    case 'A':
                    case 'B':
                    case 'C':
                    case 'D':
                    case 'E':
                    case 'F':
                    case 'G':
                    case 'H':
                    case 'I':
                    case 'J':
                    case 'K':
                    case 'L':
                    case 'M':
                    case 'N':
                    case 'O':
                    case 'P':
                    case 'Q':
                    case 'R':
                    case 'S':
                    case 'T':
                    case 'U':
                    case 'V':
                    case 'W':
                    case 'X':
                    case 'Y':
                    case 'Z':
                    case 'a':
                    case 'b':
                    case 'c':
                    case 'd':
                    case 'e':
                    case 'f':
                    case 'g':
                    case 'h':
                    case 'i':
                    case 'j':
                    case 'k':
                    case 'l':
                    case 'm':
                    case 'n':
                    case 'o':
                    case 'p':
                    case 'q':
                    case 'r':
                    case 's':
                    case 't':
                    case 'u':
                    case 'v':
                    case 'w':
                    case 'x':
                    case 'y':
                    case 'z':
                        // These are all OK.
                        currentPos++;
                        break;

                    case '/':
                    case ':':
                    case ';':
                    case '<':
                    case '=':
                    case '>':
                    case '?':
                    case '@':
                    case '[':
                    case '\\':
                    case ']':
                    case '^':
                    case '_':
                    case '`':
                        // These are not allowed, but they are explicitly called out because
                        // they are included in the range of values between '-' and 'z', and
                        // making sure all possible characters are included can help make
                        // the switch statement more efficient.  We'll fall through to the
                        // default clause to reject them.
                    default:
                        final String msg = String.format(
                                "Invalid character '%c' in a number or boolean value at " +
                                        "position %d",
                                c, currentPos);
                        throw new IllegalArgumentException(msg);
                }
            }

            final String s = filterString.substring(markPos, currentPos);
            skipWhitespace();
            final Object value = stringToValue(s);
            if (value == null || value instanceof String) {
                final String msg = String.format(
                        "Invalid filter value beginning at position %d", markPos);
                throw new IllegalArgumentException(msg);
            }
            return value;
        }
    }

    /**
     * Determine if the end of the input has been reached.
     *
     * @return  {@code true} if the end of the input has been reached.
     */
    private boolean endOfInput() {
        return currentPos == endPos;
    }

    /**
     * Skip over any whitespace at the current position.
     */
    private void skipWhitespace() {
        while (currentPos < endPos && filterString.charAt(currentPos) == ' ') {
            currentPos++;
        }
    }

    private static Object stringToValue(String string) {
        if (string.equals("")) {
            return string;
        } else if (string.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        } else if (string.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        } else if (string.equalsIgnoreCase("null")) {
            return null;
        } else {
            char initial = string.charAt(0);
            if (initial >= '0' && initial <= '9' || initial == '-') {
                try {
                    if (isDecimalNotation(string)) {
                        Double d = Double.valueOf(string);
                        if (!d.isInfinite() && !d.isNaN()) {
                            return d;
                        }
                    } else {
                        Long myLong = Long.valueOf(string);
                        if (string.equals(myLong.toString())) {
                            if (myLong == (long)myLong.intValue()) {
                                return myLong.intValue();
                            }

                            return myLong;
                        }
                    }
                } catch (Exception var3) {
                }
            }

            return string;
        }
    }

    private static boolean isDecimalNotation(String val) {
        return val.indexOf(46) > -1 || val.indexOf(101) > -1 || val.indexOf(69) > -1 || "-0".equals(val);
    }

    private static Filter createAndFilter(final List<Filter> filterComponents) {
        return new Filter(Operator.AND, null, null, false, new ArrayList<>(filterComponents));
    }

    private static Filter createOrFilter(final List<Filter> filterComponents) {
        return new Filter(Operator.OR, null, null, false, new ArrayList<>(filterComponents));
    }

    /**
     * Base class for expression stack nodes. The expression stack is needed to
     * employ the shunting-yard algorithm to parse the filter expression.
     */
    class Node {
        private final int pos;

        /**
         * Create a new node.
         *
         * @param pos  The position of the node in the filter string.
         */
        public Node(final int pos)
        {
            this.pos = pos;
        }

        /**
         * Retrieve the position of the node in the filter string.
         * @return  The position of the node in the filter string.
         */
        public int getPos()
        {
            return pos;
        }
    }

    /**
     * A node representing a filter component.
     */
    class FilterNode extends Node {
        private final Filter filterComponent;

        /**
         * Create a new filter component node.
         *
         * @param filterComponent  The filter component.
         * @param pos              The position of the node in the filter string.
         */
        public FilterNode(final Filter filterComponent,
                          final int pos) {
            super(pos);
            this.filterComponent = filterComponent;
        }

        /**
         * Retrieve the filter component.
         *
         * @return  The filter component.
         */
        public Filter getFilterComponent()
        {
            return filterComponent;
        }

        @Override
        public String toString() {
            return "FilterNode{" +
                    "filterComponent=" + filterComponent +
                    "} " + super.toString();
        }
    }

    /**
     * A node representing a logical operator.
     */
    class OperatorNode extends Node {
        private final Operator operator;

        /**
         * Create a new logical operator node.
         *
         * @param operator   The type of operator, either SCIMFilterType.AND or
         *                     SCIMFilterType.OR.
         * @param pos          The position of the node in the filter string.
         */
        public OperatorNode(final Operator operator,
                            final int pos) {
            super(pos);
            this.operator = operator;
        }

        /**
         * Retrieve the type of operator.
         *
         * @return  The type of operator, either SCIMFilterType.AND or
         *          SCIMFilterType.OR.
         */
        public Operator getOperator()
        {
            return operator;
        }

        /**
         * Retrieve the precedence of the operator.
         *
         * @return  The precedence of the operator.
         */
        public int getPrecedence() {
            switch (operator) {
                case AND:
                    return 2;
                case OR:
                default:
                    return 1;
            }
        }

        @Override
        public String toString() {
            return "OperatorNode{" +
                    "operator=" + operator +
                    "} " + super.toString();
        }
    }

    /**
     * A node representing an opening parenthesis.
     */
    class LeftParenthesisNode extends Node {
        /**
         * Create a new opening parenthesis node.
         *
         * @param pos  The position of the parenthesis in the filter string.
         */
        public LeftParenthesisNode(final int pos)
        {
            super(pos);
        }
    }
}
