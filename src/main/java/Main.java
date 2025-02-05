import java.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final Set<String> BUILTINS = new HashSet<>(
            Arrays.asList("echo", "exit", "type", "pwd", "cd")
    );
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            String input = promptAndGetInput(); // Get input from the user
            if (input == null) break; // Exit if input is null (e.g., Ctrl+D)
            String[] tokens = splitPreservingQuotes(input); // Split input into tokens, handling quotes
            if (tokens.length == 0) continue; // Skip empty input
            executeCommand(tokens); // Execute the command
        }
        scanner.close(); // Close the scanner
    }

    private static String promptAndGetInput() {
        System.out.print("$ "); // Print the prompt
        return scanner.hasNextLine() ? scanner.nextLine().trim() : null; // Read and trim input
    }

    private static void executeCommand(String[] tokens) {
        String command = tokens[0]; // Get the command name
        if (command.equals("exit") && tokens.length > 1 && tokens[1].equals("0")) {
            System.exit(0); // Exit the shell
        } else if (command.equals("echo")) {
            executeEcho(tokens); // Execute the echo command
        } else if (command.equals("pwd")) {
            executePwd(); // Execute the pwd command
        } else if (command.equals("type")) {
            executeType(tokens); // Execute the type command
        } else if (command.equals("cd")) {
            executeCd(tokens); // Execute the cd command
        } else {
            runExternalCommand(tokens); // Run an external command
        }
    }

    private static void executeEcho(String[] tokens) {
        if (tokens.length > 1) {
            System.out.println(String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length))); // Print the arguments
        } else {
            System.out.println(); // Print a newline if no arguments
        }
    }

    private static String[] splitPreservingQuotes(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        char quoteChar = 0; // Keep track of the current quote character (0 if no quote)
        boolean escape = false; // Flag to indicate if the previous character was a backslash

        for (char c : input.toCharArray()) {
            if (escape) {
                currentToken.append(c); // Append the escaped character
                escape = false; // Reset escape flag
            } else if (c == '\\') {
                escape = true; // Set escape flag
                // Do NOT append the backslash itself when escaping
            } else if ((c == ' ' || c == '\t') && quoteChar == 0) { // Space or tab outside quotes
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString()); // Add the current token to the list
                    currentToken.setLength(0); // Clear the current token
                }
            } else if ((c == '\'' || c == '"') && quoteChar == 0) { // Start of a quote
                quoteChar = c; // Set the quote character
                // Do NOT append the quote character itself when starting a quote
            } else if (c == quoteChar) { // End of a quote
                quoteChar = 0; // Reset the quote character
                // Do NOT append the quote character itself when ending a quote
            } else {
                currentToken.append(c); // Append the character to the current token
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString()); // Add the last token
        }

        String[] tokenArray = tokens.toArray(new String[0]);

        // Process escape sequences AFTER splitting:
        for (int i = 0; i < tokenArray.length; i++) {
            tokenArray[i] = processEscapeSequences(tokenArray[i]); // Process escape sequences in each token
        }

        return tokenArray;
    }

    private static String processEscapeSequences(String input) {
        StringBuilder output = new StringBuilder();
        boolean isEscaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (isEscaped) {
                switch (c) {
                    case 'n':
                        output.append("\n");
                        break; // Append newline
                    case 't':
                        output.append("\t");
                        break; // Append tab
                    case 'r':
                        output.append("\r");
                        break; // Append carriage return
                    case '\'':
                        output.append("'");
                        break; // Append single quote
                    case '"':
                        output.append("\"");
                        break; // Append double quote
                    case '\\':
                        output.append("\\");
                        break; // Append backslash
                    default:
                        output.append(c); // Append the character as is (handles other escaped chars)
                }
                isEscaped = false; // Reset escape flag
            } else if (c == '\\') {
                isEscaped = true; // Set escape flag
            } else {
                output.append(c); // Append the character
            }
        }

        if (isEscaped) {
            output.append('\\'); // Handle trailing backslash
        }

        return output.toString();
    }

    // ... (executePwd, executeType, executeCd, findExecutable remain the same)

    private static void runExternalCommand(String[] commandParts) {
        try {
            List<String> command = new ArrayList<>();
            command.add("/bin/sh"); // Use /bin/sh to handle shell features
            command.add("-c");
            StringBuilder cmd = new StringBuilder();

            for (int i = 0; i < commandParts.length; i++) {
                cmd.append(commandParts[i]);
                if (i < commandParts.length - 1) {
                    cmd.append(" ");
                }
            }
            command.add(cmd.toString());

            ProcessBuilder pb = new ProcessBuilder(command);
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
}