import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("myshell> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }
            List<String> tokens = tokenize(input);
            if (tokens.isEmpty()) {
                continue;
            }
            String command = tokens.get(0);
            List<String> arguments = tokens.subList(1, tokens.size());
            if (command.equals("exit")) {
                break;
            }
            executeCommand(command, arguments);
        }
        scanner.close();
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                token.append(input.charAt(i + 1));
                i++;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(c);
            }
        }
        if (token.length() > 0) {
            tokens.add(token.toString());
        }
        return tokens;
    }

    private static void executeCommand(String command, List<String> arguments) {
        switch (command) {
            case "echo":
                System.out.println(String.join(" ", arguments));
                break;
            case "pwd":
                System.out.println(System.getProperty("user.dir"));
                break;
            case "cd":
                if (arguments.isEmpty()) {
                    System.err.println("cd: missing argument");
                } else {
                    File newDir = new File(arguments.get(0));
                    if (newDir.isDirectory()) {
                        System.setProperty("user.dir", newDir.getAbsolutePath());
                    } else {
                        System.err.println("cd: no such directory: " + arguments.get(0));
                    }
                }
                break;
            default:
                executeExternalCommand(command, arguments);
                break;
        }
    }

    private static void executeExternalCommand(String command, List<String> arguments) {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(command);
        fullCommand.addAll(arguments);
        ProcessBuilder processBuilder = new ProcessBuilder(fullCommand);
        processBuilder.directory(new File(System.getProperty("user.dir")));
        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Error executing command: " + command);
        }
    }
}
