import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;
import java.util.Scanner;



public class Main {
    private static File currentDirectory = new File(System.getProperty("user.dir")); // Track current directory manually

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Define built-in commands handled by the shell
        Set<String> builtins = Set.of("echo", "exit", "type", "pwd", "cd");

        while (true) {
            System.out.print("$ "); // Display shell prompt

            String input = scanner.nextLine().trim(); // Read and trim user input
            if (input.isEmpty()) 
                continue; // Ignore empty inputs

            String[] tokens = input.split("\\s+"); // Split input into tokens
            String command = tokens[0]; // Extract command name

            if (command.equals("exit") && tokens.length > 1 && tokens[1].equals("0")) {
                break; // Exit shell on "exit 0"
            } else if (command.equals("echo")) {
                System.out.println(input.substring(5)); // Print everything after "echo "
            } else if (command.equals("pwd")) {
                System.out.println(currentDirectory.getAbsolutePath()); // Print working directory
            } else if (command.equals("cd")) {
                if (tokens.length < 2) {
                    System.out.println("cd: missing operand");
                } else {
                    changeDirectory(tokens[1]); // Handle "cd"
                }
            } else if (command.equals("type")) {
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
                runExternalCommand(tokens); // Try running external programs
            }
        }
        scanner.close(); // Close scanner on exit
    }

    /**
     * Changes the working directory.
     * Supports both absolute and relative paths.
     * @param path The new directory path.
     */
    private static void changeDirectory(String path) {
        File newDir = new File(path);
        // If the given path is relative, resolve it against the current directory
        if (!newDir.isAbsolute()) {
            newDir = new File(currentDirectory, path);
        }
        if (!newDir.exists() || !newDir.isDirectory()) {
            System.out.println("cd: " + path + ": No such directory");
            return;
        }
        // Update the current directory
        currentDirectory = newDir;
    }

    /**
     * Searches for an executable in the system's PATH.
     * @param command The command to search for.
     * @return The absolute path of the executable if found, otherwise null.
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
     * @param commandParts The command and its arguments.
     */
    private static void runExternalCommand(String[] commandParts) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
        // Ensure the process starts in the correct working directory
        processBuilder.directory(currentDirectory);
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
