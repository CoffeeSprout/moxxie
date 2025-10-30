package com.coffeesprout.scheduler.tag;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Parser for tag expressions supporting boolean logic and wildcards.
 *
 * Grammar:
 * - expression ::= or_expression
 * - or_expression ::= and_expression ("OR" and_expression)*
 * - and_expression ::= not_expression ("AND" not_expression)*
 * - not_expression ::= "NOT" not_expression | primary
 * - primary ::= tag | "(" expression ")"
 * - tag ::= [a-zA-Z0-9_-*]+
 *
 * Operators are case-insensitive. Tags are case-sensitive.
 */
public class TagExpressionParser {

    private static final Pattern TAG_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-*]+");

    /**
     * Parse a tag expression string into a TagExpression object
     * @param expression The expression string to parse
     * @return Parsed TagExpression
     * @throws IllegalArgumentException if the expression is invalid
     */
    public static TagExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression cannot be null or empty");
        }

        Tokenizer tokenizer = new Tokenizer(expression);
        TagExpression result = parseOrExpression(tokenizer);

        if (tokenizer.hasNext()) {
            throw new IllegalArgumentException("Unexpected token: " + tokenizer.peek());
        }

        return result.optimize();
    }

    private static TagExpression parseOrExpression(Tokenizer tokenizer) {
        TagExpression left = parseAndExpression(tokenizer);

        while (tokenizer.hasNext() && tokenizer.peek().equalsIgnoreCase("OR")) {
            tokenizer.consume("OR");
            TagExpression right = parseAndExpression(tokenizer);
            left = new OrExpression(left, right);
        }

        return left;
    }

    private static TagExpression parseAndExpression(Tokenizer tokenizer) {
        TagExpression left = parseNotExpression(tokenizer);

        while (tokenizer.hasNext() && tokenizer.peek().equalsIgnoreCase("AND")) {
            tokenizer.consume("AND");
            TagExpression right = parseNotExpression(tokenizer);
            left = new AndExpression(left, right);
        }

        return left;
    }

    private static TagExpression parseNotExpression(Tokenizer tokenizer) {
        if (tokenizer.hasNext() && tokenizer.peek().equalsIgnoreCase("NOT")) {
            tokenizer.consume("NOT");
            return new NotExpression(parseNotExpression(tokenizer));
        }

        return parsePrimary(tokenizer);
    }

    private static TagExpression parsePrimary(Tokenizer tokenizer) {
        if (!tokenizer.hasNext()) {
            throw new IllegalArgumentException("Unexpected end of expression");
        }

        String token = tokenizer.peek();

        if (token.equals("(")) {
            tokenizer.consume("(");
            TagExpression expr = parseOrExpression(tokenizer);
            tokenizer.consume(")");
            return expr;
        }

        if (TAG_PATTERN.matcher(token).matches()) {
            tokenizer.next();
            return new TagMatch(token);
        }

        throw new IllegalArgumentException("Invalid token: " + token);
    }

    /**
     * Simple tokenizer for the expression parser
     */
    private static class Tokenizer {
        private final List<String> tokens;
        private int position = 0;

        Tokenizer(String expression) {
            this.tokens = tokenize(expression);
        }

        private List<String> tokenize(String expression) {
            List<String> result = new ArrayList<>();
            StringBuilder current = new StringBuilder();

            for (int i = 0; i < expression.length(); i++) {
                char c = expression.charAt(i);

                if (Character.isWhitespace(c)) {
                    if (current.length() > 0) {
                        result.add(current.toString());
                        current = new StringBuilder();
                    }
                } else if (c == '(' || c == ')') {
                    if (current.length() > 0) {
                        result.add(current.toString());
                        current = new StringBuilder();
                    }
                    result.add(String.valueOf(c));
                } else {
                    current.append(c);
                }
            }

            if (current.length() > 0) {
                result.add(current.toString());
            }

            return result;
        }

        boolean hasNext() {
            return position < tokens.size();
        }

        String peek() {
            if (!hasNext()) {
                throw new IllegalArgumentException("Unexpected end of expression");
            }
            return tokens.get(position);
        }

        String next() {
            String token = peek();
            position++;
            return token;
        }

        void consume(String expected) {
            if (!hasNext()) {
                throw new IllegalArgumentException("Expected '" + expected + "' but reached end of expression");
            }
            String actual = next();
            if (!actual.equalsIgnoreCase(expected)) {
                throw new IllegalArgumentException("Expected '" + expected + "' but got '" + actual + "'");
            }
        }
    }

    /**
     * Tag match expression (supports wildcards)
     */
    static class TagMatch implements TagExpression {
        private final String pattern;
        private final Pattern regex;

        TagMatch(String pattern) {
            this.pattern = pattern;
            if (pattern.contains("*")) {
                String regexPattern = pattern.replace("*", ".*");
                this.regex = Pattern.compile("^" + regexPattern + "$");
            } else {
                this.regex = null;
            }
        }

        @Override
        public boolean evaluate(Set<String> tags) {
            if (regex != null) {
                return tags.stream().anyMatch(tag -> regex.matcher(tag).matches());
            } else {
                return tags.contains(pattern);
            }
        }

        @Override
        public String toString() {
            return pattern;
        }

        @Override
        public Set<String> getReferencedTags() {
            if (hasWildcards()) {
                return Collections.emptySet();
            }
            return Collections.singleton(pattern);
        }

        @Override
        public boolean hasWildcards() {
            return pattern.contains("*");
        }

        @Override
        public TagExpression optimize() {
            return this;
        }
    }

    /**
     * AND expression
     */
    static class AndExpression implements TagExpression {
        private final TagExpression left;
        private final TagExpression right;

        AndExpression(TagExpression left, TagExpression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean evaluate(Set<String> tags) {
            return left.evaluate(tags) && right.evaluate(tags);
        }

        @Override
        public String toString() {
            return "(" + left + " AND " + right + ")";
        }

        @Override
        public Set<String> getReferencedTags() {
            Set<String> tags = new HashSet<>(left.getReferencedTags());
            tags.addAll(right.getReferencedTags());
            return tags;
        }

        @Override
        public boolean hasWildcards() {
            return left.hasWildcards() || right.hasWildcards();
        }

        @Override
        public TagExpression optimize() {
            TagExpression optimizedLeft = left.optimize();
            TagExpression optimizedRight = right.optimize();

            // Optimize: false AND x = false
            if (optimizedLeft instanceof FalseExpression || optimizedRight instanceof FalseExpression) {
                return FalseExpression.INSTANCE;
            }

            // Optimize: true AND x = x
            if (optimizedLeft instanceof TrueExpression) {
                return optimizedRight;
            }
            if (optimizedRight instanceof TrueExpression) {
                return optimizedLeft;
            }

            return new AndExpression(optimizedLeft, optimizedRight);
        }
    }

    /**
     * OR expression
     */
    static class OrExpression implements TagExpression {
        private final TagExpression left;
        private final TagExpression right;

        OrExpression(TagExpression left, TagExpression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean evaluate(Set<String> tags) {
            return left.evaluate(tags) || right.evaluate(tags);
        }

        @Override
        public String toString() {
            return "(" + left + " OR " + right + ")";
        }

        @Override
        public Set<String> getReferencedTags() {
            Set<String> tags = new HashSet<>(left.getReferencedTags());
            tags.addAll(right.getReferencedTags());
            return tags;
        }

        @Override
        public boolean hasWildcards() {
            return left.hasWildcards() || right.hasWildcards();
        }

        @Override
        public TagExpression optimize() {
            TagExpression optimizedLeft = left.optimize();
            TagExpression optimizedRight = right.optimize();

            // Optimize: true OR x = true
            if (optimizedLeft instanceof TrueExpression || optimizedRight instanceof TrueExpression) {
                return TrueExpression.INSTANCE;
            }

            // Optimize: false OR x = x
            if (optimizedLeft instanceof FalseExpression) {
                return optimizedRight;
            }
            if (optimizedRight instanceof FalseExpression) {
                return optimizedLeft;
            }

            return new OrExpression(optimizedLeft, optimizedRight);
        }
    }

    /**
     * NOT expression
     */
    static class NotExpression implements TagExpression {
        private final TagExpression inner;

        NotExpression(TagExpression inner) {
            this.inner = inner;
        }

        @Override
        public boolean evaluate(Set<String> tags) {
            return !inner.evaluate(tags);
        }

        @Override
        public String toString() {
            return "(NOT " + inner + ")";
        }

        @Override
        public Set<String> getReferencedTags() {
            return inner.getReferencedTags();
        }

        @Override
        public boolean hasWildcards() {
            return inner.hasWildcards();
        }

        @Override
        public TagExpression optimize() {
            TagExpression optimizedInner = inner.optimize();

            // Optimize: NOT NOT x = x
            if (optimizedInner instanceof NotExpression) {
                return ((NotExpression) optimizedInner).inner;
            }

            // Optimize: NOT true = false
            if (optimizedInner instanceof TrueExpression) {
                return FalseExpression.INSTANCE;
            }

            // Optimize: NOT false = true
            if (optimizedInner instanceof FalseExpression) {
                return TrueExpression.INSTANCE;
            }

            return new NotExpression(optimizedInner);
        }
    }

    /**
     * Always true expression (for optimization)
     */
    static class TrueExpression implements TagExpression {
        static final TrueExpression INSTANCE = new TrueExpression();

        private TrueExpression() {}

        @Override
        public boolean evaluate(Set<String> tags) {
            return true;
        }

        @Override
        public String toString() {
            return "TRUE";
        }

        @Override
        public Set<String> getReferencedTags() {
            return Collections.emptySet();
        }

        @Override
        public boolean hasWildcards() {
            return false;
        }

        @Override
        public TagExpression optimize() {
            return this;
        }
    }

    /**
     * Always false expression (for optimization)
     */
    static class FalseExpression implements TagExpression {
        static final FalseExpression INSTANCE = new FalseExpression();

        private FalseExpression() {}

        @Override
        public boolean evaluate(Set<String> tags) {
            return false;
        }

        @Override
        public String toString() {
            return "FALSE";
        }

        @Override
        public Set<String> getReferencedTags() {
            return Collections.emptySet();
        }

        @Override
        public boolean hasWildcards() {
            return false;
        }

        @Override
        public TagExpression optimize() {
            return this;
        }
    }
}
