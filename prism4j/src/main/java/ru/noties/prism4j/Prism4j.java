package ru.noties.prism4j;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static java.util.regex.Pattern.compile;

public class Prism4j {

    // todo: rest thing (just override/add to existing grammar)
    // todo: insertBefore
    // todo: allow delete (it sometimes happen)

    public interface Grammar {

        @NonNull
        String name();

        // should mention that returned array is mutable
        @NonNull
        List<Token> tokens();
    }

    public interface Token {

        @NonNull
        String name();

        @NonNull
        List<Pattern> patterns();
    }

    public interface Pattern {

        @NonNull
        java.util.regex.Pattern regex();

        boolean lookbehind();

        boolean greedy();

        @Nullable
        String alias();

        @Nullable
        Grammar inside();
    }

    public interface Node {
        int textLength();
    }

    public interface Text extends Node {

        @NonNull
        String literal();
    }

    public interface Syntax extends Node {

        @NonNull
        String type();

        @NonNull
        List<? extends Node> children();

        @Nullable
        String alias();

        @NonNull
        String matchedString();

        boolean greedy();

        /**
         * The main aim for this flag is to be able to properly construct simplified
         * array of tokens during tests. If it\'s set to true, then children will be
         * inside another array. Otherwise they will be _flattened_ into the same array
         * as token type information
         *
         * @return a flag indicating if children of this node were tokenized
         */
        boolean tokenized();
    }

    public interface Visitor {
        void visit(@NonNull List<? extends Node> nodes);
    }

    @NonNull
    public static Grammar grammar(@NonNull String name, @NonNull List<Token> tokens) {
        return new GrammarImpl(name, tokens);
    }

    @NonNull
    public static Grammar grammar(@NonNull String name, Token... tokens) {
        return new GrammarImpl(name, ArrayUtils.toList(tokens));
    }

    @NonNull
    public static Token token(@NonNull String name, @NonNull List<Pattern> patterns) {
        return new TokenImpl(name, patterns);
    }

    @NonNull
    public static Token token(@NonNull String name, Pattern... patterns) {
        return new TokenImpl(name, ArrayUtils.toList(patterns));
    }

    @NonNull
    public static Pattern pattern(@NonNull java.util.regex.Pattern regex) {
        return new PatternImpl(regex, false, false, null, null);
    }

    @NonNull
    public static Pattern pattern(@NonNull java.util.regex.Pattern regex, boolean lookbehind) {
        return new PatternImpl(regex, lookbehind, false, null, null);
    }

    @NonNull
    public static Pattern pattern(
            @NonNull java.util.regex.Pattern regex,
            boolean lookbehind,
            boolean greedy) {
        return new PatternImpl(ensureMultilineIfGreedy(regex, greedy), lookbehind, greedy, null, null);
    }

    @NonNull
    public static Pattern pattern(
            @NonNull java.util.regex.Pattern regex,
            boolean lookbehind,
            boolean greedy,
            @Nullable String alias) {
        return new PatternImpl(ensureMultilineIfGreedy(regex, greedy), lookbehind, greedy, alias, null);
    }

    @NonNull
    public static Pattern pattern(
            @NonNull java.util.regex.Pattern regex,
            boolean lookbehind,
            boolean greedy,
            @Nullable String alias,
            @Nullable Grammar inside) {
        return new PatternImpl(ensureMultilineIfGreedy(regex, greedy), lookbehind, greedy, alias, inside);
    }

    @NonNull
    public static Grammar extend(@NonNull Grammar grammar, @NonNull String name, @NonNull List<Token> tokens) {
        return new GrammarImpl(name, GrammarUtils.extend(grammar.tokens(), tokens));
    }

    @NonNull
    public static Grammar extend(@NonNull Grammar grammar, @NonNull String name, Token... tokens) {
        return new GrammarImpl(name, GrammarUtils.extend(grammar.tokens(), ArrayUtils.toList(tokens)));
    }

    private final GrammarLocator grammarLocator;
    private final Map<String, Grammar> grammarCache = new HashMap<>(3);

    public Prism4j() {
        this(new GrammarLocatorDef());
    }

    public Prism4j(@NonNull GrammarLocator grammarLocator) {
        this.grammarLocator = grammarLocator;
    }

    @NonNull
    public GrammarLocator grammarLocator() {
        return grammarLocator;
    }

    @NonNull
    public List<Node> tokenize(@NonNull String text, @NonNull Grammar grammar) {
        final List<Node> entries = new ArrayList<>(3);
        entries.add(new TextImpl(text));
        matchGrammar(text, entries, grammar, 0, 0, false, null);
        return entries;
    }

    @Nullable
    public Grammar grammar(@NonNull String name) {
        name = grammarLocator.grammarName(name);
        Grammar grammar = grammarCache.get(name);
        if (grammar == null) {
            grammar = grammarLocator.grammar(this, name);
            if (grammar != null) {
                grammarCache.put(name, grammar);
            }
        }
        return grammar;
    }

