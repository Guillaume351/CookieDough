package com.cookiebuild.cookiedough.chat;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Chat blocker that uses a word list with character substitution.
 */
public class WordListBlocker extends ChatBlocker {
    private List<Pattern> patterns;

    public WordListBlocker(List<String> words, Map<Character, String> substitutions) {
        this.patterns = words.stream()
                .map(word -> compilePattern(word, substitutions))
                .collect(Collectors.toList());
    }

    private Pattern compilePattern(String word, Map<Character, String> substitutions) {
        StringBuilder regex = new StringBuilder();
        for (char c : word.toCharArray()) {
            if (substitutions.containsKey(c)) {
                regex.append("[").append(substitutions.get(c)).append("]");
            } else {
                regex.append(c);
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    @Override
    public boolean matches(String message) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(message).find());
    }
}