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

    private StringReader instream;
    private String infile;
    private boolean posix;
    private String eof;
    private String commenters = "#";
    private String wordchars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    private String whitespace = " \t\r\n";
    private boolean whitespaceSplit = false;
    private String quotes = "'\"";
    private String escape = "\\";
    private String escapedquotes = "\"";
    private String state = " ";
    private Deque<String> pushback = new LinkedList<>();
    private int lineno = 1;
    private int debug = 0;
    private String token = "";
    private Deque<Object[]> filestack = new LinkedList<>();
    private String source = null;
    private String punctuationChars = "";
    private Deque<Character> pushbackChars = new LinkedList<>();

    public Shlex(String instream, String infile, boolean posix, String punctuationChars) {
        this.instream = new StringReader(instream);
        this.infile = infile;
        this.posix = posix;
        this.punctuationChars = punctuationChars == null ? "" : punctuationChars;
        if (posix) {
            this.eof = null;
        } else {
            this.eof = "";
        }
        if (posix) {
            this.wordchars += "ßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞ";
        }
        if (!this.punctuationChars.isEmpty()) {
            this.wordchars += "~-./*?=";
            for (char c : this.punctuationChars.toCharArray()) {
                this.wordchars = this.wordchars.replace(String.valueOf(c), "");
            }
        }
    }

    public List<String> split() throws IOException {
        List<String> tokens = new ArrayList<>();
        while (true) {
            String token = get_token();
            if (token == null) {
                break;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private void push_token(String tok) {
        if (debug >= 1) {
            System.out.println("shlex: pushing token " + tok);
        }
        pushback.addFirst(tok);
    }

    private String get_token() throws IOException {
        if (!pushback.isEmpty()) {
            String tok = pushback.removeFirst();
            if (debug >= 1) {
                System.out.println("shlex: popping token " + tok);
            }
            return tok;
        }
        String raw = read_token();
        while (raw != null && raw.equals(eof)) {
            if (filestack.isEmpty()) {
                return eof;
            } else {
                pop_source();
                raw = get_token();
            }
        }
        if (debug >= 1) {
            if (raw != null && !raw.equals(eof)) {
                System.out.println("shlex: token=" + raw);
            } else {
                System.out.println("shlex: token=EOF");
            }
        }
        return raw;
    }

    private String read_token() throws IOException {
        boolean quoted = false;
        String escapedstate = " ";
        while (true) {
            char nextchar;
            if (!punctuationChars.isEmpty() && !pushbackChars.isEmpty()) {
                nextchar = pushbackChars.removeFirst();
            } else {
                int readChar = instream.read();
                if (readChar == -1) {
                    nextchar = '\0';
                } else {
                    nextchar = (char) readChar;
                }
            }
            if (nextchar == '\n') {
                lineno++;
            }
            if (debug >= 3) {
                System.out.println("shlex: in state " + state + " I see character: " + nextchar);
            }
            if (state == null) {
                token = "";
                break;
            } else if (state.equals(" ")) {
                if (nextchar == '\0') {
                    state = null;
                    break;
                } else if (whitespace.indexOf(nextchar) != -1) {
                    if (debug >= 2) {
                        System.out.println("shlex: I see whitespace in whitespace state");
                    }
                    if (!token.isEmpty() || (posix && quoted)) {
                        break;
                    } else {
                        continue;
                    }
                } else if (commenters.indexOf(nextchar) != -1) {
                    instream.read();
                    lineno++;
                } else if (posix && escape.indexOf(nextchar) != -1) {
                    escapedstate = "a";
                    state = String.valueOf(nextchar);
                } else if (wordchars.indexOf(nextchar) != -1) {
                    token = String.valueOf(nextchar);
                    state = "a";
                } else if (punctuationChars.indexOf(nextchar) != -1) {
                    token = String.valueOf(nextchar);
                    state = "c";
                } else if (quotes.indexOf(nextchar) != -1) {
                    if (!posix) {
                        token = String.valueOf(nextchar);
                    }
                    state = String.valueOf(nextchar);
                } else if (whitespaceSplit) {
                    token = String.valueOf(nextchar);
                    state = "a";
                } else {
                    token = String.valueOf(nextchar);
                    if (!token.isEmpty() || (posix && quoted)) {
                        break;
                    } else {
                        continue;
                    }
                }
            } else if (quotes.indexOf(state) != -1) {
                quoted = true;
                if (nextchar == '\0') {
                    throw new IllegalArgumentException("No closing quotation");
                }
                if (String.valueOf(nextchar).equals(state)) {
                    if (!posix) {
                        token += nextchar;
                        state = " ";
                        break;
                    } else {
                        state = "a";
                    }
                } else if (posix && escape.indexOf(nextchar) != -1 && escapedquotes.indexOf(state) != -1) {
                    escapedstate = state;
                    state = String.valueOf(nextchar);
                } else {
                    token += nextchar;
                }
            } else if (escape.indexOf(state) != -1) {
                if (nextchar == '\0') {
                    throw new IllegalArgumentException("No escaped character");
                }
                if (quotes.indexOf(escapedstate) != -1 && nextchar != state.charAt(0) && nextchar != escapedstate.charAt(0)) {
                    token += state;
                }
                token += nextchar;
                state = escapedstate;
            } else if (state.equals("a") || state.equals("c")) {
                if (nextchar == '\0') {
                    state = null;
                    break;
                } else if (whitespace.indexOf(nextchar) != -1) {
                    state = " ";
                    if (!token.isEmpty() || (posix && quoted)) {
                        break;
                    } else {
                        continue;
                    }
                } else if (commenters.indexOf(nextchar) != -1) {
                    instream.read();
                    lineno++;
                    if (posix) {
                        state = " ";
                        if (!token.isEmpty() || (posix && quoted)) {
                            break;
                        } else {
                            continue;
                        }
                    }
                } else if (state.equals("c")) {
                    if (punctuationChars.indexOf(nextchar) != -1) {
                        token += nextchar;
                    } else {
                        if (whitespace.indexOf(nextchar) == -1) {
                            pushbackChars.addFirst(nextchar);
                        }
                        state = " ";
                        break;
                    }
                } else if (posix && quotes.indexOf(nextchar) != -1) {
                    state = String.valueOf(nextchar);
                } else if (posix && escape.indexOf(nextchar) != -1) {
                    escapedstate = "a";
                    state = String.valueOf(nextchar);
                } else if (wordchars.indexOf(nextchar) != -1 || quotes.indexOf(nextchar) != -1
                        || (whitespaceSplit && punctuationChars.indexOf(nextchar) == -1)) {
                    token += nextchar;
                } else {
                    if (!punctuationChars.isEmpty()) {
                        pushbackChars.addFirst(nextchar);
                    } else {
                        pushback.addFirst(String.valueOf(nextchar));
                    }
                    state = " ";
                    if (!token.isEmpty() || (posix && quoted)) {
                        break;
                    } else {
                        continue;
                    }
                }
            }
        }
        String result = token;
        token = "";
        if (posix && !quoted && result.isEmpty()) {
            result = null;
        }
        if (debug > 1) {
            if (result != null) {
                System.out.println("shlex: raw token=" + result);
            } else {
                System.out.println("shlex: raw token=EOF");
            }
        }
        return result;
    }

    private void pop_source() throws IOException {
        instream.close();
        Object[] sourceInfo = filestack.removeFirst();
        infile = (String) sourceInfo[0];
        instream = new StringReader((String) sourceInfo[1]);
        lineno = (int) sourceInfo[2];
        if (debug != 0) {
            System.out.println("shlex: popping to " + instream + ", line " + lineno);
        }
        state = " ";
    }
}