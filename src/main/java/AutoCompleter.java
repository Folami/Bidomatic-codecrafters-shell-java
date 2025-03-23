import java.util.*;

public class AutoCompleter {
    private static final List<String> BUILTINS = Arrays.asList("echo", "exit");
    private static int lastTabCount = 0;
    private static String lastPrefix = "";
    private static List<String> currentCompletions = new ArrayList<>();

    public static String complete(String text, int tabCount) {
        // Reset completion state if prefix changed
        if (!text.equals(lastPrefix)) {
            lastTabCount = 0;
            currentCompletions.clear();
        }
        
        lastPrefix = text;
        lastTabCount = tabCount;

        // Get completion options if this is first tab press or prefix changed
        if (currentCompletions.isEmpty()) {
            currentCompletions = getCompletionOptions(text);
        }

        if (currentCompletions.isEmpty()) {
            return text;
        }

        // Cycle through completions
        int index = (lastTabCount - 1) % currentCompletions.size();
        return currentCompletions.get(index);
    }

    private static List<String> getCompletionOptions(String prefix) {
        List<String> matches = new ArrayList<>();
        
        // Complete builtin commands
        for (String builtin : BUILTINS) {
            if (builtin.startsWith(prefix.trim())) {
                matches.add(builtin);
            }
        }

        // Sort matches for consistent ordering
        Collections.sort(matches);
        return matches;
    }
}