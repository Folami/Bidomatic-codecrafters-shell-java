import java.util.*;

public class AutoCompleter {
    // Builtin commands to autocomplete
    private static final List<String> BUILTINS = Arrays.asList("echo", "exit");
    private static String lastInput = "";
    private static List<String> currentCompletions = new ArrayList<>();
    
    public static String complete(String text, int tabCount) {
        // Reset completions if input changed
        if (!text.equals(lastInput)) {
            lastInput = text;
            currentCompletions = getCompletionOptions(text);
        }

        // No completions available
        if (currentCompletions.isEmpty()) {
            return text;
        }

        // Single completion - return with space
        if (currentCompletions.size() == 1) {
            return currentCompletions.get(0);
        }

        // Multiple completions - cycle through them
        int index = (tabCount - 1) % currentCompletions.size();
        return currentCompletions.get(index) + " ";
    }

    private static List<String> getCompletionOptions(String prefix) {
        List<String> matches = new ArrayList<>();
        for (String builtin : BUILTINS) {
            if (builtin.startsWith(prefix.trim())) {
                matches.add(builtin);
            }
        }
        Collections.sort(matches);
        return matches;
    }
}
