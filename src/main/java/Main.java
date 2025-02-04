import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Main {

    private static final Set<String> BUILTINS = new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd", "cd"));
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            String input = promptAndGetInput();
            if (input == null) break; // Exit if input is null (e.g., Ctrl+D)

            String[] tokens = input.split("\\s+");
            if (tokens.length == 0) continue; // Skip empty input

            String command = tokens[0];
            executeCommand(command, tokens);
        }
        scanner.close();
    }

    private static String promptAndGetInput() {
        System.out.print("$ ");
        if (scanner.hasNextLine()) {
            return scanner.nextLine().trim();
        }
        return null; // Handle end of input (Ctrl+D)
    }

    private static void executeCommand(String command, String[] tokens) {
        if (command.equals("exit") && tokens.length > 1 && tokens[1].equals("0")) {
            System.exit(0); // Use System.exit for immediate exit
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

    private static void executeEcho(String[] tokens) {
    if (tokens.length > 1) {  // Check if there are arguments to echo
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < tokens.length; i++) {
            sb.append(tokens[i]);
            if (i < tokens.length - 1) { // Add space only if not the last argument
                sb.append(" ");
            }
        }
        System.out.println(sb.toString());
    } else {
        System.out.println(); // Handle "echo" with no arguments (print a blank line)
    }
}

    private static void executePwd() {
        System.out.println(System.getProperty("user.dir"));
    }

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

    private static void executeCd(String[] tokens) {
        if (tokens.length < 2) {
            System.out.println("cd: missing operand");
            return;
        }
        String path = tokens[1];
        if (path.startsWith("~/") || path.equals("~")) {
            String homeDir = System.getenv("HOME"); // Use System.getenv("HOME") for better cross-platform compatibility
            if (homeDir == null) {
                System.out.println("cd: Home directory not set");
                return;
            }
            path = homeDir + path.substring(1);
        }
        try {
            Path resolvedPath = (
                path.startsWith("/") ? Paths.get(path).normalize() :
                Paths.get(System.getProperty("user.dir")).resolve(path).normalize()
            );
            File directory = resolvedPath.toFile();
            if (directory.exists() && directory.isDirectory()) {
                System.setProperty("user.dir", directory.getAbsolutePath());
            } else {
                System.out.println("cd: " + path + ": No such file or directory");
            }
        } catch (Exception e) {
            System.out.println("cd: " + path + ": Invalid path");
        }
    }

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File file = new File(dir, command);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static void runExternalCommand(String[] commandParts) {
        try {
            Process process = new ProcessBuilder(commandParts).inheritIO().start();
            process.waitFor();
        } catch (IOException e) {
            System.out.println(commandParts[0] + ": command not found");
        } catch (InterruptedException e) {
            System.out.println("Process interrupted");
            Thread.currentThread().interrupt();
        }
    }
}