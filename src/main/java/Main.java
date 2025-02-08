import java.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    // Set of built-in commands.  These are handled directly by the shell.
    private static final Set<String> BUILTINS = new HashSet<>(
            Arrays.asList("echo", "exit", "type", "pwd", "cd")
    );
    // Scanner to read user input from the console.
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        // Main shell loop.  Continues until the user enters "exit" or Ctrl+D.
        while (true) {
            String input = promptAndGetInput(); // Get a line of input from the user.
            if (input == null)  // Check for end-of-input (Ctrl+D).
                break; // Exit the shell loop.
            String[] tokens = splitPreservingQuotes(input); // Split the input into tokens, respecting quotes.
            if (tokens.length == 0)  // Check if the input was empty.
                continue; // If empty, go to the next iteration of the loop.
            executeCommand(tokens); // Execute the command specified by the tokens.
        }
        scanner.close(); // Close the scanner to release resources.
    }

    // Prints the prompt and reads a line of input from the user.
    private static String promptAndGetInput() {
        System.out.print("$ "); // Print the shell prompt.
        return scanner.hasNextLine()  // Check if there's more input.
            ? scanner.nextLine().trim() // Read the line and remove leading/trailing whitespace.
            : null; // Return null if there's no more input (end-of-file).
    }

    // Executes the command given by the tokens.
    private static void executeCommand(String[] tokens) {
        String command = tokens[0]; // The first token is the command name.
        if (command.equals("exit") && tokens.length > 1 && tokens[1].equals("0")) {
            System.exit(0); // Exit the shell if the command is "exit 0".
        } else if (command.equals("echo")) {
            executeEcho(tokens); // Execute the echo command.
        } else if (command.equals("pwd")) {
            executePwd(); // Execute the pwd command.
        } else if (command.equals("type")) {
            executeType(tokens); // Execute the type command.
        } else if (command.equals("cd")) {
            executeCd(tokens); // Execute the cd command.
        } else {
            runExternalCommand(tokens); // Execute an external command.
        }
    }

    // Executes the echo command.
    private static void executeEcho(String[] tokens) {
        if (tokens.length > 1) {
            System.out.println(String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length))); // Print the arguments.
        } else {
            System.out.println(); // Print a newline if no arguments.
        }
    }

    // Executes the pwd command.
    private static void executePwd() {
        System.out.println(System.getProperty("user.dir")); // Print the current directory.
    }

    // Executes the type command.
    private static void executeType(String[] tokens) {
        if (tokens.length < 2) {
            System.out.println("type: missing operand"); // Print an error if no operand is given.
            return;
        }
        String targetCommand = tokens[1]; // The command to check.
        if (BUILTINS.contains(targetCommand)) {
            System.out.println(targetCommand + " is a shell builtin"); // It's a built-in command.
        } else {
            String path = findExecutable(targetCommand); // Try to find the executable.
            System.out.println(targetCommand + (path != null ? " is " + path : " not found")); // Print the result.
        }
    }

    // Executes the cd command.
    private static void executeCd(String[] tokens) {
        if (tokens.length < 2) {
            System.out.println("cd: missing operand"); // Error if no directory is given.
            return;
        }
        String path = tokens[1].replace("~", System.getenv("HOME")); // Replace ~ with the home directory.
        try {
            Path resolvedPath = Paths.get(System.getProperty("user.dir")).resolve(path).normalize(); // Resolve the path.
            File directory = resolvedPath.toFile(); // Get the File object.
            if (directory.exists() && directory.isDirectory()) {
                System.setProperty("user.dir", directory.getAbsolutePath()); // Change the current directory.
            } else {
                System.out.println("cd: " + path + ": No such file or directory"); // Error if the path is invalid.
            }
        } catch (Exception e) {
            System.out.println("cd: " + path + ": Invalid path"); // Handle potential exceptions.
        }
    }

    // Finds an executable file in the PATH.
    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH"); // Get the PATH environment variable.
        if (pathEnv == null)  // PATH is not set
            return null;
        for (String dir : pathEnv.split(File.pathSeparator)) { // Split PATH into directories.
            File file = new File(dir, command); // Create a File object for the command.
            if (file.isFile() && file.canExecute())  // Check if it's a file and executable.
                return file.getAbsolutePath(); // Return the absolute path.
        }
        return null; // Return null if not found.
    }

    


    // Splits the input string into tokens, handling quotes.
    private static String[] splitPreservingQuotes(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        char quoteChar = 0; // 0 means not in a quote, otherwise holds the quote character.
        boolean escape = false; // True if the previous char was a backslash.

        for (char c : input.toCharArray()) {
            if (escape) {
                currentToken.append(c); // Add the escaped char to the token.
                escape = false; // Reset escape flag.
            } else if (c == '\\') {
                escape = true; // Set escape flag.
            } else if ((c == ' ' || c == '\t') && quoteChar == 0) { // Space or tab outside quotes.
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString()); // Add current token to the list.
                    currentToken.setLength(0); // Reset current token.
                }
            } else if ((c == '\'' || c == '"') && quoteChar == 0) { // Start of a quote.
                quoteChar = c; // Remember which quote char we're using.
            } else if (c == quoteChar) { // End of a quote.
                quoteChar = 0; // Reset quote char.
            } else {
                currentToken.append(c); // Add the char to the current token.
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString()); // Add the last token.
        }

        String[] tokenArray = tokens.toArray(new String[0]);

        // Process escape sequences AFTER splitting:
        for (int i = 0; i < tokenArray.length; i++) {
            tokenArray[i] = processEscapeSequences(tokenArray[i]); // Process escape sequences in each token.
        }

        return tokenArray;
    }

    // Processes escape sequences within a string.
    private static String processEscapeSequences(String input) {
        StringBuilder output = new StringBuilder();
        boolean isEscaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (isEscaped) {
                switch (c) {
                    case 'n': output.append("\n"); break; // Append newline.
                    case 't': output.append("\t"); break; // Append tab.
                    case 'r': output.append("\r"); break; // Append carriage return.
                    case '\'': output.append("'"); break; // Append single quote.
                    case '"': output.append("\""); break; // Append double quote.
                    case '\\': output.append("\\"); break; // Append backslash.
                    default: output.append(c); // Append the character as is (handles other escaped chars).
                }
                isEscaped = false; // Reset escape flag.
            } else if (c == '\\') {
                isEscaped = true; // Set escape flag.
            } else {
                output.append(c); // Append the character.
            }
        }

        if (isEscaped) {
            output.append('\\'); // Handle trailing backslash.
        }

        return output.toString();
    }

    private static void runExternalCommand(String[] commandParts) {
        try {
            List<String> command = new ArrayList<>();
            command.add("/bin/sh"); // Use /bin/sh to handle shell features.
            command.add("-c");
            StringBuilder cmd = new StringBuilder();

            for (int i = 0; i < commandParts.length; i++) {
                cmd.append(commandParts[i]);
                if (i < commandParts.length - 1) {
                    cmd.append(" ");
                }
            }
            command.add(cmd.toString());

            ProcessBuilder pb = new ProcessBuilder(command); // Create ProcessBuilder with command.
            pb.inheritIO(); // Inherit standard input/output streams.
            Process process = pb.start(); // Start the process.
            int exitCode = process.waitFor(); // Wait for the process to finish and get the exit code.
            if (exitCode != 0) {
                System.err.println(commandParts[0] + ": command failed with exit code " + exitCode); // Print error message.
            }
        } catch (IOException e) {
            System.err.println(commandParts[0] + ": command not found or could not be executed"); // Handle IO exceptions.
        } catch (InterruptedException e) {
            System.err.println("Process interrupted"); // Handle interrupted exceptions.
            Thread.currentThread().interrupt(); // Interrupt the current thread.
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage()); // Handle other exceptions.
        }
    }
}