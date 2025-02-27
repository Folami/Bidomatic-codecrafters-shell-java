import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public class Main {

    private static final String shellHome = System.getProperty("user.dir");
    private static final List<String> shBuiltins = List.of("echo", "exit", "type", "pwd", "cd");

    public static void main(String[] args) {
        while (true) {
            String commandLine = inputPrompt();
            if (commandLine == null || commandLine.isEmpty()) {
                continue;
            }
            try {
                List<String> tokens = Shlex.split(commandLine, true, true);
                if (tokens == null || tokens.isEmpty()) {
                    continue;
                }

                String command = tokens.get(0);
                List<String> commandArgs = tokens.subList(1, tokens.size());
                executeCommand(command, commandArgs);

            } catch (IllegalArgumentException e) {
                System.out.println("Error parsing command: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("An error occurred: " + e.getMessage());
            }
        }
    }

    private static String inputPrompt() {
        System.out.print("$ ");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private static void executeCommand(String command, List<String> args) throws IOException {
        switch (command) {
            case "exit":
                exitShell();
                break;
            case "echo":
                executeEcho(args);
                break;
            case "type":
                executeType(args);
                break;
            case "pwd":
                executePwd();
                break;
            case "cd":
                executeCd(args);
                break;
            default:
                runExternalCommand(command, args);
                break;
        }
    }

    private static void exitShell() {
        System.exit(0);
    }

    private static void executeEcho(List<String> args) throws IOException {
        String outputFile = null;
        List<String> echoArgs = new ArrayList<>();

        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(">") || args.get(i).equals("1>")) {
                if (i + 1 < args.size()) {
                    outputFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for redirection");
                    return;
                }
            } else {
                echoArgs.add(args.get(i));
            }
        }

        String output = String.join(" ", echoArgs);

        if (outputFile != null) {
            try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter(outputFile))) {
                out.println(output);
            }
        } else {
            System.out.println(output);
        }
    }


    private static void executeType(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("type: missing operand");
            return;
        }

        String targetCommand = args.get(0);
        if (shBuiltins.contains(targetCommand)) {
            System.out.println(targetCommand + " is a shell builtin");
        } else {
            String executable = findExecutable(targetCommand);
            if (executable != null) {
                System.out.println(targetCommand + " is " + executable);
            } else {
                System.out.println(targetCommand + ": not found");
            }
        }
    }

    private static void executePwd() {
        System.out.println(System.getProperty("user.dir"));
    }

    private static void executeCd(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("cd: missing operand");
            return;
        }
        String newDir = args.get(0);
        if (newDir.startsWith("~")) {
            newDir = System.getenv("HOME") + newDir.substring(1);
        }
        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path newPath = currentPath.resolve(newDir).normalize();
        try {
            if (Files.exists(newPath) && Files.isDirectory(newPath)) {
                System.setProperty("user.dir", newPath.toString());
            } else {
                System.err.println("cd: " + newDir + ": No such file or directory");
            }
        } catch (Exception e) {
            System.err.println("cd: " + newDir + ": " + e.getMessage());
        }
    }


    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] paths = pathEnv.split(File.pathSeparator);
            for (String dir : paths) {
                Path filePath = Paths.get(dir, command);
                if (Files.exists(filePath) && Files.isExecutable(filePath)) {
                    return filePath.toString();
                }
            }
        }
        // Check in the current directory
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        Path filePath = currentDir.resolve(command);
        if (Files.exists(filePath) && Files.isExecutable(filePath)) {
            return filePath.toString();
        }
        return null;
    }


    private static void runExternalCommand(String command, List<String> args) throws IOException {
        if (findExecutable(command) == null) {
            System.err.println(command + ": command not found");
            return;
        }
        List<String> commandWithArgs = new ArrayList<>();
        commandWithArgs.add(command);
        
        String outputFile = null;
        String errorFile = null;
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(">") || args.get(i).equals("1>")) {
                if (i + 1 < args.size()) {
                    outputFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for redirection");
                    return;
                }
            } else if (args.get(i).equals("2>")) {
                if (i + 1 < args.size()) {
                    errorFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for error redirection");
                    return;
                }
            } else {
                commandWithArgs.add(args.get(i));
            }
        }
        ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);
        if (outputFile != null) {
            processBuilder.redirectOutput(ProcessBuilder.Redirect.to(new File(outputFile)));
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE); // Capture error stream
        } else if (errorFile != null) {
            processBuilder.redirectError(ProcessBuilder.Redirect.to(new File(errorFile)));
        } else {
            processBuilder.redirectErrorStream(true);
        }
        try {
            Process process = processBuilder.start();
            if (outputFile == null && errorFile == null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            } else if (outputFile != null) {
                // Output is redirected, capture error stream if not redirected to a file
                if (errorFile == null) {
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null) {
                            System.err.println(errorLine); // Print error messages
                        }
                    }
                }
            } else if (errorFile != null) {
                // Error is redirected, capture output stream if not redirected to a file
                try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String outputLine;
                    while ((outputLine = outputReader.readLine()) != null) {
                        System.out.println(outputLine); // Print output
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (outputFile != null || errorFile != null) {
                    // Do not print generic error message if output or error is redirected
                    // System.err.println(command + ": command failed with exit code " + exitCode);
                } else {
                    System.err.println(command + ": command failed with exit code " + exitCode);
                }
            }
        } catch (IOException e) {
            if (e.getMessage().contains("Cannot run program")) {
                System.err.println(command + ": command not found");
            } else if (e.getMessage().contains("No such file or directory")) {
                System.err.println("Error: unable to redirect output or error to file");
            } else {
                System.err.println(command + ": " + e.getMessage());
            }
        } catch (InterruptedException e) {
            System.err.println(command + ": process interrupted");
            Thread.currentThread().interrupt();
        }
    }
 
}
