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
    public static List<String> split(String s) throws Exception {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (escaped) {
                if (inSingleQuotes) {
                    current.append('\\').append(c);
                } else {
                    if (c == 'n') current.append('\n');
                    else if (c == 't') current.append('\t');
                    else if (c == 'r') current.append('\r');
                    else current.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                if (inSingleQuotes) {
                    current.append(c);
                } else {
                    escaped = true;
                }
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if ((c == ' ' || c == '\t') && !inSingleQuotes && !inDoubleQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (escaped || inSingleQuotes || inDoubleQuotes) {
            throw new Exception("Unmatched quote or escape");
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}
