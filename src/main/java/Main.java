import java.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.BufferedReader;
import java.io.FileReader;

public class Main {
    // BUILTINS: A set containing the names of commands that are built into the shell.
    // These commands are handled directly by the shell itself, rather than by running
    // external programs.  Using a Set allows for efficient checking if a command
    // is a built-in.
    private static final Set<String> BUILTINS = new HashSet<>(
        Arrays.asList("echo", "exit", "type", "pwd", "cd")
    );
    // scanner: A Scanner object used to read input from the user.  It reads from
    // System.in, which is the standard input stream (usually the keyboard).
    private static final Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) {
        // Main shell loop.  This loop continuously prompts the user for commands,
        // processes them, and repeats until the user enters the "exit" command or
        // signals the end of input (e.g., by pressing Ctrl+D).
        while (true) {
            String input = promptAndGetInput(); // Get a line of input from the user.
            if (input == null)  // Check for end-of-input (Ctrl+D).  This signals that
                break; // the user has finished providing input, so we exit the loop.

            String[] tokens = splitPreservingQuotes(input); // Split the input into tokens,                                                  
                                                            // respecting quotes so that arguments
                                                            // containing spaces are treated as single units.
            if (tokens.length == 0) // Check if the input was empty (e.g., user just pressed Enter).
                continue; // If empty, go to the next iteration of the shell loop
                          // without trying to execute anything.
                executeCommand(tokens); // Execute the command specified by the tokens.
            }
            scanner.close(); // Close the scanner to release any system resources it's using.
        }
        
    // promptAndGetInput(): Prints the shell prompt ("$ ") and reads a line of input
    // from the user.
    private static String promptAndGetInput() {
        System.out.print("$ "); // Print the shell prompt to indicate it's ready for input.
        return scanner.hasNextLine() // Check if there's more input available.  This is a non-blocking
                                     // call; it returns immediately.
            ? scanner.nextLine().trim() // Read the line of input, and then .trim() removes any
                                        // leading or trailing whitespace (spaces, tabs, etc.)
            : null; // Return null if there's no more input (end-of-file).
    }
    
    // executeCommand(): Determines what to do based on the command the user entered.
    private static void executeCommand(String[] tokens) {
        String command = tokens[0]; // The first token is the command name.
        if (command.equals("exit") && tokens.length > 1 && tokens[1].equals("0")) {
            System.exit(0); // Exit the shell if the command is "exit 0".  A non-zero
                            // exit code could be used to indicate an error, but this
                            // simple shell always exits with 0 (success).
        } else if (command.equals("echo")) {
            executeEcho(tokens); // Execute the echo command.
        } else if (command.equals("pwd")) {
            executePwd(); // Execute the pwd (print working directory) command.
        } else if (command.equals("type")) {
            executeType(tokens); // Execute the type command (tells whether a command is
                                 // a built-in or an external program).
        } else if (command.equals("cd")) {
            executeCd(tokens); // Execute the cd (change directory) command.
        } else {
            runExternalCommand(tokens); // If the command is not a built-in, try to run it
                                        // as an external program.
        }
    }
    
    // executeEcho(): Handles the "echo" command.
    private static void executeEcho(String[] tokens) {
        if (tokens.length > 1) {
            System.out.println(String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length))); // Print the arguments, joined by spaces.
        } else {
            System.out.println(); // Print a newline if no arguments are given to "echo".
        }
    }
    
    // executePwd(): Handles the "pwd" command.
    private static void executePwd() {
        System.out.println(System.getProperty("user.dir")); // Print the current directory. System.getProperty("user.dir")
                                                            // gets the value of the "user.dir" system property, which
                                                            // holds the path to the current working directory.
    }

    // executeType(): Handles the "type" command.
    private static void executeType(String[] tokens) {
        if (tokens.length < 2) {
            System.out.println("type: missing operand"); // Print an error if no command name is provided.
            return;
        }
        String targetCommand = tokens[1]; // The command to check.
        if (BUILTINS.contains(targetCommand)) {
            System.out.println(targetCommand + " is a shell builtin"); // It's a built-in command.
        } else {
            String path = findExecutable(targetCommand); // Try to find the executable file.
            System.out.println(targetCommand + (path != null ? " is " + path : " not found")); // Print the result.
        }
    }
    
    // executeCd(): Handles the "cd" command.
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

    private static void executeCat(String[] tokens) {
        if (tokens.length < 2) {
            System.out.println("cat: missing operand");
            return;
        }
        try {
            StringBuilder concatenatedOutput = new StringBuilder();
            for (int i = 1; i < tokens.length; i++) {
                String filePath = tokens[i];
                File file = new File(filePath);
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    concatenatedOutput.append(line).append("\n");
                }
            }
            System.out.print(concatenatedOutput.toString());
        } catch (IOException e) {
            System.err.println("cat: An I/O error occurred: " + e.getMessage());
        }
    }
    
    // findExecutable(): Searches the PATH environment variable for an executable file.
    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH"); // Get the PATH environment variable.
        if (pathEnv == null) // PATH is not set
            return null;
            
        for (String dir : pathEnv.split(File.pathSeparator)) { // Split PATH into directories.
            File file = new File(dir, command); // Create a File object for the command.
            if (file.isFile() && file.canExecute()) // Check if it's a file and executable.
                return file.getAbsolutePath(); // Return the absolute path.
            }
        return null; // Return null if not found.
    }

    // runExternalCommand(): Runs an external command using ProcessBuilder.
    private static void runExternalCommand(String[] commandParts) {
        String command1 = commandParts[0];
        if (command1.equals("cat")) {
            executeCat(commandParts);
            return; // Cat is handled separately.
        }
        try {
            List<String> command = new ArrayList<>();
            command.add("/bin/sh");
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
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
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
}
