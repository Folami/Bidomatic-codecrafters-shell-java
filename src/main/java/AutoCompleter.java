import java.util.*;

public class AutoCompleter {
    // Builtin commands to autocomplete.
    private static final List<String> BUILTINS = Arrays.asList("echo", "exit");

    // Returns a list of builtin commands that start with the given prefix.
    private static List<String> getCompletionOptions(String prefix) {
        List<String> options = new ArrayList<>();
        String trimmed = prefix.trim();
        for (String builtin : BUILTINS) {
            if (builtin.startsWith(trimmed)) {
                options.add(builtin);
            }
        }
        Collections.sort(options);
        return options;
    }

    // Returns the common prefix among all strings in the list.
    private static String getCommonPrefix(List<String> strings) {
        if (strings.isEmpty()) {
            return "";
        }
        String prefix = strings.get(0);
        for (int i = 1; i < strings.size(); i++) {
            String s = strings.get(i);
            int j = 0;
            while (j < prefix.length() && j < s.length() && prefix.charAt(j) == s.charAt(j)) {
                j++;
            }
            prefix = prefix.substring(0, j);
        }
        return prefix;
    }

    // Mimics the Python complete() function.
    // tabPressCount: the number of times Tab has been pressed on the same prefix.
    public static String complete(String text, int tabPressCount) {
        List<String> options = getCompletionOptions(text);
        if (options.isEmpty()) {
            return text;
        }
        // If there's exactly one match, return it with a trailing space if it's a builtin.
        if (options.size() == 1) {
            String option = options.get(0);
            return BUILTINS.contains(option) ? option + " " : option;
        }
        // If multiple matches exist, compute the common prefix.
        String common = getCommonPrefix(options);
        if (common.length() > text.length()) {
            return common + " ";
        }
        // Otherwise, cycle through the available completions.
        int index = (tabPressCount - 1) % options.size();
        String option = options.get(index);
        return BUILTINS.contains(option) ? option + " " : option;
    }

    // (Optional) Setup initialization if needed.
    public static void setupAutocomplete() {
        // In our simple case, the builtins are hard-coded.
    }
}