    public void language(@NonNull Grammar grammar) {
        grammarCache.put(grammar.name(), grammar);
    }


    private void matchGrammar(
            @NonNull String text,
            @NonNull List<Node> entries,
            @NonNull Grammar grammar,
            int index,
            int startPosition,
            boolean oneShot,
            @Nullable Token target
    ) {

        for (Token token : grammar.tokens()) {

            if (token == target) {
                return;
            }

            for (Pattern pattern : token.patterns()) {

                final boolean lookbehind = pattern.lookbehind();
                final boolean greedy = pattern.greedy();
                int lookbehindLength = 0;

                final java.util.regex.Pattern regex = pattern.regex();

                // NB originally (prismjs) here was a check if a pattern is multiline if pattern is greedy
                // this check has been moved out of here to configuration step (factory `pattern` methods)

                // Don't cache textLength as it changes during the loop
                for (int i = index, position = startPosition; i < entries.size(); position += entries.get(i).textLength(), ++i) {

                    // todo: more meaningful thing here
                    if (entries.size() > text.length()) {
                        System.out.printf("entries: %s%n", entries);
                        throw new RuntimeException();
                    }

                    final Node node = entries.get(i);
                    if (isSyntaxNode(node)) {
                        continue;
                    }

                    String str = ((Text) node).literal();

                    final Matcher matcher;
                    final int deleteCount;
                    final boolean greedyMatch;
                    int greedyAdd = 0;

                    if (greedy && i != entries.size() - 1) {

                        matcher = regex.matcher(text);
                        // limit search to the position (?)
                        matcher.region(position, text.length());

                        if (!matcher.find()) {
                            break;
                        }

                        int from = matcher.start();

                        if (lookbehind) {
                            from += matcher.group(1).length();
                        }
                        final int to = matcher.start() + matcher.group(0).length();

                        int k = i;
                        int p = position;

                        for (int len = entries.size(); k < len && (p < to || (!isSyntaxNode(entries.get(k)) && !isGreedyNode(entries.get(k - 1)))); ++k) {
                            p += entries.get(k).textLength();
                            // Move the index i to the element in strarr that is closest to from
                            if (from >= p) {
                                i += 1;
                                position = p;
                            }
                        }

                        if (isSyntaxNode(entries.get(i))) {
                            continue;
                        }

                        deleteCount = k - i;
                        str = text.substring(position, p);
                        greedyMatch = true;
                        greedyAdd = -position;

                    } else {
                        matcher = regex.matcher(str);
                        deleteCount = 1;
                        greedyMatch = false;
                    }

                    if (!greedyMatch && !matcher.find()) {
                        if (oneShot) {
                            break;
                        }
                        continue;
                    }

                    if (lookbehind) {
                        final String group = matcher.group(1);
                        lookbehindLength = group != null ? group.length() : 0;
                    }

                    final int from = matcher.start() + greedyAdd + lookbehindLength;
                    final String match;
                    if (lookbehindLength > 0) {
                        match = matcher.group().substring(lookbehindLength);
                    } else {
                        match = matcher.group();
                    }
                    final int to = from + match.length();

                    for (int d = 0; d < deleteCount; d++) {
                        entries.remove(i);
                    }

                    int i2 = i;

                    if (from != 0) {
                        final String before = str.substring(0, from);
                        i += 1;
                        position += before.length();
                        entries.add(i2++, new TextImpl(before));
                    }

                    final List<? extends Node> tokenEntries;
                    final Grammar inside = pattern.inside();
                    final boolean hasInside = inside != null;
                    if (hasInside) {
                        tokenEntries = tokenize(match, inside);
                    } else {
                        tokenEntries = Collections.singletonList(new TextImpl(match));
                    }

                    entries.add(i2++, new SyntaxImpl(
                            token.name(),
                            tokenEntries,
                            pattern.alias(),
                            match,
                            greedy,
                            hasInside
                    ));

                    // important thing here (famous off-by one error) to check against full length (not `length - 1`)
                    if (to < str.length()) {
                        final String after = str.substring(to);
                        entries.add(i2, new TextImpl(after));
                    }

                    if (deleteCount != 1) {
                        matchGrammar(text, entries, grammar, i, position, true, token);
                    }

                    if (oneShot) {
                        break;
                    }
                }
            }
        }
    }

    // we are using `multiline` definition here, but originally prism is using word `global`
    @NonNull
    private static java.util.regex.Pattern ensureMultilineIfGreedy(java.util.regex.Pattern pattern, boolean greedy) {
        if (greedy
                && !(java.util.regex.Pattern.MULTILINE == (java.util.regex.Pattern.MULTILINE & pattern.flags()))) {
            return compile(pattern.pattern(), java.util.regex.Pattern.MULTILINE | pattern.flags());
        }
        return pattern;
    }

    private static boolean isSyntaxNode(@NonNull Node node) {
        return node instanceof Syntax;
    }

    private static boolean isGreedyNode(@NonNull Node node) {
        return (node instanceof Syntax) && ((Syntax) node).greedy();
    }
}
