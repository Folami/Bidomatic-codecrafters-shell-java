import java.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    // Set of built-in commands
    private static final Set<String> BUILTINS = new HashSet<>(
        Arrays.asList("echo", "exit", "type", "pwd", "cd")
    );
    // Scanner for reading user input
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            String input = promptAndGetInput();
            if (input == null) break; // Exit if input is null (e.g., Ctrl+D)
            String[] tokens = splitPreservingQuotes(input);
            if (tokens.length == 0) continue; // Skip empty input
            executeCommand(tokens);
        }
        scanner.close();
    }

    // Display prompt and get user input
    private static String promptAndGetInput() {
        System.out.print("$ ");
        return scanner.hasNextLine() ? scanner.nextLine().trim() : null;
    }

    // Execute the appropriate command based on the first token
    private static void executeCommand(String[] tokens) {
        String command = tokens[0];
        if (command.equals("exit") && tokens.length > 1 && tokens[1].equals("0")) {
            System.exit(0);
        } else if (command.equals("echo")) {
            executeEcho(tokens);
        } else if (command.equals("pwd")) {
            executePwd();
        } else if (command.equals("type")) {
            executeType(tokens);
        } else if (command.equals("cd")) {
            executeCd(tokens);
        } else {
            runExternalCommand(tokens);
        }
    }

    // Execute the echo command
    private static void executeEcho(String[] tokens) {
        if (tokens.length > 1) {
            // Join all tokens after "echo" and print
            System.out.println(String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)));
        } else {
            System.out.println(); // Print empty line if no arguments
        }
    }

    // Split input string into tokens, preserving quotes and handling escapes
    private static String[] splitPreservingQuotes(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        boolean escaped = false;

        for (char c : input.toCharArray()) {
            if (escaped) {
                // If escaped, add the character as-is
                currentToken.append(c);
                escaped = false;
            } else if (c == '\\') {
                // Set escaped flag for next character
                escaped = true;
                currentToken.append(c);
            } else if (c == '\'' || c == '"') {
                if (inQuotes && c == quoteChar) {
                    // End of quoted section
                    inQuotes = false;
                    quoteChar = 0;
                } else if (!inQuotes) {
                    // Start of quoted section
                    inQuotes = true;
                    quoteChar = c;
                }
                currentToken.append(c);
            } else if ((c == ' ' || c == '\t') && !inQuotes) {
                // Space outside quotes, end current token
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                // Add character to current token
                currentToken.append(c);
            }
        }

        // Add last token if exists
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens.toArray(new String[0]);
    }

    // Execute the pwd command
    private static void executePwd() {
        System.out.println(System.getProperty("user.dir"));
    }

    // Execute the type command
    private static void executeType(String[] tokens) {
        if (tokens.length < 2) {
            System.out.println("type: missing operand");
            return;
        }
        String targetCommand = tokens[1];
        if (BUILTINS.contains(targetCommand)) {
            System.out.println(targetCommand + " is a shell builtin");
        } else {
            String path = findExecutable(targetCommand);
            System.out.println(targetCommand + (path != null ? " is " + path : " not found"));
        }
    }

    // Execute the cd command
    private static void executeCd(String[] tokens) {
        if (tokens.length < 2) {
            System.out.println("cd: missing operand");
            return;
        }
        String path = tokens[1].replace("~", System.getenv("HOME"));
        Path resolvedPath = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        File directory = resolvedPath.toFile();
        if (directory.exists() && directory.isDirectory()) {
            System.setProperty("user.dir", directory.getAbsolutePath());
        } else {
            System.out.println("cd: " + path + ": No such file or directory");
        }
    }

        // Find the executable in the PATH
    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File file = new File(dir, command);
            if (file.isFile() && file.canExecute()) return file.getAbsolutePath();
        }
        return null;
    }

    // Run an external command
    private static void runExternalCommand(String[] commandParts) {
        try {
            List<String> processedArgs = new ArrayList<>();
            for (String arg : commandParts) {
                // Process each argument
                processedArgs.add(processArgument(arg));
            }

            ProcessBuilder pb = new ProcessBuilder(processedArgs);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println(commandParts[0] + ": command failed with exit code " + exitCode);
            }
        } catch (IOException e) {
            System.err.println(commandParts[0] + ": command not found or could not be executed");
        } catch (InterruptedException e) {
            System.err.println("Process interrupted");
            Thread.currentThread().interrupt();
        }
    }

    // Process an individual argument, handling quotes and escapes
    private static String processArgument(String arg) {
        StringBuilder processed = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        boolean escaped = false;

        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (escaped) {
                // If escaped, add the character as-is, except for 'n' which becomes a literal 'n'
                if (c == 'n') {
                    processed.append("\\n");
                } else {
                    processed.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                // Set escaped flag for next character
                escaped = true;
            } else if (c == '\'' || c == '"') {
                if (inQuotes && c == quoteChar) {
                    // End of quoted section
                    inQuotes = false;
                    quoteChar = 0;
                } else if (!inQuotes) {
                    // Start of quoted section
                    inQuotes = true;
                    quoteChar = c;
                } else {
                    // Quote character inside another type of quote, add as-is
                    processed.append(c);
                }
            } else {
                // Add character to processed argument
                processed.append(c);
            }
        }

        return processed.toString();
    }
}
