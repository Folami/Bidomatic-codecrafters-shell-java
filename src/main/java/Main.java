import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    private static final List<String> shBuiltins = Arrays.asList("echo", "exit", "type", "pwd", "cd");

    public static void main(String[] args) {
        while (true) {
            String commandLine = inputPrompt();
            if (commandLine.isEmpty()) {
                continue;
            }
            try {
                List<String> tokens = manualTokenize(commandLine);
                if (tokens.isEmpty()) {
                    continue;
                }
                String command = tokens.get(0);
                List<String> commandArgs = tokens.subList(1, tokens.size());
                executeCommand(command, commandArgs);

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
            return "";
        }
    }

    private static List<String> manualTokenize(String cmdLine) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean escape = false;

        for (char c : cmdLine.toCharArray()) {
            if (escape) {
                current.append(c);
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '\'' && !doubleQuote) {
                singleQuote = !singleQuote;
            } else if (c == '"' && !singleQuote) {
                doubleQuote = !doubleQuote;
            } else if (Character.isWhitespace(c) && !singleQuote && !doubleQuote) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
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

    private static void executeEcho(List<String> args) {
        if (args.contains("-n")) {
            args.remove("-n");
            String output = String.join(" ", args);

            // Check for redirection (>)
            int redirectionIndex = args.indexOf(">");
            if (redirectionIndex != -1) {
                if (redirectionIndex + 1 < args.size()) {
                    String filePath = args.get(redirectionIndex + 1);
                    try {
                        // Create necessary directories
                        Path parentDir = Paths.get(filePath).getParent();
                        if (parentDir != null) {  //check if parent directory exists
                            Files.createDirectories(parentDir);
                        }

                        try (PrintWriter writer = new PrintWriter(filePath)) {
                            writer.print(output);
                        }
                    } catch (IOException e) {
                        System.err.println("Error redirecting output: " + e.getMessage());
                    }
                } else {
                    System.err.println("Error: Missing filename after redirection.");
                }
            } else {
                System.out.print(output); // No redirection, print to console
            }
        } else {
            System.out.println(String.join(" ", args)); // No -n, print to console
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
        Path path = Paths.get(newDir).toAbsolutePath().normalize();
        try {
            System.setProperty("user.dir", path.toString());
        } catch (Exception e) {
            System.out.println("cd: " + newDir + ": " + e.getMessage());
        }
    }

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                Path filePath = Paths.get(dir, command);
                if (Files.isExecutable(filePath)) {
                    return filePath.toString();
                }
            }
        }
        return null;
    }

    private static void runExternalCommand(String command, List<String> args) {
        List<String> commandWithArgs = new ArrayList<>();
        commandWithArgs.add(command);
        commandWithArgs.addAll(args);

        ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println(command + ": " + e.getMessage());
        }
    }
}




