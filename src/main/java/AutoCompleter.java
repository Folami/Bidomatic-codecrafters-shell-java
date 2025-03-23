import java.util.*;

public class AutoCompleter {
    private static final Trie commandTrie;
    private static List<String> completionOptions = new ArrayList<>();
    private static int completionState = 0;
    // Initialize the trie with built-in commands
    static {
        List<String> builtInCommands = Arrays.asList("echo", "exit", "type", "pwd", "cd");
        commandTrie = new Trie(builtInCommands);
    }
        
    public static String complete(String text, int state) {
        if (state == 0) {
            completionState++;
            completionOptions = commandTrie.suggest(text);                
            if (completionOptions.size() > 1) {
                String commonPrefix = commandTrie.getCommonPrefix(completionOptions);
                if (!commonPrefix.equals(text)) {
                    return commonPrefix;
                }
                if (completionState == 1) {
                    System.out.print("\u0007"); // Ring the bell
                    return null;
                } else if (completionState == 2) {
                    System.out.println("\n" + String.join("  ", completionOptions));
                    System.out.print("$ " + text);
                    completionState = 0;
                    return null;
                }
            } else if (completionOptions.size() == 1) {
                return completionOptions.get(0);
            }
        }
        completionState = 0;
        return null;
    }

    private static class Trie {
        private final TrieNode root;

        public Trie(List<String> words) {
            root = new TrieNode();
            for (String word : words) {
                root.insert(word);
            }
        }

        public List<String> suggest(String prefix) {
            List<String> suggestions = new ArrayList<>();
            TrieNode lastNode = root;
                
            // Traverse to the last node of prefix
            for (char c : prefix.toCharArray()) {
                TrieNode child = lastNode.children.get(c);
                if (child == null) {
                    return suggestions;
                }
                lastNode = child;
            }
                
            // Collect all words starting from last node
            suggestHelper(lastNode, suggestions, new StringBuilder(prefix));
            return suggestions;
        }

        private void suggestHelper(TrieNode node, List<String> suggestions, StringBuilder current) {
            if (node.isWord) {
                suggestions.add(current.toString());
            }

            for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
                current.append(entry.getKey());
                suggestHelper(entry.getValue(), suggestions, current);
                current.setLength(current.length() - 1);
            }
        }

        public String getCommonPrefix(List<String> words) {
            if (words.isEmpty()) return "";
            String first = words.get(0);
            for (int i = 0; i < first.length(); i++) {
                char c = first.charAt(i);
                for (int j = 1; j < words.size(); j++) {
                    if (i >= words.get(j).length() || words.get(j).charAt(i) != c) {
                        return first.substring(0, i);
                    }
                }
            }
            return first;
        }

        private static class TrieNode {
            Map<Character, TrieNode> children;
            boolean isWord;

            public TrieNode() {
                children = new HashMap<>();
                isWord = false;
            }

            public void insert(String word) {
                TrieNode current = this;
                for (char c : word.toCharArray()) {
                    current.children.putIfAbsent(c, new TrieNode());
                    current = current.children.get(c);
                }
                current.isWord = true;
            }
        }
    }
}