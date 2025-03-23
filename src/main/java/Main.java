import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;



public class Main {
    private static final String shellHome = System.getProperty("user.dir");
    private static final List<String> shBuiltins = List.of("echo", "exit", "type", "pwd", "cd");
    private static int tabPressCount = 0;  // Track Tab presses
    
    
    public static void main(String[] args) {
        while (true) {
            String commandLine = inputPrompt();
            if (commandLine == null || commandLine.isEmpty()) {
                continue;
            }
            try {
                List<String> tokens = Shlex.split(commandLine, true, true);
                if (tokens == null || tokens.isEmpty()) {
                    continue;
                }
                String command = tokens.get(0);
                List<String> commandArgs = tokens.subList(1, tokens.size());
                executeCommand(command, commandArgs);
            } catch (IllegalArgumentException e) {
                System.out.println("Error parsing command: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("An error occurred: " + e.getMessage());
            }
        }
    }

    private static String inputPrompt() {
        /*
         * Prompts for and returns user input, handling Tab for autocompletion.
         * Returns: The user input string or null on error/EOF.
         */
        Console console = System.console();
        if (console == null) {
            // Fallback for non-interactive terminals
            System.out.print("$ ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                return reader.readLine();
            } catch (IOException e) {
                return null;
            }
        }
        System.out.print("$ ");
        StringBuilder inputBuffer = new StringBuilder();
        try {
            while (true) {
                int key = System.in.read();  // Read a single character
                if (key == '\n') {  // Enter key submits input
                    System.out.println();
                    return inputBuffer.toString().trim();
                } else if (key == '\t') {  // Tab key triggers autocompletion
                    inputPromptTabbed(inputBuffer);
                } else if (key == 127 || key == 8) {  // Backspace key
                    if (inputBuffer.length() > 0) {
                        inputBuffer.setLength(inputBuffer.length() - 1);
                        System.out.print("\b \b");  // Erase last character
                    }
                } else {
                    inputBuffer.append((char)key);  // Append character to buffer
                    System.out.print((char)key);  // Display character
                }
            }
        } catch (IOException e) {
            return null;  // Return null on I/O error
        }
    }

    private static void inputPromptTabbed(StringBuilder inputBuffer) {
        String currentText = inputBuffer.toString().trim();
        String completed = AutoCompleter.complete(currentText, tabPressCount);
        if (completed != null) {
            // Clear current line and display completed text
            System.out.print("\r$ ");  // Return to start of line
            System.out.print(completed);  // Print completed command
            // Clear any remaining characters from previous input
            int extraLength = Math.max(0, inputBuffer.length() - completed.length());
            for (int i = 0; i < extraLength; i++) {
                System.out.print(" ");
            }
            System.out.print("\r$ " + completed);  // Move cursor back
            inputBuffer.setLength(0);
            inputBuffer.append(completed.trim());  // Update buffer
        }
        System.out.flush();
    }


    private static void executeCommand(String command, List<String> args) throws IOException {
        switch (command) {
            case "exit":
                exitShell();
                break;
            case "echo":
                executeEcho(args);
                break;
            case "type":
                executeType(args);
                break;
            case "pwd":
                executePwd();
                break;
            case "cd":
                executeCd(args);
                break;
            default:
                runExternalCommand(command, args);
                break;
        }
    }

    private static void exitShell() {
        System.exit(0);
    }

    private static void executeEcho(List<String> args) throws IOException {
        String outputFile = null;       // For stdout redirection (>)
        String appendOutputFile = null; // For stdout append redirection (>> or 1>>)
        String errorFile = null;        // For stderr redirection (2>)
        String appendErrorFile = null;  // For stderr append redirection (2>>)
        List<String> echoArgs = new ArrayList<>();

        // Parse arguments for redirection
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(">") || args.get(i).equals("1>")) {
                if (i + 1 < args.size()) {
                    outputFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for redirection");
                    return;
                }
            } else if (args.get(i).equals(">>") || args.get(i).equals("1>>")) {
                if (i + 1 < args.size()) {
                    appendOutputFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for append redirection");
                    return;
                }
            } else if (args.get(i).equals("2>")) {
                if (i + 1 < args.size()) {
                    errorFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for error redirection");
                    return;
                }
            } else if (args.get(i).equals("2>>")) {
                if (i + 1 < args.size()) {
                    appendErrorFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for error append redirection");
                    return;
                }
            } else {
                echoArgs.add(args.get(i));
            }
        }

        String output = String.join(" ", echoArgs);

        // Handle stdout redirection (overwrite)
        if (outputFile != null) {
            File outputFileObj = new File(outputFile);
            if (!outputFileObj.getParentFile().exists()) {
                if (!outputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for output redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter(outputFileObj))) {
                out.println(output); // Write output with newline for stdout
            }
        }
        // Handle stdout redirection (append)
        else if (appendOutputFile != null) {
            File appendOutputFileObj = new File(appendOutputFile);
            if (!appendOutputFileObj.getParentFile().exists()) {
                if (!appendOutputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for append redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter(appendOutputFileObj, true))) {
                out.println(output); // Append output with newline
            }
        } else {
            // If no stdout redirection, print to console
            System.out.println(output);
        }

