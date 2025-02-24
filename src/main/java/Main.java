import java.io.*;
import java.util.*;

public class Main {
    private static final Set<String> SH_BUILTINS = new HashSet<>(Arrays.asList("echo", "exit", "type", "pwd", "cd"));
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            String commandLine = inputPrompt();
            if (commandLine == null || commandLine.isEmpty()) {
                continue;
            }

            try {
                List<String> tokens = Shlex.split(commandLine);
                if (tokens.isEmpty()) {
                    continue;
                }

                String command = tokens.get(0);
                List<String> commandArgs = tokens.subList(1, tokens.size());

                executeCommand(command, commandArgs);
            } catch (Exception e) {
                System.out.println("Error parsing command: " + e.getMessage());
            }
        }
    }

    private static String inputPrompt() {
        System.out.print("$ ");
        return scanner.hasNextLine() ? scanner.nextLine() : "exit";
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
        if (SH_BUILTINS.contains(targetCommand)) {
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
        System.out.println(String.join(" ", args));
    }

    private static void executeCd(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("cd: missing operand");
            return;
        }

        String newDir = args.get(0).replace("~", System.getProperty("user.home"));
        File dir = new File(newDir);

        if (dir.isDirectory()) {
            System.setProperty("user.dir", dir.getAbsolutePath());
        } else {
            System.out.println("cd: " + newDir + ": No such directory");
        }
    }

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                File file = new File(dir, command);
                if (file.isFile() && file.canExecute()) {
                    return file.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private static void runExternalCommand(String command, List<String> args) {
        try {
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(command);
            fullCommand.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.err.println(command + ": command failed with exit code " + exitCode);
            }
        } catch (IOException e) {
            System.err.println(command + ": command not found");
        } catch (InterruptedException e) {
            System.err.println(command + ": command interrupted");
        }
    }
}

class Shlex {

    // The states of our tokenizer
    private enum State {
        NORMAL,          // Outside of any quotes
        IN_SINGLE,       // Inside single quotes: everything is literal
        IN_DOUBLE,       // Inside double quotes: backslashes escape next character
        ESCAPE           // Next character is escaped
    }

    /**
     * Splits the input string into tokens, handling:
     *   a. Single quotes (contents taken literally; backslashes preserved)
     *   b. Double quotes (backslashes escape the next character)
     *   c. Backslashes in unquoted text (escape the next character)
     *   d. Removing surrounding quotes from quoted executables.
     *
     * @param input the command-line input string
     * @return a list of tokens
     */
    public static List<String> split(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        State state = State.NORMAL;
        State prevState = State.NORMAL;  // Used for ESCAPE state

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (state) {
                case NORMAL:
                    if (c == '\\') {
                        prevState = state;
                        state = State.ESCAPE;
                    } else if (c == '\'') {
                        // Enter single-quote state; do not include the quote.
                        state = State.IN_SINGLE;
                    } else if (c == '"') {
                        // Enter double-quote state; do not include the quote.
                        state = State.IN_DOUBLE;
                    } else if (Character.isWhitespace(c)) {
                        if (current.length() > 0) {
                            tokens.add(current.toString());
                            current.setLength(0);
                        }
                    } else {
                        current.append(c);
                    }
                    break;
                case IN_SINGLE:
                    // In single quotes, everything is taken literally.
                    if (c == '\'') {
                        state = State.NORMAL;
                    } else {
                        current.append(c);
                    }
                    break;
                case IN_DOUBLE:
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
                    // Append the character regardless of context.
                    current.append(c);
                    state = prevState;
                    break;
            }
        }
        if (state == State.ESCAPE) {
            // Trailing backslash: append it literally.
            current.append('\\');
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
    
}

