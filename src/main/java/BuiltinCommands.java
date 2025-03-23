import java.io.*;
import java.nio.file.*;
import java.util.*;

public class BuiltinCommands {
    private static final List<String> BUILTINS = List.of("echo", "exit", "type", "pwd", "cd");
    
    public static boolean isBuiltin(String command) {
        return BUILTINS.contains(command);
    }

    public static void execute(String command, List<String> args) throws IOException {
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
                throw new IllegalArgumentException("Not a builtin command: " + command);
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
        if (BUILTINS.contains(targetCommand)) {
            System.out.println(targetCommand + " is a shell builtin");
        } else {
            String executable = ExternalCommands.findExecutable(targetCommand);
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
}