        // Handle stderr redirection (overwrite)
        if (errorFile != null) {
            File errorFileObj = new File(errorFile);
            if (!errorFileObj.getParentFile().exists()) {
                if (!errorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter errOut = new java.io.PrintWriter(new java.io.FileWriter(errorFileObj))) {
                errOut.print(""); // No stderr for echo by default
            }
        }
        // Handle stderr redirection (append)
        else if (appendErrorFile != null) {
            File appendErrorFileObj = new File(appendErrorFile);
            if (!appendErrorFileObj.getParentFile().exists()) {
                if (!appendErrorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error append redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter errOut = new java.io.PrintWriter(new java.io.FileWriter(appendErrorFileObj, true))) {
                errOut.print(""); // No stderr for echo by default, append nothing
            }
        }
    }

    private static void executeType(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("type: missing operand");
            return;
        }
        String targetCommand = args.get(0);
        if (shBuiltins.contains(targetCommand)) {
            System.out.println(targetCommand + " is a shell builtin");
            } else {
            String executable = findExecutable(targetCommand);
            if (executable != null) {
                System.out.println(targetCommand + " is " + executable);
            } else {
                System.out.println(targetCommand + ": not found");
            }
        }
    }

    private static void executePwd() {
        System.out.println(System.getProperty("user.dir"));
    }

    private static void executeCd(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("cd: missing operand");
            return;
        }
        String newDir = args.get(0);
        if (newDir.startsWith("~")) {
            newDir = System.getenv("HOME") + newDir.substring(1);
        }
        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path newPath = currentPath.resolve(newDir).normalize();
        try {
            if (Files.exists(newPath) && Files.isDirectory(newPath)) {
                System.setProperty("user.dir", newPath.toString());
            } else {
                System.err.println("cd: " + newDir + ": No such file or directory");
            }
        } catch (Exception e) {
            System.err.println("cd: " + newDir + ": " + e.getMessage());
        }
    }


    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] paths = pathEnv.split(File.pathSeparator);
            for (String dir : paths) {
                Path filePath = Paths.get(dir, command);
                if (Files.exists(filePath) && Files.isExecutable(filePath)) {
                    return filePath.toString();
                }
            }
        }
        // Check in the current directory
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path filePath = currentDir.resolve(command);
        if (Files.exists(filePath) && Files.isExecutable(filePath)) {
            return filePath.toString();
        }
        return null;
    }


    private static void runExternalCommand(String command, List<String> args) throws IOException {
        if (findExecutable(command) == null) {
            System.err.println(command + ": command not found");
            return;
        }
        List<String> commandWithArgs = new ArrayList<>();
        commandWithArgs.add(command);
        
        String outputFile = null;       // For stdout overwrite (>)
        String appendOutputFile = null; // For stdout append (>> or 1>>)
        String errorFile = null;        // For stderr overwrite (2>)
        String appendErrorFile = null;  // For stderr append (2>>)
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(">") || args.get(i).equals("1>")) {
                if (i + 1 < args.size()) {
                    outputFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for redirection");
                    return;
                }
            } else if (args.get(i).equals(">>") || args.get(i).equals("1>>")) {
                if (i + 1 < args.size()) {
                    appendOutputFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for append redirection");
                    return;
                }
            } else if (args.get(i).equals("2>")) {
                if (i + 1 < args.size()) {
                    errorFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for error redirection");
                    return;
                }
            } else if (args.get(i).equals("2>>")) {
                if (i + 1 < args.size()) {
                    appendErrorFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for error append redirection");
                    return;
                }
            } else {
                commandWithArgs.add(args.get(i));
            }
        }

        ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);
        if (outputFile != null) {
            File outputFileObj = new File(outputFile);
            if (!outputFileObj.getParentFile().exists()) {
                if (!outputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for output redirection file");
                    return;
                }
            }
            processBuilder.redirectOutput(ProcessBuilder.Redirect.to(outputFileObj));
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE); // Capture error stream
        } else if (appendOutputFile != null) {
            File appendOutputFileObj = new File(appendOutputFile);
            if (!appendOutputFileObj.getParentFile().exists()) {
                if (!appendOutputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for append redirection file");
                    return;
                }
            }
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(appendOutputFileObj));
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE); // Capture error stream
        } else if (errorFile != null) {
            File errorFileObj = new File(errorFile);
            if (!errorFileObj.getParentFile().exists()) {
                if (!errorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error redirection file");
                    return;
                }
            }
            processBuilder.redirectError(ProcessBuilder.Redirect.to(errorFileObj));
        } else if (appendErrorFile != null) {
            File appendErrorFileObj = new File(appendErrorFile);
            if (!appendErrorFileObj.getParentFile().exists()) {
                if (!appendErrorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error append redirection file");
                    return;
                }
            }
            processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(appendErrorFileObj));
        } else {
            processBuilder.redirectErrorStream(true);
        }

        try {
            Process process = processBuilder.start();
            if (outputFile == null && appendOutputFile == null && errorFile == null && appendErrorFile == null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            } else if (outputFile != null || appendOutputFile != null) {
                if (errorFile == null && appendErrorFile == null) {
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null) {
                            System.err.println(errorLine); // Print error messages
                        }
                    }
                }
            } else if (errorFile != null || appendErrorFile != null) {
                try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String outputLine;
                    while ((outputLine = outputReader.readLine()) != null) {
                        System.out.println(outputLine); // Print output
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (outputFile != null || appendOutputFile != null || errorFile != null || appendErrorFile != null) {
                    // Do not print generic error message if output or error is redirected
                } else {
                    System.err.println(command + ": command failed with exit code " + exitCode);
                }
            }
        } catch (IOException e) {
            if (e.getMessage().contains("Cannot run program")) {
                System.err.println(command + ": command not found");
            } else {
                System.err.println(command + ": " + e.getMessage());
            }
        } catch (InterruptedException e) {
            System.err.println(command + ": process interrupted");
            Thread.currentThread().interrupt();
        }
    }


    public static class AutoCompleter {
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
}


