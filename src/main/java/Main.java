import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Define built-in commands handled directly by the shell
        Set<String> builtins = new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd", "cd"));

        while (true) {
            System.out.print("$ "); // Display the shell prompt
            String input = scanner.nextLine().trim(); // Read user input and trim whitespace

            if (input.isEmpty()) continue; // Skip empty inputs

            String[] tokens = input.split("\\s+"); // Tokenize input by spaces
            String command = tokens[0]; // Extract the command name

            if (command.equals("exit") && tokens.length > 1 && tokens[1].equals("0")) {
                break; // Exit shell on "exit 0"
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
            } else if (command.equals("cd")) {
                // Handle "cd" command
                changeDirectory(tokens);
            } else {
                // Try to execute external commands
                runExternalCommand(tokens);
            }
        }
        scanner.close(); // Close the scanner on exit
    }

    /**
     * Changes the working directory, supporting absolute, relative, and home (~) paths.
     * @param tokens The parsed command tokens (includes "cd" and arguments).
     */
    private static void changeDirectory(String[] tokens) {
        String homeDir = System.getProperty("user.home");
        String newPath;

        // If no argument is given, default to home directory
        if (tokens.length < 2) {
            newPath = homeDir;
        } else {
            String path = tokens[1];

            // Handle "cd ~" and "cd ~/path"
            if (path.equals("~")) {
                newPath = homeDir;
            } else if (path.startsWith("~/")) {
                newPath = homeDir + path.substring(1);
            } else {
                File newDir = new File(path);
                if (!newDir.isAbsolute()) {
                    newDir = new File(System.getProperty("user.dir"), path); // Convert to absolute
                }
                try {
                    newPath = newDir.getCanonicalPath(); // Resolve symbolic links and ".."
                } catch (IOException e) {
                    System.out.println("cd: " + path + ": Invalid path");
                    return;
                }
            }
        }

        File targetDir = new File(newPath);

        // Validate the directory
        if (targetDir.exists() && targetDir.isDirectory()) {
            System.setProperty("user.dir", targetDir.getAbsolutePath());
        } else {
            System.out.println("cd: " + newPath + ": No such file or directory");
        }
    }

    /**
     * Searches for an executable in the system's PATH environment variable.
     * @param command The command name to search for.
     * @return The absolute path of the executable if found, otherwise null.
     */
    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        String[] paths = pathEnv.split(File.pathSeparator);

        for (String dir : paths) {
            File file = new File(dir, command);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    /**
     * Runs an external program with arguments.
     * @param commandParts The command and its arguments.
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
