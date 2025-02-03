import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Define built-in commands handled directly by the shell
        Set<String> builtins = Set.of("echo", "exit", "type", "pwd");

        while (true) {
            System.out.print("$ ");  // Display the shell prompt
            String input = scanner.nextLine().trim();  // Read user input and trim whitespace

            if (input.isEmpty()) continue;  // Skip empty inputs

            String[] tokens = input.split("\\s+"); // Tokenize input by spaces
            String command = tokens[0];  // Extract the command name

            if (command.equals("exit") && tokens.length > 1 && tokens[1].equals("0")) {
                break;  // Exit shell on "exit 0"
            } else if (command.equals("echo")) {
                // Print everything after "echo "
                System.out.println(input.substring(5));
            } else if (command.equals("pwd")) {
                // Print current working directory
                System.out.println(System.getProperty("user.dir"));
            } else if (command.equals("type")) {
                // Handle "type" command: check if built-in or an external executable
                if (tokens.length < 2) {
                    System.out.println("type: missing operand");
                    continue;
                }
                String targetCommand = tokens[1];
                if (builtins.contains(targetCommand)) {
                    System.out.println(targetCommand + " is a shell builtin");
                } else {
                    String path = findExecutable(targetCommand);
                    if (path != null) {
                        System.out.println(targetCommand + " is " + path);
                    } else {
                        System.out.println(targetCommand + " not found");
                    }
                }
            } else {
                // Try to execute external commands
                runExternalCommand(tokens);
            }
        }

        scanner.close(); // Close the scanner on exit
    }

    /**
     * Searches for an executable in the system's PATH environment variable.
     * @param command The command name to search for
     * @return The absolute path of the executable if found, otherwise null
     */
    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) 
            return null;

        String[] paths = pathEnv.split(File.pathSeparator);
        for (String dir : paths) {
            File file = new File(dir, command);
            if (file.isFile() && file.canExecute())
                return file.getAbsolutePath();
        }
        return null;
    }

    /**
     * Runs an external program with arguments.
     * @param commandParts The command and its arguments
     */
    private static void runExternalCommand(String[] commandParts) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
        processBuilder.inheritIO(); // Redirect process I/O to the terminal
        try {
            Process process = processBuilder.start(); // Start the process
            process.waitFor(); // Wait for it to complete
        } catch (IOException e) {
            System.out.println(commandParts[0] + ": command not found");
        } catch (InterruptedException e) {
            System.out.println("Process interrupted");
            Thread.currentThread().interrupt();
        }
    }
}
