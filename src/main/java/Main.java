import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;



public class Main {
    private static int tabPressCount = 0;  // Track Tab presses
    
    
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
        Console console = System.console();
        if (console == null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                String input = reader.readLine();
                if (input == null) {
                    return null;
                }
                // Handle tab completion
                if (input.contains("\t")) {
                    tabPressCount++;
                    String textBeforeTab = input.substring(0, input.indexOf('\t'));
                    String completedText = AutoCompleter.complete(textBeforeTab, tabPressCount);
                    System.out.print("\r$ " + completedText); // Update the prompt
                    return completedText;
                }
                tabPressCount = 0;
                return input;
            } catch (IOException e) {
                return null;
            }
        } else {
            StringBuilder input = new StringBuilder();
            while (true) {
                String line = console.readLine("$ " + input.toString());
                if (line == null) {
                    return null;
                }
                // Handle tab completion
                if (line.contains("\t")) {
                    tabPressCount++;
                    String textBeforeTab = line.substring(0, line.indexOf('\t'));
                    String completedText = AutoCompleter.complete(textBeforeTab, tabPressCount);
                    input.append(completedText);
                    System.out.print("\r$ " + input.toString()); // Update the prompt
                } else {
                    tabPressCount = 0;
                    input.append(line);
                    break;
                }
            }
            return input.toString();
        }
    }

    protected static void executeCommand(String command, List<String> args) throws IOException {
        if (BuiltinCommands.isBuiltin(command)) {
            BuiltinCommands.execute(command, args);
        } else {
            ExternalCommands.runExternalCommand(command, args);
        }
    }
}

