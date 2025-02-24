import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    private static final List<String> shBuiltins = Arrays.asList("echo", "exit", "type", "pwd", "cd");

    public static class Shlex {

        public static List<String> split(String input) {
            List<String> tokens = new ArrayList<>();
            StringBuilder currentToken = new StringBuilder();
            boolean inSingleQuotes = false;
            boolean inDoubleQuotes = false;
            boolean escapeNext = false;

            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);

                if (escapeNext) {
                    currentToken.append(c);
                    escapeNext = false;
                } else if (c == '\\') {
                    escapeNext = true;
                } else if (c == '\'' && !inDoubleQuotes) {
                    if (inSingleQuotes) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                        inSingleQuotes = false;
                    } else {
                        inSingleQuotes = true;
                    }
                } else if (c == '"' && !inSingleQuotes) {
                    if (inDoubleQuotes) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                        inDoubleQuotes = false;
                    } else {
                        inDoubleQuotes = true;
                    }
                } else if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
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

            return tokens;
        }
    }

    private static class Tokenizer {

        private static List<String> tokens;
        private static StringBuilder current;
        private static boolean singleQuote;
        private static boolean doubleQuote;
        private static boolean escape;

        public static List<String> manualTokenize(String cmdLine) {
            tokens = new ArrayList<>();
            current = new StringBuilder();
            singleQuote = false;
            doubleQuote = false;
            escape = false;

            for (char c : cmdLine.toCharArray()) {
                processCharacter(c);
            }

            finalizeToken();
            return tokens;
        }

        private static void processCharacter(char c) {
            if (escape) {
                handleEscapeCharacter(c);
            } else if (c == '\\') {
                escape = true;
            } else if (c == '\'' && !doubleQuote) {
                handleSingleQuote();
            } else if (c == '"' && !singleQuote) {
                handleDoubleQuote();
            } else if (Character.isWhitespace(c) && !singleQuote && !doubleQuote) {
                handleWhitespace();
            } else {
                current.append(c);
            }
        }

        private static void handleEscapeCharacter(char c) {
            current.append(c);
            escape = false;
        }

        private static void handleSingleQuote() {
            if (singleQuote) {
                addToken();
            }
            singleQuote = !singleQuote;
        }

        private static void handleDoubleQuote() {
            if (doubleQuote) {
                addToken();
            }
            doubleQuote = !doubleQuote;
        }

        private static void handleWhitespace() {
            if (current.length() > 0) {
                addToken();
            }
        }

        private static void addToken() {
            tokens.add(current.toString());
            current.setLength(0);
        }

        private static void finalizeToken() {
            if (current.length() > 0) {
                addToken();
            }
        }
    }

    public static void main(String[] args) {
        while (true) {
            String commandLine = inputPrompt();
            if (commandLine.isEmpty()) {
                continue;
            }
            try {
                // Tokenizer tokenizer = new Tokenizer();
                List<String> tokens = Shlex.split(commandLine);
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
    
    public static void executeEcho(List<String> args) {
        System.out.println(String.join(" ", args));
    }
/* 
    public static void executeEcho(List<String> args) {
        if (args.isEmpty()) {
            System.out.println();
            return;
        }
        boolean suppressNewline = false;
        int startIndex = 0;
        // Check for the -n flag
        if (args.get(0).equals("-n")) {
            suppressNewline = true;
            startIndex = 1;
        }
        joinEchoArgs(args, startIndex, suppressNewline);
    }

    private static void joinEchoArgs(List<String> args, int startIndex, boolean suppressNewline) {
        // Join the arguments into a single string
        StringBuilder output = new StringBuilder();
        for (int i = startIndex; i < args.size(); i++) {
            if (args.get(i).equals(">") && i + 1 < args.size()) {
                // Handle output redirection
                String filePath = args.get(i + 1);
                filePath = filePath.replace("\\n", "\n")
                                   .replace("\\t", "\t")
                                   .replace("\\\\", "\\");
                writeFile(output.toString(), filePath, suppressNewline);
                return;
            }
            output.append(args.get(i));
            if (i < args.size() - 1) {
                output.append(" ");
            }
        }
        // If no redirection, print to console
        if (suppressNewline) {
            System.out.print(output.toString());
        } else {
            System.out.println(output.toString());
        }        
    }

    private static void writeFile(String content, String filePath, boolean suppressNewline) {
        Path path = Paths.get(filePath);
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(path.getParent());
            // Write content to the file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                writer.write(content);
                if (!suppressNewline) {
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
*/
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
