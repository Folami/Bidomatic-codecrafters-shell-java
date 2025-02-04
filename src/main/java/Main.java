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
            if (input == null) 
                break; // Exit if input is null (e.g., Ctrl+D)

            String[] tokens = splitPreservingQuotesAndEscapes(input);
            if (tokens.length == 0) 
                continue; // Skip empty input

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
            System.out.println(String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)));
        } else {
            System.out.println();
        }
    }

    private static String[] splitPreservingQuotesAndEscapes(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;
        boolean escape = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escape) {
                currentToken.append(c); // Always add escaped characters
                escape = false;
            } else if (c == '\\') {
                escape = true; // Next character is escaped
            } else if ((c == '"' || c == '\'') && !inQuote) {
                inQuote = true;
                quoteChar = c;
            } else if (c == quoteChar) {
                inQuote = false;
                quoteChar = 0;
            } else if ((c == ' ' || c == '\t') && !inQuote) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        return tokens.toArray(new String[0]);
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
            String homeDir = System.getenv("HOME");
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
