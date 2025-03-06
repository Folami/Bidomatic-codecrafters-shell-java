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
import java.io.Console;
import java.util.stream.Collectors;


public class Main {

    private static final String shellHome = System.getProperty("user.dir");
    private static final List<String> shBuiltins = List.of("echo", "exit", "type", "pwd", "cd");
    // Shlex object for parsing shell commands
    private static final Shlex shlex = new Shlex();

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
        Console console = System.console();
        if (console == null) {
            // Fallback for non-interactive terminals
            System.out.print("$ ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                return reader.readLine();
            } catch (IOException e) {
                return null;
            }
        }

        System.out.print("$ ");
        StringBuilder inputBuffer = new StringBuilder();
        try {
            while (true) {
                int key = System.in.read();
                if (key == '\n') {
                    System.out.println();
                    return inputBuffer.toString();
                } else if (key == '\t') {
                    // Handle autocompletion
                    String completed = AutoCompleter.complete(inputBuffer.toString());
                    System.out.print("\r$ " + completed); // Overwrite with the completed command
                    inputBuffer.setLength(0);
                    inputBuffer.append(completed);
                } else if (key == 127 || key == 8) { // Handle backspace
                    if (inputBuffer.length() > 0) {
                        inputBuffer.setLength(inputBuffer.length() - 1);
                        System.out.print("\b \b");
                    }
                } else {
                    inputBuffer.append((char) key);
                    System.out.print((char) key);
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    public class AutoCompleter {

        private static final List<String> builtins = List.of("echo", "exit", "pwd", "cd", "type");

        public static String complete(String partial) {
            List<String> matches = builtins.stream()
                .filter(cmd -> cmd.startsWith(partial))
                .collect(Collectors.toList());

            if (matches.size() == 1) {
                return matches.get(0) + " "; // Add space after completion
            } else {
                return partial; // No unique match, keep input unchanged
            }
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
        String outputFile = null;       // For stdout redirection (>)
        String appendOutputFile = null; // For stdout append redirection (>> or 1>>)
        String errorFile = null;        // For stderr redirection (2>)
        String appendErrorFile = null;  // For stderr append redirection (2>>)
        List<String> echoArgs = new ArrayList<>();

        // Parse arguments for redirection
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(">") || args.get(i).equals("1>")) {
                if (i + 1 < args.size()) {
                    outputFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for redirection");
                    return;
                }
            } else if (args.get(i).equals(">>") || args.get(i).equals("1>>")) {
                if (i + 1 < args.size()) {
                    appendOutputFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for append redirection");
                    return;
                }
            } else if (args.get(i).equals("2>")) {
                if (i + 1 < args.size()) {
                    errorFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for error redirection");
                    return;
                }
            } else if (args.get(i).equals("2>>")) {
                if (i + 1 < args.size()) {
                    appendErrorFile = args.get(i + 1);
                    i++; // Skip file name
                } else {
                    System.err.println("Syntax error: no file specified for error append redirection");
                    return;
                }
            } else {
                echoArgs.add(args.get(i));
            }
        }

        String output = String.join(" ", echoArgs);

        // Handle stdout redirection (overwrite)
        if (outputFile != null) {
            File outputFileObj = new File(outputFile);
            if (!outputFileObj.getParentFile().exists()) {
                if (!outputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for output redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter(outputFileObj))) {
                out.println(output); // Write output with newline for stdout
            }
        }
        // Handle stdout redirection (append)
        else if (appendOutputFile != null) {
            File appendOutputFileObj = new File(appendOutputFile);
            if (!appendOutputFileObj.getParentFile().exists()) {
                if (!appendOutputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for append redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.FileWriter(appendOutputFileObj, true))) {
                out.println(output); // Append output with newline
            }
        } else {
            // If no stdout redirection, print to console
            System.out.println(output);
        }

        // Handle stderr redirection (overwrite)
        if (errorFile != null) {
            File errorFileObj = new File(errorFile);
            if (!errorFileObj.getParentFile().exists()) {
                if (!errorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter errOut = new java.io.PrintWriter(new java.io.FileWriter(errorFileObj))) {
                errOut.print(""); // No stderr for echo by default
            }
        }
        // Handle stderr redirection (append)
        else if (appendErrorFile != null) {
            File appendErrorFileObj = new File(appendErrorFile);
            if (!appendErrorFileObj.getParentFile().exists()) {
                if (!appendErrorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error append redirection file");
                    return;
                }
            }
            try (java.io.PrintWriter errOut = new java.io.PrintWriter(new java.io.FileWriter(appendErrorFileObj, true))) {
                errOut.print(""); // No stderr for echo by default, append nothing
            }
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
        
        String outputFile = null;       // For stdout overwrite (>)
        String appendOutputFile = null; // For stdout append (>> or 1>>)
        String errorFile = null;        // For stderr overwrite (2>)
        String appendErrorFile = null;  // For stderr append (2>>)
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(">") || args.get(i).equals("1>")) {
                if (i + 1 < args.size()) {
                    outputFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for redirection");
                    return;
                }
            } else if (args.get(i).equals(">>") || args.get(i).equals("1>>")) {
                if (i + 1 < args.size()) {
                    appendOutputFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for append redirection");
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
            } else if (args.get(i).equals("2>>")) {
                if (i + 1 < args.size()) {
                    appendErrorFile = args.get(i + 1);
                    i++; // Skip the next argument (file name)
                } else {
                    System.err.println("Syntax error: no file specified for error append redirection");
                    return;
                }
            } else {
                commandWithArgs.add(args.get(i));
            }
        }

        ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);
        if (outputFile != null) {
            File outputFileObj = new File(outputFile);
            if (!outputFileObj.getParentFile().exists()) {
                if (!outputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for output redirection file");
                    return;
                }
            }
            processBuilder.redirectOutput(ProcessBuilder.Redirect.to(outputFileObj));
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE); // Capture error stream
        } else if (appendOutputFile != null) {
            File appendOutputFileObj = new File(appendOutputFile);
            if (!appendOutputFileObj.getParentFile().exists()) {
                if (!appendOutputFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for append redirection file");
                    return;
                }
            }
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(appendOutputFileObj));
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE); // Capture error stream
        } else if (errorFile != null) {
            File errorFileObj = new File(errorFile);
            if (!errorFileObj.getParentFile().exists()) {
                if (!errorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error redirection file");
                    return;
                }
            }
            processBuilder.redirectError(ProcessBuilder.Redirect.to(errorFileObj));
        } else if (appendErrorFile != null) {
            File appendErrorFileObj = new File(appendErrorFile);
            if (!appendErrorFileObj.getParentFile().exists()) {
                if (!appendErrorFileObj.getParentFile().mkdirs()) {
                    System.err.println("Error: unable to create directory for error append redirection file");
                    return;
                }
            }
            processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(appendErrorFileObj));
        } else {
            processBuilder.redirectErrorStream(true);
        }

        try {
            Process process = processBuilder.start();
            if (outputFile == null && appendOutputFile == null && errorFile == null && appendErrorFile == null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            } else if (outputFile != null || appendOutputFile != null) {
                if (errorFile == null && appendErrorFile == null) {
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null) {
                            System.err.println(errorLine); // Print error messages
                        }
                    }
                }
            } else if (errorFile != null || appendErrorFile != null) {
                try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String outputLine;
                    while ((outputLine = outputReader.readLine()) != null) {
                        System.out.println(outputLine); // Print output
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (outputFile != null || appendOutputFile != null || errorFile != null || appendErrorFile != null) {
                    // Do not print generic error message if output or error is redirected
                } else {
                    System.err.println(command + ": command failed with exit code " + exitCode);
                }
            }
        } catch (IOException e) {
            if (e.getMessage().contains("Cannot run program")) {
                System.err.println(command + ": command not found");
            } else {
                System.err.println(command + ": " + e.getMessage());
            }
        } catch (InterruptedException e) {
            System.err.println(command + ": process interrupted");
            Thread.currentThread().interrupt();
        }
    }
}
