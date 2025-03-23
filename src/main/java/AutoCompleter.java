import java.util.*;

public class AutoCompleter {
    private static final List<String> BUILTINS = Arrays.asList("echo", "exit");
    private static int lastTabCount = 0;
    private static String lastPrefix = "";
    private static List<String> currentCompletions = new ArrayList<>();

    protected static String complete(String text, int tabCount) {
        // Reset state if prefix has changed.
        if (!text.equals(lastPrefix)) {
            lastTabCount = 0;
            currentCompletions.clear();
        }
        lastPrefix = text;
        lastTabCount = tabCount;

        // If completions not computed yet, get them.
        if (currentCompletions.isEmpty()) {
            currentCompletions = getCompletionOptions(text);
        }
        if (currentCompletions.isEmpty()) {
            return text;
        }
        int index = (lastTabCount - 1) % currentCompletions.size();
        String result = currentCompletions.get(index);
        // If the completion exactly matches a builtin, add a trailing space.
        if (BUILTINS.contains(result)) {
            result = result + " ";
        }
        return result;
    }

    protected static List<String> getCompletionOptions(String prefix) {
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