import java.util.*;



public class AutoCompleter {
    // Builtin commands to autocomplete.
    private static final List<String> BUILTINS = Arrays.asList("echo", "exit", "type", "pwd", "cd");
    private static String lastInput = "";
    private static List<String> currentCompletions = new ArrayList<>();

    public static String complete(String text, int tabCount) {
        // Reset completions if input changed
        if (!text.equals(lastInput)) {
            lastInput = text;
            currentCompletions = getCompletionOptions(text);
        }

        // No completions available.
        if (currentCompletions.isEmpty()) {
            return text;
        }

        // Single completion – return it with a trailing space.
        if (currentCompletions.size() == 1) {
            return currentCompletions.get(0) + " ";
        }

        // Multiple completions – cycle through them (always append a space).
        int index = (tabCount - 1) % currentCompletions.size();
        return currentCompletions.get(index) + " ";
    }

    private static List<String> getCompletionOptions(String prefix) {
        List<String> matches = new ArrayList<>();
        String trimmed = prefix.trim();
        for (String builtin : BUILTINS) {
            if (builtin.startsWith(trimmed)) {
                matches.add(builtin);
            }
        }
        Collections.sort(matches);
        return matches;
    }
}
