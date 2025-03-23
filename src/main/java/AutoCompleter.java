import java.util.*;

public class AutoCompleter {
    // Builtin commands to autocomplete.
    private static final List<String> BUILTINS = Arrays.asList("echo", "exit");
    
    // Cache for the current completion options and state.
    private static List<String> completionOptions = new ArrayList<>();
    private static String lastPrefix = "";
    private static int tabCount = 0;
    
    // Setup function which could be expanded as needed.
    public static void setupAutocomplete() {
        // In our simple case, the builtins are hard-coded.
        // More initialization logic could go here.
    }
    
    // Called when the user presses the <TAB> key. This function simulates the complete() function in main.py.
    public static String complete(String text, int currentTabCount) {
        // If the prefix has changed, reset the cached options.
        if (!text.equals(lastPrefix)) {
            lastPrefix = text;
            tabCount = 0;
            completionOptions = _getCompletionOptions(text);
        }
        // Update our tab press count.
        tabCount = currentTabCount;
        
        if (completionOptions.isEmpty()) {
            return text;
        }
        // Cycle through the available completions.
        int index = (tabCount - 1) % completionOptions.size();
        String completion = completionOptions.get(index);
        // If the completion exactly matches a builtin, append a trailing space.
        if (BUILTINS.contains(completion)) {
            return completion + " ";
        }
        return completion;
    }
    
    // Simulates _get_completion_options() in main.py: returns a list of builtin commands that start with the given prefix.
    private static List<String> _getCompletionOptions(String prefix) {
        List<String> options = new ArrayList<>();
        for (String builtin : BUILTINS) {
            if (builtin.startsWith(prefix.trim())) {
                options.add(builtin);
            }
        }
        Collections.sort(options);
        return options;
    }
}
