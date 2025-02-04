import java.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final Set<String> BUILTINS = new HashSet<>(
        Arrays.asList("echo", "exit", "type", "pwd", "cd")
    );
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            String input = promptAndGetInput();
            if (input == null) break;
            String[] tokens = splitPreservingQuotes(input);
            if (tokens.length == 0) continue;
            executeCommand(tokens);
        }
        scanner.close();
    }

    private static String promptAndGetInput() {
        System.out.print("$ ");
        return scanner.hasNextLine() ? scanner.nextLine().trim() : null;
    }

    private static void executeCommand(String[] tokens) {
        String command = tokens[0];
        if (command.equals("exit") && tokens.length > 1 && tokens[1].equals("0")) {
            System.exit(0);
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
        if (tokens.length > 1) {
            String[] processedTokens = new String[tokens.length - 1];
            for (int i = 1; i < tokens.length; i++) {
                processedTokens[i - 1] = processEscapeSequences(tokens[i]);
            }
            System.out.println(String.join(" ", processedTokens));
        } else {
            System.out.println();
        }
    }


    private static String[] splitPreservingQuotes(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        char quoteChar = 0;
        boolean escape = false;

        for (char c : input.toCharArray()) {
            if (escape) {
                currentToken.append(c);
                escape = false;
            } else if (c == '\\') {
                escape = true;
                currentToken.append(c);
            } else if ((c == ' ' || c == '\t') && quoteChar == 0) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else if ((c == '\'' || c == '"') && quoteChar == 0) {
                quoteChar = c;
                currentToken.append(c);
            } else if (c == quoteChar) {
                quoteChar = 0;
                currentToken.append(c);
            } else {
                currentToken.append(c);
            }
        }
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        return tokens.toArray(new String[0]);
    }

    private static String processEscapeSequences(String input) {
        StringBuilder output = new StringBuilder();
        boolean escape = false;
        for (char c : input.toCharArray()) {
            if (escape) {
                switch (c) {
                    case 'n': output.append('\n'); break;
                    case 't': output.append('\t'); break;
                    case 'r': output.append('\r'); break;
                    default: output.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else {
                output.append(c);
            }
        }
        return output.toString();
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
        String path = tokens[1].replace("~", System.getenv("HOME"));
        Path resolvedPath = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        File directory = resolvedPath.toFile();
        if (directory.exists() && directory.isDirectory()) {
            System.setProperty("user.dir", directory.getAbsolutePath());
        } else {
            System.out.println("cd: " + path + ": No such file or directory");
        }
    }

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File file = new File(dir, command);
            if (file.isFile() && file.canExecute()) return file.getAbsolutePath();
        }
        return null;
    }

    private static void runExternalCommand(String[] commandParts) {
        try {
            ProcessBuilder pb = new ProcessBuilder(commandParts);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor(); // Capture exit code

            if (exitCode != 0) {
                System.err.println(commandParts[0] + ": command failed with exit code " + exitCode);
            }

        } catch (IOException e) {
            System.err.println(commandParts[0] + ": command not found or could not be executed");
        } catch (InterruptedException e) {
            System.err.println("Process interrupted");
            Thread.currentThread().interrupt();
        }
    }

}