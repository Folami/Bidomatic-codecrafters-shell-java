import java.util.*;

public class AutoCompleter {
    // Builtin commands to autocomplete.
    private static final List<String> BUILTINS = Arrays.asList("echo", "exit");
    private static int completionState = 0;
    private static List<String> completionOptions = new ArrayList<>();
    private static int tabCount = 0;
    
    // Setup function which could be expanded as needed.
    public static void setupAutocomplete() {
        // In our simple case, the builtins are hard-coded.
        // More initialization logic could go here.
    }
    
    // Called when the user presses the <TAB> key. This function simulates the complete() function in main.py.
    public static String complete(String text, int currentTabCount) {
        if (tabCount == 0) {
            completionState += 1;
            completionOptions = _getCompletionOptions(text);
            if (completionOptions.size() > 1) {
                String commonPrefix = _getCommonPrefix(completionOptions);
                if (commonPrefix != text) {
                    return commonPrefix + " ";
                }
                if (completionState == 1) {
                    // Ring the bell.
                    System.out.print("\007");
                    return "";    
                } else if (completionState == 2) {
                    System.out.println("\n" + completionOptions);
                    System.out.print("$ " + text);
                    completionState = 0;
                    return null;
                }
            }
        }
        if (tabCount < completionOptions.size()) {
            return completionOptions.get(tabCount++);
        }
        tabCount = 0;
        return null;
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

    // Simulates _get_common_prefix() in main.py: returns the common prefix of a list of strings.
    private static String _getCommonPrefix(List<String> strings) {
        if (strings.isEmpty()) {
            return "";
        }
        String first = strings.get(0);
        for (int i = 1; i < strings.size(); i++) {
            String other = strings.get(i);
            int j = 0;
            while (j < first.length() && j < other.length() && first.charAt(j) == other.charAt(j)) {
                j++;
            }
            first = first.substring(0, j);
        }
        return first;
    }
}
