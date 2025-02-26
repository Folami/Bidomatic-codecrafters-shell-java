import java.io.*;
import java.nio.file.*;
import java.util.*;

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
                
                List<String> commandParts = new ArrayList<>();
                String outputFile = null;
                boolean redirectOutput = false;
                
                for (int i = 0; i < tokens.size(); i++) {
                    if (tokens.get(i).equals(">") || tokens.get(i).equals("1>")) {
                        if (i + 1 < tokens.size()) {
                            outputFile = tokens.get(i + 1);
                            redirectOutput = true;
                            break;
                        } else {
                            System.out.println("Error: Missing file name after redirection operator");
                            continue;
                        }
                    }
                    commandParts.add(tokens.get(i));
                }
                
                if (commandParts.isEmpty()) {
                    continue;
                }
                
                String command = commandParts.get(0);
                List<String> commandArgs = commandParts.subList(1, commandParts.size());
                executeCommand(command, commandArgs, redirectOutput, outputFile);
                
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

    private static void executeCommand(String command, List<String> args, boolean redirectOutput, String outputFile) throws IOException {
        switch (command) {
            case "exit":
                exitShell();
                break;
            case "echo":
                executeEcho(args, redirectOutput, outputFile);
                break;
            case "type":
                executeType(args, redirectOutput, outputFile);
                break;
            case "pwd":
                executePwd(redirectOutput, outputFile);
                break;
            case "cd":
                executeCd(args);
                break;
            default:
                runExternalCommand(command, args, redirectOutput, outputFile);
                break;
        }
    }

    private static void exitShell() {
        System.exit(0);
    }

    private static void executeEcho(List<String> args, boolean redirectOutput, String outputFile) throws IOException {
        String output = String.join(" ", args);
        writeOutput(output, redirectOutput, outputFile);
    }

    private static void executeType(List<String> args, boolean redirectOutput, String outputFile) throws IOException {
        if (args.isEmpty()) {
            writeOutput("type: missing operand", redirectOutput, outputFile);
            return;
        }
        
        String targetCommand = args.get(0);
        String output;
        if (shBuiltins.contains(targetCommand)) {
            output = targetCommand + " is a shell builtin";
        } else {
            String executable = findExecutable(targetCommand);
            output = (executable != null) ? (targetCommand + " is " + executable) : (targetCommand + ": not found");
        }
        writeOutput(output, redirectOutput, outputFile);
    }

    private static void executePwd(boolean redirectOutput, String outputFile) throws IOException {
        writeOutput(System.getProperty("user.dir"), redirectOutput, outputFile);
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

    private static void runExternalCommand(String command, List<String> args, boolean redirectOutput, String outputFile) throws IOException {
        List<String> commandWithArgs = new ArrayList<>();
        commandWithArgs.add(command);
        commandWithArgs.addAll(args);

        ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);
        processBuilder.redirectErrorStream(true);
        if (redirectOutput && outputFile != null) {
            processBuilder.redirectOutput(new File(outputFile));
        }

        Process process;
        try {
            process = processBuilder.start();
            if (!redirectOutput) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }
            process.waitFor();
            if (process.exitValue() != 0) {
                System.out.println(command + ": command not found");
            }
        } catch (IOException e) {
            System.out.println(command + ": command not found");
        } catch (InterruptedException e) {
            System.out.println(command + ": " + e.getMessage());
        }
    }

    private static void writeOutput(String output, boolean redirectOutput, String outputFile) throws IOException {
        if (redirectOutput && outputFile != null) {
            Files.writeString(Paths.get(outputFile), output + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            System.out.println(output);
        }
    }
}
