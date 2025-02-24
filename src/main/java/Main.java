import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Main {
    // List of shell built-in commands
    private static final List<String> shBuiltins = Arrays.asList("echo", "exit", "type", "pwd", "cd");

    public static void main(String[] args) {
        while (true) {
            String commandLine = inputPrompt();
            if (commandLine.isEmpty()) {
                continue;
            }
            List<String> tokens = Shlex.split(commandLine);
            if (tokens.isEmpty()) {
                continue;
            }
            String command = tokens.get(0);
            List<String> commandArgs = tokens.subList(1, tokens.size());
            executeCommand(command, commandArgs);
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

    private static void executeCommand(String command, List<String> args) {
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

    private static void executeEcho(List<String> args) {
        // Simple echo implementation; for more advanced redirection support, extend here.
        System.out.println(String.join(" ", args));
    }

    private static void executeCd(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("cd: missing operand");
            return;
        }
        String newDir = args.get(0);
        newDir = newDir.replaceFirst("^~", System.getProperty("user.home"));
        try {
            System.setProperty("user.dir", Paths.get(newDir).toAbsolutePath().normalize().toString());
            // In Java, changing the "user.dir" property doesn't change the actual working directory,
            // so you might need to simulate this behavior in your shell implementation.
        } catch (Exception e) {
            System.out.println("cd: " + newDir + ": " + e.getMessage());
        }
    }

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                Path potential = Paths.get(dir, command);
                if (Files.isRegularFile(potential) && Files.isExecutable(potential)) {
                    return potential.toString();
                }
            }
        }
        return null;
    }

    private static void runExternalCommand(String command, List<String> args) {
        List<String> cmdWithArgs = new ArrayList<>();
        cmdWithArgs.add(command);
        cmdWithArgs.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmdWithArgs);
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
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

    /**
     * The Shlex class provides a static split method that tokenizes an input
     * command line string in a shell-like manner. It supports:
     *   a. Single quotes (contents taken literally; backslashes are preserved).
     *   b. Double quotes (backslashes escape the next character).
     *   c. Backslashes in unquoted text.
     *   d. Quoted executables (quotes are removed during tokenization).
     */
    public static class Shlex {
        private enum State { NORMAL, IN_SINGLE_QUOTE, IN_DOUBLE_QUOTE, ESCAPE }

        public static List<String> split(String input) {
            List<String> tokens = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            State state = State.NORMAL;
            State prevState = State.NORMAL;

            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                switch (state) {
                    case NORMAL:
                        if (c == '\\') {
                            prevState = state;
                            state = State.ESCAPE;
                        } else if (c == '\'') {
                            state = State.IN_SINGLE_QUOTE;
                        } else if (c == '"') {
                            state = State.IN_DOUBLE_QUOTE;
                        } else if (Character.isWhitespace(c)) {
                            if (current.length() > 0) {
                                tokens.add(current.toString());
                                current.setLength(0);
                            }
                        } else {
                            current.append(c);
                        }
                        break;
                    case IN_SINGLE_QUOTE:
                        // In single quotes, backslashes are preserved literally.
                        if (c == '\'') {
                            state = State.NORMAL;
                        } else {
                            current.append(c);
                        }
                        break;
                    case IN_DOUBLE_QUOTE:
                        if (c == '\\') {
                            prevState = state;
                            state = State.ESCAPE;
                        } else if (c == '"') {
                            state = State.NORMAL;
                        } else {
                            current.append(c);
                        }
                        break;
                    case ESCAPE:
                        // Append the escaped character and return to previous state.
                        current.append(c);
                        state = prevState;
                        break;
                }
            }
            if (state == State.ESCAPE) {
                // Trailing backslash; append it literally.
                current.append('\\');
            }
            if (current.length() > 0) {
                tokens.add(current.toString());
            }
            return tokens;
        }
    }
}
