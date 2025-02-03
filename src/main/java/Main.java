import java.io.File;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Define built-in commands: These are handled directly by the shell.
        Set<String> builtins = Set.of("echo", "exit", "type");

        while (true) {
            System.out.print("$ ");  // Display the shell prompt
            String input = scanner.nextLine().trim();  // Read user input and remove leading/trailing spaces

            if (input.equals("exit 0")) {
                break;  // Exit the shell when the user types "exit 0"
            } else if (input.startsWith("echo ")) {
                // Handle the "echo" command: print everything after "echo "
                System.out.println(input.substring(5));
            } else if (input.startsWith("type ")) {
                // Handle the "type" command: check if a command is built-in or an executable
                String command = input.substring(5).trim();  // Extract the command name after "type"
                if (builtins.contains(command)) {
                    // If the command is a built-in, indicate it as such
                    System.out.println(command + " is a shell builtin");
                } else {
                    // Otherwise, search for the command in the system's PATH
                    String path = findExecutable(command);
                    if (path != null) {
                        System.out.println(command + " is " + path);
                    } else {
                        System.out.println(command + " not found");
                    }
                }
            } else {
                // If the command is not recognized, print an error message
                System.out.println(input + ": command not found");
            }
        }
        scanner.close(); // Close the scanner when exiting
    }

    /**
     * Searches for an executable in the system's PATH environment variable.
     * @param command The command name to search for
     * @return The absolute path of the executable if found, otherwise null
     */
    private static String findExecutable(String command) {
        // Get the PATH environment variable, which contains directories of executable files
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) 
            return null;  // If PATH is not set, return null

        // Split the PATH variable into an array of directories
        String[] paths = pathEnv.split(File.pathSeparator);

        // Iterate over each directory in PATH and check for the executable file
        for (String dir : paths) {
            File file = new File(dir, command);
            if (file.isFile() && file.canExecute()) { // Check if the file exists and is executable
                return file.getAbsolutePath();  // Return the full path of the executable
            }
        }
        return null; // Return null if the executable is not found
    }
}
