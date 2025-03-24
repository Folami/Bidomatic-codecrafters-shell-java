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

    protected static String inputPrompt() {
        System.out.print("$ ");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder currentInput = new StringBuilder();

        try {
            while (true) {
                int charCode = reader.read();
                if (charCode == -1) { // End of stream
                    return null;
                }
                char inputChar = (char) charCode;

                if (inputChar == '\t') {
                    tabPressCount++;
                    String textBeforeTab = currentInput.toString();
                    String completedText = AutoCompleter.complete(textBeforeTab, tabPressCount);
                    // Debug: Verify completedText
                    System.err.println("DEBUG: completedText = '" + completedText + "'");

                    // Clear and rewrite
                    System.out.print("\r$ " + completedText);
                    System.out.print("\033[K");
                    System.out.flush();

                    currentInput.setLength(0);
                    currentInput.append(completedText);

                } else if (inputChar == '\n') {
                    tabPressCount = 0;
                    String finalInput = currentInput.toString();
                    System.out.println();
                    return finalInput;
                } else if (inputChar == 8) { // Backspace
                    if (currentInput.length() > 0) {
                        currentInput.deleteCharAt(currentInput.length() - 1);
                        System.out.print("\r$ " + currentInput.toString() + " \033[K");
                        System.out.flush();
                    }
                } else {
                    System.out.print(inputChar);
                    System.out.flush();
                    currentInput.append(inputChar);
                }
            }
        } catch (IOException e) {
            return null;
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


