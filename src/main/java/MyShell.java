import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MyShell {

    private static final List<String> shBuiltins = List.of("echo", "exit", "type", "pwd", "cd");

    public static void main(String[] args) {
        while (true) {
            String commandLine = inputPrompt();
            if (commandLine == null || commandLine.isEmpty()) {
                continue;
            }

            try {
                List<String> tokens = Shlex.split(commandLine);
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

    private static void executeEcho(List<String> args) {
        System.out.println(String.join(" ", args));
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

    private static void executeCd(List<String> args) throws IOException {
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

    private static void runExternalCommand(String command, List<String> args) throws IOException {
        List<String> commandWithArgs = new ArrayList<>();
        commandWithArgs.add(command);
        commandWithArgs.addAll(args);

        ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println(command + ": " + e.getMessage());
        } finally {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                System.out.println(command + ": " + e.getMessage());
            }

        }
    }

    public static class Shlex {

        public static List<String> split(String cmdLine) {
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
                    if (singleQuote) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    singleQuote = !singleQuote;
                } else if (c == '"' && !singleQuote) {
                    if (doubleQuote) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
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
    }
}