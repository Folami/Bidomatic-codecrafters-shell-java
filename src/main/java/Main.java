import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    // Store the initial working directory as the shell's home
    private static final String homeDirectory = System.getProperty("user.dir");
    // List of built-in commands supported by the shell
    private static final List<String> builtinCommands = List.of("echo", "exit", "type", "pwd", "cd");
    // List to hold possible tab completion options
    private static List<String> tabCompletionOptions = new ArrayList<>();
    // Counter to track the number of Tab presses for autocompletion
    private static int tabPressCount = 0;

    public static void main(String[] args) {
        // Run the shell in a continuous loop
        while (true) {
            // Get user input from the prompt
            String commandLine = getUserInput();
            // Skip empty or null input
            if (commandLine == null || commandLine.isEmpty()) {
                continue;
            }
            try {
                // Parse the command line into tokens using Shlex
                List<String> tokens = Shlex.split(commandLine, true, true);
                // Skip if no tokens are parsed
                if (tokens == null || tokens.isEmpty()) {
                    continue;
                }
                // Extract command and arguments
                String command = tokens.get(0);
                List<String> arguments = tokens.subList(1, tokens.size());
                // Execute the command
                runCommand(command, arguments);
            } catch (IllegalArgumentException e) {
                // Report parsing errors
                System.out.println("Error parsing command: " + e.getMessage());
            } catch (IOException e) {
                // Report I/O errors
                System.out.println("An error occurred: " + e.getMessage());
            }
        }
    }

    private static String getUserInput() {
        /*
         * Prompts for and returns user input, handling Tab for autocompletion.
         * Uses BufferedReader for character-by-character input to detect Tab presses.
         */
        System.out.print("$ ");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder inputBuffer = new StringBuilder();
        try {
            while (true) {
                // Read a single character from input
                int charRead = reader.read();
                // Handle end-of-input (Ctrl+D or EOF)
                if (charRead == -1) {
                    return null;
                }
                // Handle Enter key to submit input
                else if (charRead == '\n') {
                    System.out.println();  // Move to next line
                    String input = inputBuffer.toString().trim();
                    return input.isEmpty() ? "" : input;  // Return trimmed input or empty string
                }
                // Handle Tab key for autocompletion
                else if (charRead == '\t') {
                    String currentText = inputBuffer.toString().trim();
                    String completed = complete(currentText, tabPressCount);
                    if (completed != null) {
                        // Overwrite prompt with completed text if a completion is returned
                        inputBuffer.setLength(0);
                        inputBuffer.append(completed);
                        System.out.print("\r$ " + inputBuffer);
                    }
                    System.out.flush();
                }
                // Handle backspace (ASCII 127 or 8)
                else if (charRead == 127 || charRead == 8) {
                    if (inputBuffer.length() > 0) {
                        // Remove last character and update display
                        inputBuffer.setLength(inputBuffer.length() - 1);
                        System.out.print("\b \b");
                    }
                }
                // Append any other character to input
                else {
                    inputBuffer.append((char) charRead);
                    System.out.print((char) charRead);
                    System.out.flush();
                }
            }
        } catch (IOException e) {
            return null;  // Return null on I/O error
        }
    }

    private static String complete(String currentText, int state) {
        /*
         * Handles tab completion for built-ins and external commands.
         * Args:
         *   currentText: The text to be completed.
         *   state: The number of Tab presses (tracked externally).
         * Returns: The completed text or null.
         */
        // On first Tab press, gather options
        if (state == 0) {
            tabCompletionOptions = collectCompletionOptions(currentText);
            if (tabCompletionOptions.size() > 1) {
                // Compute longest common prefix for multiple matches
                String commonPrefix = computeLongestCommonPrefix(tabCompletionOptions);
                if (!commonPrefix.equals(currentText)) {
                    return commonPrefix;  // Return LCP if it extends input
                }
            }
            // Handle multiple matches with bell and list behavior
            if (tabCompletionOptions.size() > 1 && tabPressCount == 1) {
                System.out.write(7);  // ASCII bell (\a)
                System.out.flush();
                return null;  // No completion yet
            } else if (tabCompletionOptions.size() > 1 && tabPressCount == 2) {
                // Print options with two spaces and reprint prompt
                System.out.println("\n" + String.join("  ", tabCompletionOptions));
                System.out.print("$ " + currentText);
                tabPressCount = 0;  // Reset after listing
                return null;
            }
        }

        // Single match or cycling through options
        if (state < tabCompletionOptions.size()) {
            return tabCompletionOptions.get(state) + " ";
        }
        tabPressCount = 0;  // Reset if no more options
        return null;
    }

    private static List<String> collectCompletionOptions(String text) {
        /*
         * Collects built-in and external command names matching the input text.
         * Args:
         *   text: The text to match against.
         * Returns: List of matching command names.
         */
        List<String> builtinMatches = new ArrayList<>();
        // Filter built-in commands that start with the input text
        for (String cmd : builtinCommands) {
            if (cmd.startsWith(text)) {
                builtinMatches.add(cmd);
            }
        }

        // Set to store unique external executable names
        Set<String> externalMatches = new HashSet<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            // Split PATH into directories
            String[] directories = pathEnv.split(File.pathSeparator);
            for (String directory : directories) {
                File dir = new File(directory);
                if (dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            // Check if file is executable and matches text
                            if (file.isFile() && Files.isExecutable(file.toPath()) && 
                                file.getName().startsWith(text) && 
                                !builtinCommands.contains(file.getName())) {
                                externalMatches.add(file.getName());
                            }
                        }
                    }
                }
            }
        }

        // Combine and sort options
        List<String> allOptions = new ArrayList<>(builtinMatches);
        allOptions.addAll(externalMatches);
        allOptions.sort(String::compareTo);
        return allOptions;
    }

    private static String computeLongestCommonPrefix(List<String> options) {
        /*
         * Computes the longest common prefix of a list of strings.
         * Args:
         *   options: List of strings to compare.
         * Returns: Longest common prefix.
         */
        if (options.isEmpty()) {
            return "";
        }
        if (options.size() == 1) {
            return options.get(0);
        }
        // Sort to compare first and last for efficiency
        List<String> sortedOptions = new ArrayList<>(options);
        sortedOptions.sort(String::compareTo);
        String first = sortedOptions.get(0);
        String last = sortedOptions.get(sortedOptions.size() - 1);
        
        int i = 0;
        while (i < first.length() && i < last.length() && first.charAt(i) == last.charAt(i)) {
            i++;
        }
        return first.substring(0, i);
    }

    private static void runCommand(String command, List<String> arguments) throws IOException {
        /*
         * Executes the given command with its arguments.
         * Args:
         *   command: The command to execute.
         *   arguments: List of arguments for the command.
         */
        switch (command) {
            case "exit":
                runExit();  // Exit the shell
                break;
            case "echo":
                runEcho(arguments);  // Run echo command
                break;
            case "type":
                runType(arguments);  // Run type command
                break;
            case "pwd":
                runPwd();  // Run pwd command
                break;
            case "cd":
                runCd(arguments);  // Run cd command
                break;
            default:
                runExternalCommand(command, arguments);  // Run external command
                break;
        }
    }

    private static void runExit() {
        // Exit the shell program cleanly
        System.exit(0);
    }

    private static void runEcho(List<String> arguments) throws IOException {
        /*
         * Executes the echo command with redirection support.
         * Args:
         *   arguments: List of arguments for echo.
         */
        ParsedCommand parsed = parseRedirection(arguments);
        String outputString = String.join(" ", parsed.commandArgs);
        processOutput(outputString, parsed.redirections);
    }

    private static void runType(List<String> arguments) {
        /*
         * Executes the type command to identify command type.
         * Args:
         *   arguments: List of arguments (command to type).
         */
        if (arguments.isEmpty()) {
            System.out.println("type: missing operand");
            return;
        }
        String targetCommand = arguments.get(0);
        if (builtinCommands.contains(targetCommand)) {
            System.out.println(targetCommand + " is a shell builtin");
        } else {
            String executablePath = findExecutable(targetCommand);
            if (executablePath != null) {
                System.out.println(targetCommand + " is " + executablePath);
            } else {
                System.out.println(targetCommand + ": not found");
            }
        }
    }

    private static void runPwd() {
        // Prints the current working directory
        System.out.println(System.getProperty("user.dir"));
    }

    private static void runCd(List<String> arguments) {
        /*
         * Executes the cd command to change directory.
         * Args:
         *   arguments: List containing the target directory.
         */
        if (arguments.isEmpty()) {
            System.out.println("cd: missing operand");
            return;
        }
        String targetDir = arguments.get(0);
        // Expand ~ to user's home directory if present
        if (targetDir.startsWith("~")) {
            targetDir = System.getenv("HOME") + targetDir.substring(1);
        }
        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path newPath = currentPath.resolve(targetDir).normalize();
        try {
            // Check if the new path exists and is a directory
            if (Files.exists(newPath) && Files.isDirectory(newPath)) {
                System.setProperty("user.dir", newPath.toString());
            } else {
                System.err.println("cd: " + targetDir + ": No such file or directory");
            }
        } catch (Exception e) {
            System.err.println("cd: " + targetDir + ": " + e.getMessage());
        }
    }

    private static String findExecutable(String command) {
        /*
         * Finds the full path of an executable in PATH or current directory.
         * Args:
         *   command: The command to locate.
         * Returns: Full path or null if not found.
         */
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] directories = pathEnv.split(File.pathSeparator);
            for (String directory : directories) {
                Path filePath = Paths.get(directory, command);
                if (Files.exists(filePath) && Files.isExecutable(filePath)) {
                    return filePath.toString();
                }
            }
        }
        // Check current directory
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path filePath = currentDir.resolve(command);
        if (Files.exists(filePath) && Files.isExecutable(filePath)) {
            return filePath.toString();
        }
        return null;
    }

    private static void runExternalCommand(String command, List<String> arguments) throws IOException {
        /*
         * Runs an external command with redirection support.
         * Args:
         *   command: The external command to run.
         *   arguments: List of arguments for the command.
         */
        String executablePath = findExecutable(command);
        if (executablePath == null) {
            System.err.println(command + ": command not found");
            return;
        }
        ParsedCommand parsed = parseRedirection(command, arguments);
        executeProcess(parsed.commandArgs, parsed.redirections);
    }

    private static ParsedCommand parseRedirection(String command, List<String> arguments) {
        /*
         * Parses redirection operators from command and arguments.
         * Args:
         *   command: The command string.
         *   arguments: List of arguments.
         * Returns: ParsedCommand object with command args and redirections.
         */
        List<String> commandArgs = new ArrayList<>();
        commandArgs.add(command);  // Start with the command itself
        Redirections redirections = new Redirections();

        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            if (arg.equals(">") || arg.equals("1>")) {
                if (i + 1 < arguments.size()) {
                    redirections.stdoutFile = arguments.get(i + 1);
                    redirections.stdoutMode = "w";
                    i++;  // Skip the filename
                } else {
                    System.err.println("Syntax error: no file specified for redirection");
                }
            } else if (arg.equals(">>") || arg.equals("1>>")) {
                if (i + 1 < arguments.size()) {
                    redirections.stdoutFile = arguments.get(i + 1);
                    redirections.stdoutMode = "a";
                    i++;
                } else {
                    System.err.println("Syntax error: no file specified for append redirection");
                }
            } else if (arg.equals("2>")) {
                if (i + 1 < arguments.size()) {
                    redirections.stderrFile = arguments.get(i + 1);
                    redirections.stderrMode = "w";
                    i++;
                } else {
                    System.err.println("Syntax error: no file specified for error redirection");
                }
            } else if (arg.equals("2>>")) {
                if (i + 1 < arguments.size()) {
                    redirections.stderrFile = arguments.get(i + 1);
                    redirections.stderrMode = "a";
                    i++;
                } else {
                    System.err.println("Syntax error: no file specified for error append redirection");
                }
            } else {
                commandArgs.add(arg);
            }
        }
        return new ParsedCommand(commandArgs, redirections);
    }

    private static ParsedCommand parseRedirection(List<String> arguments) {
        /*
         * Parses redirection operators from arguments (for echo).
         * Args:
         *   arguments: List of arguments.
         * Returns: ParsedCommand object with command args and redirections.
         */
        List<String> commandArgs = new ArrayList<>();
        Redirections redirections = new Redirections();

        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            if (arg.equals(">") || arg.equals("1>")) {
                if (i + 1 < arguments.size()) {
                    redirections.stdoutFile = arguments.get(i + 1);
                    redirections.stdoutMode = "w";
                    i++;  // Skip the filename
                } else {
                    System.err.println("Syntax error: no file specified for redirection");
                }
            } else if (arg.equals(">>") || arg.equals("1>>")) {
                if (i + 1 < arguments.size()) {
                    redirections.stdoutFile = arguments.get(i + 1);
                    redirections.stdoutMode = "a";
                    i++;
                } else {
                    System.err.println("Syntax error: no file specified for append redirection");
                }
            } else if (arg.equals("2>")) {
                if (i + 1 < arguments.size()) {
                    redirections.stderrFile = arguments.get(i + 1);
                    redirections.stderrMode = "w";
                    i++;
                } else {
                    System.err.println("Syntax error: no file specified for error redirection");
                }
            } else if (arg.equals("2>>")) {
                if (i + 1 < arguments.size()) {
                    redirections.stderrFile = arguments.get(i + 1);
                    redirections.stderrMode = "a";
                    i++;
                } else {
                    System.err.println("Syntax error: no file specified for error append redirection");
                }
            } else {
                commandArgs.add(arg);
            }
        }
        return new ParsedCommand(commandArgs, redirections);
    }

    private static void processOutput(String outputString, Redirections redirections) throws IOException {
        /*
         * Processes output based on redirection settings for echo.
         * Args:
         *   outputString: The string to output.
         *   redirections: Redirection settings.
         */
        try {
            // Handle stdout redirection
            if (redirections.stdoutFile != null) {
                File stdoutFile = new File(redirections.stdoutFile);
                if (!stdoutFile.getParentFile().exists()) {
                    if (!stdoutFile.getParentFile().mkdirs()) {
                        System.err.println("Error: unable to create directory for output redirection file");
                        return;
                    }
                }
                try (java.io.PrintWriter out = new java.io.PrintWriter(
                        new java.io.FileWriter(stdoutFile, redirections.stdoutMode.equals("a")))) {
                    out.println(outputString);  // Write with newline
                }
            } else {
                // No redirection, print to console
                System.out.println(outputString);
            }

            // Handle stderr redirection (empty for echo)
            if (redirections.stderrFile != null) {
                File stderrFile = new File(redirections.stderrFile);
                if (!stderrFile.getParentFile().exists()) {
                    if (!stderrFile.getParentFile().mkdirs()) {
                        System.err.println("Error: unable to create directory for error redirection file");
                        return;
                    }
                }
                try (java.io.PrintWriter errOut = new java.io.PrintWriter(
                        new java.io.FileWriter(stderrFile, redirections.stderrMode.equals("a")))) {
                    errOut.print("");  // Empty stderr output
                }
            }
        } catch (IOException e) {
            System.err.println("echo: " + e.getMessage());
        }
    }

    private static void executeProcess(List<String> commandArgs, Redirections redirections) throws IOException {
        /*
         * Executes an external process with redirection support.
         * Args:
         *   commandArgs: List of command and arguments.
         *   redirections: Redirection settings.
         */
        ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);
        // Configure stdout redirection
        if (redirections.stdoutFile != null) {
            File stdoutFile = new File(redirections.stdoutFile);
            if (!stdoutFile.getParentFile().exists()) {
                if (!stdoutFile.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for output redirection file");
                    return;
                }
            }
            processBuilder.redirectOutput(
                redirections.stdoutMode.equals("w") ? 
                ProcessBuilder.Redirect.to(stdoutFile) : 
                ProcessBuilder.Redirect.appendTo(stdoutFile)
            );
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);  // Capture stderr if stdout redirected
        }
        // Configure stderr redirection
        if (redirections.stderrFile != null) {
            File stderrFile = new File(redirections.stderrFile);
            if (!stderrFile.getParentFile().exists()) {
                if (!stderrFile.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error redirection file");
                    return;
                }
            }
            processBuilder.redirectError(
                redirections.stderrMode.equals("w") ? 
                ProcessBuilder.Redirect.to(stderrFile) : 
                ProcessBuilder.Redirect.appendTo(stderrFile)
            );
        }
        // Merge stderr into stdout if no stderr redirection
        if (redirections.stdoutFile == null && redirections.stderrFile == null) {
            processBuilder.redirectErrorStream(true);
        }

        try {
            Process process = processBuilder.start();
            // Handle output/error streams based on redirection
            if (redirections.stdoutFile == null && redirections.stderrFile == null) {
                // No redirection, print combined output
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            } else if (redirections.stdoutFile != null && redirections.stderrFile == null) {
                // Stdout redirected, print stderr
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        System.err.println(errorLine);
                    }
                }
            } else if (redirections.stderrFile != null && redirections.stdoutFile == null) {
                // Stderr redirected, print stdout
                try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String outputLine;
                    while ((outputLine = outputReader.readLine()) != null) {
                        System.out.println(outputLine);
                    }
                }
            }
            // Wait for process completion and check exit code
            int exitCode = process.waitFor();
            if (exitCode != 0 && redirections.stdoutFile == null && redirections.stderrFile == null) {
                System.err.println(commandArgs.get(0) + ": command failed with exit code " + exitCode);
            }
        } catch (IOException e) {
            if (e.getMessage().contains("Cannot run program")) {
                System.err.println(commandArgs.get(0) + ": command not found");
            } else {
                System.err.println(commandArgs.get(0) + ": " + e.getMessage());
            }
        } catch (InterruptedException e) {
            System.err.println(commandArgs.get(0) + ": process interrupted");
            Thread.currentThread().interrupt();
        }
    }

    // Helper class to store parsed command and redirection info
    private static class ParsedCommand {
        List<String> commandArgs;
        Redirections redirections;

        ParsedCommand(List<String> commandArgs, Redirections redirections) {
            this.commandArgs = commandArgs;
            this.redirections = redirections;
        }
    }

    // Helper class to store redirection settings
    private static class Redirections {
        String stdoutFile = null;
        String stdoutMode = "w";  // "w" for overwrite, "a" for append
        String stderrFile = null;
        String stderrMode = "w";

        Redirections() {}
    }

    // Shlex class remains unchanged as provided
    public static class Shlex {
        // ... (Original Shlex implementation unchanged) ...
        public static List<String> split(String s, boolean comments, boolean posix) throws IOException {
            if (s == null) {
                throw new IllegalArgumentException("s argument must not be null");
            }
            Shlex lex = new Shlex(s, null, posix, null);
            lex.whitespaceSplit = true;
            if (!comments) {
                lex.commenters = "";
            }
            return lex.split();
        }

        private StringReader instream;
        private String infile;
        private boolean posix;
        private String eof;
        private String commenters = "#";
        private String wordchars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
        private String whitespace = " \t\r\n";
        private boolean whitespaceSplit = false;
        private String quotes = "'\"";
        private String escape = "\\";
        private String escapedquotes = "\"";
        private String state = " ";
        private java.util.Deque<String> pushback = new java.util.LinkedList<>();
        private int lineno = 1;
        private int debug = 0;
        private String token = "";
        private java.util.Deque<Object[]> filestack = new java.util.LinkedList<>();
        private String source = null;
        private String punctuationChars = "";
        private java.util.Deque<Character> pushbackChars = new java.util.LinkedList<>();

        public Shlex(String instream, String infile, boolean posix, String punctuationChars) {
            this.instream = new StringReader(instream);
            this.infile = infile;
            this.posix = posix;
            this.punctuationChars = punctuationChars == null ? "" : punctuationChars;
            if (posix) {
                this.eof = null;
            } else {
                this.eof = "";
            }
            if (posix) {
                this.wordchars += "ßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞ";
            }
            if (!this.punctuationChars.isEmpty()) {
                this.wordchars += "~-./*?=";
                for (char c : this.punctuationChars.toCharArray()) {
                    this.wordchars = this.wordchars.replace(String.valueOf(c), "");
                }
            }
        }

        public List<String> split() throws IOException {
            List<String> tokens = new ArrayList<>();
            while (true) {
                String token = get_token();
                if (token == null) {
                    break;
                }
                tokens.add(token);
            }
            return tokens;
        }

        private void push_token(String tok) {
            if (debug >= 1) {
                System.out.println("shlex: pushing token " + tok);
            }
            pushback.addFirst(tok);
        }

        private String get_token() throws IOException {
            if (!pushback.isEmpty()) {
                String tok = pushback.removeFirst();
                if (debug >= 1) {
                    System.out.println("shlex: popping token " + tok);
                }
                return tok;
            }
            String raw = read_token();
            while (raw != null && raw.equals(eof)) {
                if (filestack.isEmpty()) {
                    return eof;
                } else {
                    pop_source();
                    raw = get_token();
                }
            }
            if (debug >= 1) {
                if (raw != null && !raw.equals(eof)) {
                    System.out.println("shlex: token=" + raw);
                } else {
                    System.out.println("shlex: token=EOF");
                }
            }
            return raw;
        }

        private String read_token() throws IOException {
            boolean quoted = false;
            String escapedstate = " ";
            while (true) {
                char nextchar;
                if (!punctuationChars.isEmpty() && !pushbackChars.isEmpty()) {
                    nextchar = pushbackChars.removeFirst();
                } else {
                    int readChar = instream.read();
                    if (readChar == -1) {
                        nextchar = '\0';
                    } else {
                        nextchar = (char) readChar;
                    }
                }
                if (nextchar == '\n') {
                    lineno++;
                }
                if (debug >= 3) {
                    System.out.println("shlex: in state " + state + " I see character: " + nextchar);
                }
                if (state == null) {
                    token = "";
                    break;
                } else if (state.equals(" ")) {
                    if (nextchar == '\0') {
                        state = null;
                        break;
                    } else if (whitespace.indexOf(nextchar) != -1) {
                        if (debug >= 2) {
                            System.out.println("shlex: I see whitespace in whitespace state");
                        }
                        if (!token.isEmpty() || (posix && quoted)) {
                            break;
                        } else {
                            continue;
                        }
                    } else if (commenters.indexOf(nextchar) != -1) {
                        instream.read();
                        lineno++;
                    } else if (posix && escape.indexOf(nextchar) != -1) {
                        escapedstate = "a";
                        state = String.valueOf(nextchar);
                    } else if (wordchars.indexOf(nextchar) != -1) {
                        token = String.valueOf(nextchar);
                        state = "a";
                    } else if (punctuationChars.indexOf(nextchar) != -1) {
                        token = String.valueOf(nextchar);
                        state = "c";
                    } else if (quotes.indexOf(nextchar) != -1) {
                        if (!posix) {
                            token = String.valueOf(nextchar);
                        }
                        state = String.valueOf(nextchar);
                    } else if (whitespaceSplit) {
                        token = String.valueOf(nextchar);
                        state = "a";
                    } else {
                        token = String.valueOf(nextchar);
                        if (!token.isEmpty() || (posix && quoted)) {
                            break;
                        } else {
                            continue;
                        }
                    }
                } else if (quotes.indexOf(state) != -1) {
                    quoted = true;
                    if (nextchar == '\0') {
                        throw new IllegalArgumentException("No closing quotation");
                    }
                    if (String.valueOf(nextchar).equals(state)) {
                        if (!posix) {
                            token += nextchar;
                            state = " ";
                            break;
                        } else {
                            state = "a";
                        }
                    } else if (posix && escape.indexOf(nextchar) != -1 && escapedquotes.indexOf(state) != -1) {
                        escapedstate = state;
                        state = String.valueOf(nextchar);
                    } else {
                        token += nextchar;
                    }
                } else if (escape.indexOf(state) != -1) {
                    if (nextchar == '\0') {
                        throw new IllegalArgumentException("No escaped character");
                    }
                    if (quotes.indexOf(escapedstate) != -1 && nextchar != state.charAt(0) && nextchar != escapedstate.charAt(0)) {
                        token += state;
                    }
                    token += nextchar;
                    state = escapedstate;
                } else if (state.equals("a") || state.equals("c")) {
                    if (nextchar == '\0') {
                        state = null;
                        break;
                    } else if (whitespace.indexOf(nextchar) != -1) {
                        state = " ";
                        if (!token.isEmpty() || (posix && quoted)) {
                            break;
                        } else {
                            continue;
                        }
                    } else if (commenters.indexOf(nextchar) != -1) {
                        instream.read();
                        lineno++;
                        if (posix) {
                            state = " ";
                            if (!token.isEmpty() || (posix && quoted)) {
                                break;
                            } else {
                                continue;
                            }
                        }
                    } else if (state.equals("c")) {
                        if (punctuationChars.indexOf(nextchar) != -1) {
                            token += nextchar;
                        } else {
                            if (whitespace.indexOf(nextchar) == -1) {
                                pushbackChars.addFirst(nextchar);
                            }
                            state = " ";
                            break;
                        }
                    } else if (posix && quotes.indexOf(nextchar) != -1) {
                        state = String.valueOf(nextchar);
                    } else if (posix && escape.indexOf(nextchar) != -1) {
                        escapedstate = "a";
                        state = String.valueOf(nextchar);
                    } else if (wordchars.indexOf(nextchar) != -1 || quotes.indexOf(nextchar) != -1
                            || (whitespaceSplit && punctuationChars.indexOf(nextchar) == -1)) {
                        token += nextchar;
                    } else {
                        if (!punctuationChars.isEmpty()) {
                            pushbackChars.addFirst(nextchar);
                        } else {
                            pushback.addFirst(String.valueOf(nextchar));
                        }
                        state = " ";
                        if (!token.isEmpty() || (posix && quoted)) {
                            break;
                        } else {
                            continue;
                        }
                    }
                }
            }
            String result = token;
            token = "";
            if (posix && !quoted && result.isEmpty()) {
                result = null;
            }
            if (debug > 1) {
                if (result != null) {
                    System.out.println("shlex: raw token=" + result);
                } else {
                    System.out.println("shlex: raw token=EOF");
                }
            }
            return result;
        }

        private void pop_source() throws IOException {
            instream.close();
            Object[] sourceInfo = filestack.removeFirst();
            infile = (String) sourceInfo[0];
            instream = new StringReader((String) sourceInfo[1]);
            lineno = (int) sourceInfo[2];
            if (debug != 0) {
                System.out.println("shlex: popping to " + instream + ", line " + lineno);
            }
            state = " ";
        }
    }
}